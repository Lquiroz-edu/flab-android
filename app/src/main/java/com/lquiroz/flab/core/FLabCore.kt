package com.lquiroz.flab.core

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import androidx.window.layout.WindowMetricsCalculator
import com.lquiroz.flab.motion.EvidenceSource
import com.lquiroz.flab.motion.FoldEvidence
import com.lquiroz.flab.motion.MotionTuning
import com.lquiroz.flab.profiles.FLabProfile
import com.lquiroz.flab.profiles.ProfileId
import com.lquiroz.flab.settings.FLabConfiguration
import com.lquiroz.flab.settings.FLabSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * The F/LAB Core (DoD 2).
 *
 * One engine, one state. Every module reads [state] and nothing else — no module opens its own
 * `WindowInfoTracker`, registers its own sensor listener, or keeps its own idea of the posture.
 * That single rule is what makes a continuous
 * `closed -> part open -> open -> part closed -> closed` session survive without a restart, and
 * it is what makes the state reproducible in a test.
 *
 * ### Lifecycle
 *
 * The Core lives for as long as the process. Activities [attach] and [detach] around their
 * `STARTED` lifecycle; the Core keeps its state across those, so rotating, folding or backgrounding
 * never resets configuration. Attaching twice is safe — the previous subscription is cancelled.
 *
 * ### Cost
 *
 * Every subscription here is event-driven. There is no timer, no poll and no wake lock anywhere in
 * this class (DoD 22, 25).
 *
 * The hinge sensor is the only high-rate source. By default it is registered only while a
 * transition is in flight and released when the interpolator settles. The exception is
 * [requestContinuousTracking]: reacting to a fold while F/LAB is not on screen — the System
 * effects service, the Fold Wallpaper — requires *something* to be listening, and that is a real
 * cost rather than a free one. It is opt-in, ref-counted by caller, and tracking only actually
 * stops once every caller has released it — so the wallpaper being visible on the home screen
 * cannot be starved of evidence just because System effects happened to switch off first, or the
 * other way around.
 */
