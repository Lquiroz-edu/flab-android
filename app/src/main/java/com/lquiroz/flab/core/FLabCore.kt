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
 * Every subscription here is event-driven. The hinge sensor is the only high-rate source and it is
 * registered only while a transition is in flight; when the interpolator settles, the listener
 * goes away. There is no timer, no poll and no wake lock anywhere in this class (DoD 22, 25).
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
    private var hingeJob: Job? = null
    private var configuration: FLabConfiguration = FLabConfiguration()

    val activeProfile: FLabProfile
        get() = FLabProfile.of(configuration.profileId)

    val isHingeSensorAvailable: Boolean get() = hingeSource.isAvailable

    init {
        settings.observe()
            .onEach(::applyConfiguration)
            .launchIn(scope)
    }

    /** Binds the Core to an Activity's window. Safe to call repeatedly. */
    fun attach(activity: Activity) {
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

    fun detach() {
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

    /** Kill switch (DoD 21): stop everything now and remember that choice. */
    fun disable() {
        settings.setEnabled(false)
        stopHingeTracking()
        _state.value = _state.value.copy(engineStatus = EngineStatus.Disabled)
    }

    fun enable() {
        settings.setEnabled(true)
        _state.value = _state.value.copy(
            engineStatus = EngineStatus.Active,
            sessionStartedAtMillis = clock(),
        )
    }

    /** Reset F/LAB (DoD 21): clear F/LAB's own configuration and return to first-run defaults. */
    fun reset() {
        settings.reset()
        stopHingeTracking()
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
        if (updated.disabledByBreaker && module == ModuleId.FoldMotion) stopHingeTracking()
    }

    /** Re-arms a module the breaker tripped. Only the user may do this. */
    fun clearModuleFailure(module: ModuleId) {
        val current = _state.value
        val moduleState = current.moduleStates[module] ?: return
        _state.value = current.copy(
            moduleStates = current.moduleStates + (module to breaker.reset(moduleState)),
        )
    }

    fun refreshPowerPosture() {
        val posture = when {
            powerManager == null -> PowerPosture.Normal
            powerManager.isPowerSaveMode -> PowerPosture.Conserving
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE ->
                PowerPosture.Restricted
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE ->
                PowerPosture.Conserving
            else -> PowerPosture.Normal
        }
        if (_state.value.power == posture) return
        _state.value = _state.value.copy(power = posture)
        if (posture == PowerPosture.Restricted) stopHingeTracking()
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
        if (!config.enabled) stopHingeTracking()
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

        markTransitioning(true)
        hingeJob = hingeSource.angles()
            .onEach { degrees ->
                _evidence.value = FoldEvidence(
                    progress = hingeSource.normalise(degrees),
                    source = EvidenceSource.HingeAngle,
                    timestampNanos = System.nanoTime(),
                )
                val fold = _state.value.fold
                if (fold.hingeAngleDegrees?.equals(degrees) != true) {
                    _state.value = _state.value.copy(
                        fold = fold.copy(
                            hingeAngleDegrees = degrees,
                            evidenceSource = EvidenceSource.HingeAngle,
                        ),
                    )
                }
            }
            .launchIn(scope)
    }

    /** Releases the hinge listener. Called when motion settles, on detach, and on disable. */
    fun stopHingeTracking() {
        hingeJob?.cancel()
        hingeJob = null
        markTransitioning(false)
    }

    private fun markTransitioning(transitioning: Boolean) {
        val current = _state.value
        if (current.fold.isTransitioning == transitioning) return
        _state.value = current.copy(fold = current.fold.copy(isTransitioning = transitioning))
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
        /** Below this smallest-width, a foldable window is almost certainly the cover display. */
        const val INNER_DISPLAY_MIN_DP = 560
        const val FREEFORM_MAX_DP = 500
    }
}