class FLabCore(
    private val applicationContext: Context,
    private val settings: FLabSettings,
    private val scope: CoroutineScope,
    private val breaker: ModuleCircuitBreaker = ModuleCircuitBreaker(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val hingeSource = HingeAngleSource(applicationContext)
    private val powerManager =
        applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val _state = MutableStateFlow(FLabState())
    val state: StateFlow<FLabState> = _state.asStateFlow()

    private val _evidence = MutableStateFlow(FoldEvidence(0f, EvidenceSource.PostureEvent, 0L))

    /**
     * The latest physical observation, for the motion engine to consume.
     *
     * Kept separate from [state] because it changes at sensor rate: folding it into the aggregate
     * state would wake every state collector for every degree of hinge movement.
     */
    val evidence: StateFlow<FoldEvidence> = _evidence.asStateFlow()

    private var attachedJob: Job? = null
    private var attachedActivity: WeakReference<Activity>? = null
    private var hingeJob: Job? = null
    private var configuration: FLabConfiguration = FLabConfiguration()

    /**
     * Callers currently holding continuous tracking open, keyed by whatever object they identify
     * themselves with. A set rather than a count so a caller that crashes without releasing cannot
     * leave a phantom count above zero from a second `request` no one asked for — the same owner
     * requesting twice is one entry, not two.
     */
    private val continuousTrackingOwners = mutableSetOf<Any>()

    val activeProfile: FLabProfile
        get() = FLabProfile.of(configuration.profileId)

    /**
     * The profile's motion tuning after the current power posture has had its say. Renderers read
     * this, never `activeProfile.motion` directly — otherwise battery saver would be a fact the
     * state knows and the frame loop ignores.
     */
    val effectiveMotionTuning: MotionTuning
        get() = activeProfile.motion.forPower(_state.value.power)

    val isHingeSensorAvailable: Boolean get() = hingeSource.isAvailable

    init {
        settings.observe()
            .onEach(::applyConfiguration)
            .launchIn(scope)
    }

    /** Binds the Core to an Activity's window. Safe to call repeatedly; the newest caller wins. */
    fun attach(activity: Activity) {
        attachedActivity = WeakReference(activity)
        attachedJob?.cancel()
        attachedJob = scope.launch {
            WindowInfoTracker.getOrCreate(activity)
                .windowLayoutInfo(activity)
                .distinctUntilChanged()
                .collect { layoutInfo -> onWindowLayout(activity, layoutInfo) }
        }
        onWindowMetrics(activity)
        refreshPowerPosture()
    }

    /**
     * Releases [activity]'s binding — and only its own. With two Activities in the app (F/LAB and
     * F/LAB Home) Android starts the new one *before* stopping the old one, so an unconditional
     * detach would tear down the subscription the newcomer just opened.
     */
    fun detach(activity: Activity) {
        if (attachedActivity?.get() !== activity) return
        attachedActivity = null
        attachedJob?.cancel()
        attachedJob = null
        stopHingeTracking()
    }

    /** Re-reads window metrics. Call from `onConfigurationChanged` and after a resize. */
    fun onWindowMetrics(activity: Activity) {
        val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity)
        val density = activity.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val size = WindowSize(
            widthDp = (metrics.bounds.width() / density).toInt(),
            heightDp = (metrics.bounds.height() / density).toInt(),
        )
        val presentation = presentationOf(activity)
        _state.value = _state.value.let { current ->
            current.copy(
                window = current.window.copy(
                    size = size,
                    presentation = presentation,
                    display = displayFor(size, current.fold),
                    orientation = size.orientation,
                ),
            )
        }
    }

    /** Records the foreground package. Only ever F/LAB's own package unless a module supplies more. */
    fun onForegroundPackage(packageName: String?) {
        if (_state.value.foregroundPackage == packageName) return
        _state.value = _state.value.copy(foregroundPackage = packageName)
    }

    // ---------------------------------------------------------------- engine control

    /**
     * Kill switch (DoD 21): stop everything now and remember that choice.
     *
     * Clears every standing continuous-tracking request, not just the listener. A background
     * consumer — System effects, Fold Wallpaper — does not get to keep the sensor alive through a
     * kill switch by having asked before it was pressed; "instantly return to original behaviour"
     * means the ledger is wiped too, not only its immediate effect.
     */
    fun disable() {
        settings.setEnabled(false)
        continuousTrackingOwners.clear()
        forceStopHingeTracking()
        _state.value = _state.value.copy(engineStatus = EngineStatus.Disabled)
    }

    fun enable() {
        settings.setEnabled(true)
        _state.value = _state.value.copy(
            engineStatus = EngineStatus.Active,
            sessionStartedAtMillis = clock(),
        )
        // A consumer may have requested continuous tracking while the engine was off and been
        // silently refused by startHingeTracking()'s own gate; re-enabling is the moment to honour
        // that standing request rather than waiting for the next unrelated posture event.
        reevaluateContinuousTracking()
    }

    /** Reset F/LAB (DoD 21): clear F/LAB's own configuration and return to first-run defaults. */
    fun reset() {
        settings.reset()
        continuousTrackingOwners.clear()
        forceStopHingeTracking()
        _state.value = FLabState()
    }

    fun setProfile(id: ProfileId) = settings.setProfile(id)

    fun setModuleEnabled(module: ModuleId, enabled: Boolean) =
        settings.setModuleEnabled(module, enabled)

    /**
     * Reports that a module failed (DoD 20).
     *
     * Three failures inside a minute and the breaker takes the module out. The engine itself stays
     * up, the other modules stay up, and One UI never knew anything happened.
     */
    fun reportModuleFailure(module: ModuleId, error: Throwable) {
        val current = _state.value
        val moduleState = current.moduleStates[module] ?: ModuleRuntimeState()
        val updated = breaker.recordFailure(
            current = moduleState,
            message = error.javaClass.simpleName + (error.message?.let { ": $it" } ?: ""),
            nowMillis = clock(),
        )
        _state.value = current.copy(moduleStates = current.moduleStates + (module to updated))
        if (updated.disabledByBreaker && module == ModuleId.FoldMotion) forceStopHingeTracking()
    }

    /** Re-arms a module the breaker tripped. Only the user may do this. */
    fun clearModuleFailure(module: ModuleId) {
        val current = _state.value
        val moduleState = current.moduleStates[module] ?: return
        _state.value = current.copy(
            moduleStates = current.moduleStates + (module to breaker.reset(moduleState)),
        )
        if (module == ModuleId.FoldMotion) reevaluateContinuousTracking()
    }

    /**
     * Re-reads battery saver and thermal status.
     *
     * Conserving no longer stops Fold Motion — it drops the veil channels through
     * [effectiveMotionTuning] and nothing else. That distinction is the difference between an app
     * that visibly works on a real Galaxy Fold and one that silently does nothing: battery saver at
     * 40% and a `THERMAL_STATUS_MODERATE` after a few minutes of use are both routine there, and
     * treating either as "switch the whole feature off" left every effect dead while Home read ON.
     */
    fun refreshPowerPosture() {
        val thermal = if (powerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager.currentThermalStatus
        } else {
            PowerManager.THERMAL_STATUS_NONE
        }
        val (posture, signal) = when {
            powerManager == null -> PowerPosture.Normal to PowerSignal.None
            thermal >= PowerManager.THERMAL_STATUS_SEVERE -> PowerPosture.Restricted to PowerSignal.Thermal
            powerManager.isPowerSaveMode -> PowerPosture.Conserving to PowerSignal.BatterySaver
            thermal >= PowerManager.THERMAL_STATUS_MODERATE -> PowerPosture.Conserving to PowerSignal.Thermal
            else -> PowerPosture.Normal to PowerSignal.None
        }
        val current = _state.value
        if (current.power == posture && current.powerSignal == signal) return
        _state.value = current.copy(power = posture, powerSignal = signal)
        if (posture == PowerPosture.Restricted) {
            forceStopHingeTracking()
        } else {
            // Recovering from a thermal restriction is the moment to resume any standing request
            // that the restriction had force-stopped out from under its owner.
            reevaluateContinuousTracking()
        }
    }

    // ---------------------------------------------------------------- internals

    private fun applyConfiguration(config: FLabConfiguration) {
        configuration = config
        val current = _state.value
        val modules = current.moduleStates.mapValues { (id, runtime) ->
            runtime.copy(enabled = config.moduleEnabled[id] ?: true)
        }
        _state.value = current.copy(
            engineStatus = if (config.enabled) EngineStatus.Active else EngineStatus.Disabled,
            activeProfile = config.profileId,
            moduleStates = modules,
            sessionStartedAtMillis = if (current.sessionStartedAtMillis == 0L && config.enabled) {
                clock()
            } else {
                current.sessionStartedAtMillis
            },
        )
        // Symmetric on purpose: this runs on every settings change, not only enable/disable, so a
        // standing continuous-tracking request is resumed the moment any gate it depends on —
        // engine enabled, or the Fold Motion module specifically — opens back up, rather than
        // waiting for whichever caller happens to notice and ask again.
        if (config.enabled) {
            reevaluateContinuousTracking()
        } else {
            forceStopHingeTracking()
        }
    }

    private fun onWindowLayout(activity: Activity, layoutInfo: WindowLayoutInfo) {
        val feature = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        val current = _state.value

        val posture = when {
            feature == null -> if (current.fold.isFoldable) FoldPosture.Closed else FoldPosture.Unknown
            feature.state == FoldingFeature.State.FLAT -> FoldPosture.Open
            feature.state == FoldingFeature.State.HALF_OPENED -> FoldPosture.HalfOpened
            else -> FoldPosture.Unknown
        }
        val hingeOrientation = when (feature?.orientation) {
            FoldingFeature.Orientation.VERTICAL -> HingeOrientation.Vertical
            FoldingFeature.Orientation.HORIZONTAL -> HingeOrientation.Horizontal
            else -> HingeOrientation.None
        }

        val fold = current.fold.copy(
            posture = posture,
            hingeOrientation = hingeOrientation,
            isSeparating = feature?.isSeparating ?: false,
        )
        _state.value = current.copy(fold = fold)
        onWindowMetrics(activity)

        // A posture change is coarse evidence, and it is also the cue to start listening to the
        // hinge sensor, which is the fine evidence. If there is no sensor, this is all we get.
        submitPostureEvidence(posture)
        startHingeTracking()
    }

    private fun submitPostureEvidence(posture: FoldPosture) {
        val progress = when (posture) {
            FoldPosture.Open -> 1f
            FoldPosture.HalfOpened -> 0.5f
            FoldPosture.Closed -> 0f
            else -> return
        }
        _evidence.value = FoldEvidence(progress, EvidenceSource.PostureEvent, System.nanoTime())
    }

    /**
     * Starts listening to the hinge angle.
     *
     * Idempotent, and a no-op when the module is not running or the device has no hinge sensor.
     * The listener is torn down by [stopHingeTracking] once motion settles — see
     * `FoldMotionHost` for where that happens in the render loop.
     */
    fun startHingeTracking() {
        if (hingeJob?.isActive == true) return
        if (!hingeSource.isAvailable) return
        if (!_state.value.isModuleRunning(ModuleId.FoldMotion)) return

        markHingeTracking(true)
        hingeJob = hingeSource.angles()
            .onEach { degrees ->
                val progress = hingeSource.normalise(degrees)
                _evidence.value = FoldEvidence(
                    progress = progress,
                    source = EvidenceSource.HingeAngle,
                    timestampNanos = System.nanoTime(),
                )
                val fold = _state.value.fold
                if (fold.hingeAngleDegrees?.equals(degrees) != true) {
                    _state.value = _state.value.copy(
                        fold = fold.copy(
                            hingeAngleDegrees = degrees,
                            progress = progress,
                            evidenceSource = EvidenceSource.HingeAngle,
                        ),
                    )
                }
            }
            .launchIn(scope)
    }

    /**
     * Keeps the hinge listener registered even when motion settles, for as long as [owner] holds
     * the request.
     *
     * Without this, the two background renderers — System effects, Fold Wallpaper — would fight
     * the in-app path: it calls [stopHingeTracking] as soon as motion settles, which would tear
     * down the very source either of them depends on to notice the *next* fold. Ref-counted by
     * owner rather than a single flag, so one background consumer switching off cannot silently
     * strand another that is still holding a request — the wallpaper visible on the home screen and
     * the System effects overlay are entirely independent callers and must not be able to cancel
     * each other.
     *
     * This is the honest cost of reacting while F/LAB is not on screen — something has to be
     * listening. The hinge sensor is the cheapest continuous source available and is what the
     * platform itself uses for posture, but it is not free, which is why it is opt-in, per-caller,
     * and released the moment every caller that asked for it has let go.
     */
    fun requestContinuousTracking(owner: Any) {
        continuousTrackingOwners += owner
        reevaluateContinuousTracking()
    }

    /** Releases [owner]'s hold on continuous tracking. The listener stays up for any other holder. */
    fun releaseContinuousTracking(owner: Any) {
        continuousTrackingOwners -= owner
        if (continuousTrackingOwners.isEmpty()) forceStopHingeTracking()
    }

    /**
     * Releases the hinge listener. Called when in-app motion settles, and on detach.
     *
     * A no-op while any [requestContinuousTracking] caller still holds the request, so a settling
     * animation in F/LAB's own UI cannot silently disable a background effect that needs the sensor
     * to keep running.
     */
    fun stopHingeTracking() {
        if (continuousTrackingOwners.isNotEmpty()) return
        forceStopHingeTracking()
    }

    /**
     * Re-attempts hinge tracking for any standing continuous-tracking request.
     *
     * Safe to call from anywhere, at any time: [startHingeTracking]'s own gates (module enabled,
     * engine active, sensor present) decide whether this does anything. Called after every state
     * transition that could open one of those gates back up — enabling F/LAB, clearing a tripped
     * breaker, recovering from a thermal restriction — so a request made while a gate was closed is
     * honoured as soon as it opens, instead of waiting for an unrelated posture event to happen to
     * pass through the same code path.
     */
    private fun reevaluateContinuousTracking() {
        if (continuousTrackingOwners.isNotEmpty()) startHingeTracking()
    }

    private fun forceStopHingeTracking() {
        hingeJob?.cancel()
        hingeJob = null
        markHingeTracking(false)
    }

    private fun markHingeTracking(tracking: Boolean) {
        val current = _state.value
        if (current.fold.isHingeTracking == tracking) return
        _state.value = current.copy(fold = current.fold.copy(isHingeTracking = tracking))
    }

    /**
     * Best-effort guess at which panel is in use.
     *
     * Android does not tell a normal app "you are on the cover screen", so this is inferred from
     * the window's smallest width and the presence of a hinge. It is a guess, it is labelled as a
     * guess, and nothing destructive depends on it.
     */
    private fun displayFor(size: WindowSize, fold: FoldState): ActiveDisplay = when {
        size == WindowSize.Unknown -> ActiveDisplay.Unknown
        !fold.isFoldable -> ActiveDisplay.Unknown
        minOf(size.widthDp, size.heightDp) >= INNER_DISPLAY_MIN_DP -> ActiveDisplay.Inner
        else -> ActiveDisplay.Cover
    }

    private fun presentationOf(activity: Activity): WindowPresentation = when {
        activity.isInPictureInPictureMode -> WindowPresentation.PictureInPicture
        activity.isInMultiWindowMode ->
            if (isFreeForm(activity)) WindowPresentation.FreeForm else WindowPresentation.SplitScreen
        else -> WindowPresentation.FullScreen
    }

    private fun isFreeForm(activity: Activity): Boolean {
        // There is no public "am I in a pop-up view" API. A multi-window activity whose window is
        // small in both dimensions is a pop-up rather than a split half; the distinction only
        // changes a label, and both answers disable the Immersive module anyway.
        val config = activity.resources.configuration
        return config.screenWidthDp < FREEFORM_MAX_DP && config.screenHeightDp < FREEFORM_MAX_DP
    }

    private companion object {
        const val FREEFORM_MAX_DP = 500
    }
}
