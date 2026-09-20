package com.lquiroz.flab.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lquiroz.flab.FLabApplication
import com.lquiroz.flab.compat.CompatibilityRegistry
import com.lquiroz.flab.compat.ConfigResolver
import com.lquiroz.flab.core.EngineStatus
import com.lquiroz.flab.core.Experiments
import com.lquiroz.flab.core.FLabState
import com.lquiroz.flab.core.ModuleId
import com.lquiroz.flab.core.PowerPosture
import com.lquiroz.flab.core.SetupProgress
import com.lquiroz.flab.core.forPower
import com.lquiroz.flab.motion.MotionTuning
import com.lquiroz.flab.diagnostics.AccessRequirement
import com.lquiroz.flab.diagnostics.DebugReport
import com.lquiroz.flab.diagnostics.DeviceReport
import com.lquiroz.flab.diagnostics.DiagnosticsSnapshot
import com.lquiroz.flab.diagnostics.DisplayProbe
import com.lquiroz.flab.diagnostics.ModuleError
import com.lquiroz.flab.launcher.CoverDisplayBridge
import com.lquiroz.flab.diagnostics.SystemEffectsReport
import com.lquiroz.flab.profiles.AppProfile
import com.lquiroz.flab.profiles.DefaultAppProfiles
import com.lquiroz.flab.profiles.FLabProfile
import com.lquiroz.flab.profiles.ProfileId
import com.lquiroz.flab.profiles.TreatmentMode
import com.lquiroz.flab.settings.FLabConfiguration
import com.lquiroz.flab.system.FLabOverlayService
import com.lquiroz.flab.system.FoldOverlayWindow
import com.lquiroz.flab.system.OverlayVerdict
import com.lquiroz.flab.system.SystemAccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The screens F/LAB has. Flat on purpose: this is a control panel, not a hierarchy. */
enum class FLabScreen(val title: String) {
    Onboarding("Welcome"),
    Home("F/LAB"),
    FoldMotion("Fold Motion"),
    Apps("Apps"),
    Profiles("Profiles"),
    Experiments("F/LAB Experiments"),
    Access("F/LAB Access"),
    Diagnostics("F/LAB Diagnostics"),
}

/** Everything a screen needs, assembled once. */
data class FLabUiState(
    val state: FLabState = FLabState(),
    val configuration: FLabConfiguration = FLabConfiguration(),
    val profile: FLabProfile = FLabProfile.Balanced,
    val appProfiles: List<AppProfile> = DefaultAppProfiles.seeded,
    val device: DeviceReport? = null,
    val systemEffects: SystemEffectsState = SystemEffectsState(),
    /** Whether F/LAB Home is the default home screen — where the Duo icon reflow lives. */
    val isDefaultHome: Boolean = false,
    val isFoldWallpaperActive: Boolean = false,
) {
    val configuredAppCount: Int get() = appProfiles.count { !it.isDisabled }

    /** The tuning F/LAB's own screens actually animate with, power posture included. */
    val effectiveMotion: MotionTuning get() = profile.motion.forPower(state.power)

    /** Whether the System effects preview can show anything right now (see `SystemEffectPolicy`). */
    val canPreviewSystemEffects: Boolean
        get() = systemEffects.enabled && systemEffects.canRun && state.power == PowerPosture.Normal

    /** The Home health line from DoD 43. */
    val healthLine: String
        get() = when {
            !configuration.enabled -> "F/LAB is off"
            state.moduleStates.values.any { it.disabledByBreaker } -> "Action required"
            state.activeModules.isEmpty() -> "No modules running"
            else -> "F/LAB Active"
        }

    /**
     * Whether [AttentionCard][com.lquiroz.flab.ui.screens.HomeScreen] shows.
     *
     * Deliberately scoped to breaker trips only, not to System effects missing a grant — that case
     * is fully owned by the Home setup checklist (`SetupProgress`), which reappears automatically
     * the moment any of its three steps becomes undone, permission revoked later included, and
     * says exactly what is missing with a button to fix it. Folding that case in here as well would
     * show a second, emptier card underneath the checklist with nothing of its own to say.
     */
    val needsAttention: Boolean
        get() = configuration.enabled && state.moduleStates.values.any { it.disabledByBreaker }
}

/**
 * Everything the UI needs to describe the system-wide effect.
 *
 * [canRun] is the honest summary: both capabilities granted. With either one missing the effect
 * cannot work at all, so Home says which one rather than offering a switch that does nothing.
 */
data class SystemEffectsState(
    val enabled: Boolean = false,
    val serviceRunning: Boolean = false,
    val hasOverlayPermission: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    /**
     * Whether F/LAB's own engine is on — the main pill on Home, not this feature's own switch.
     *
     * System effects extends Fold Motion outside F/LAB's window; it has nothing to extend if the
     * engine itself is off. Without this in [canRun], the Home row could read "ON" and Access could
     * show both grants satisfied while the effect can never actually fire — exactly what happened
     * before this field existed: enabling System effects with the main engine off left every visible
     * signal claiming success.
     */
    val engineEnabled: Boolean = false,
    val verdict: OverlayVerdict = OverlayVerdict.ModuleOff,
) {
    val canRun: Boolean get() = hasOverlayPermission && accessibilityEnabled && engineEnabled

    val missing: List<String>
        get() = buildList {
            if (!engineEnabled) add("F/LAB turned on")
            if (!hasOverlayPermission) add("Display over other apps")
            if (!accessibilityEnabled) add("App awareness")
        }
}

/**
 * Bridges the Core to Compose.
 *
 * Holds no engine state of its own: every value here is derived from [FLabCore.state] and the
 * settings store, so the UI cannot drift from what the engine actually believes. That matters for
 * DoD 37 — a Diagnostics screen that shows a cached copy of the truth is worse than no Diagnostics
 * screen at all.
 */
class FLabViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FLabApplication
    private val core = app.core
    private val settings = app.settings

    private val _screen = MutableStateFlow(FLabScreen.Home)
    val screen: StateFlow<FLabScreen> = _screen.asStateFlow()

    private val _previewProgress = MutableStateFlow(0f)

    /** The Live Preview scrubber position (DoD 11). Never touches the running configuration. */
    val previewProgress: StateFlow<Float> = _previewProgress.asStateFlow()

    private val resolvedConfig = ConfigResolver().resolve()

    val registry: CompatibilityRegistry get() = resolvedConfig.registry

    /**
     * Bumped whenever the app comes back to the foreground.
     *
     * Overlay and accessibility grants are made in Settings, outside this process, and nothing
     * notifies us when they change. Without this the Access screen would still be claiming a
     * permission is missing after the user had just granted it.
     */
    private val accessRefresh = MutableStateFlow(0)

    val uiState: StateFlow<FLabUiState> = combine(
        core.state,
        settings.observe(),
        FLabOverlayService.isRunning,
        FLabOverlayService.currentVerdict,
        accessRefresh,
    ) { state, configuration, serviceRunning, verdict, _ ->
        FLabUiState(
            state = state,
            configuration = configuration,
            profile = FLabProfile.of(configuration.profileId),
            appProfiles = mergedAppProfiles(configuration),
            device = deviceReport(),
            systemEffects = SystemEffectsState(
                enabled = configuration.systemEffectsEnabled,
                serviceRunning = serviceRunning,
                hasOverlayPermission = SystemAccess.canDrawOverlays(application),
                accessibilityEnabled = SystemAccess.isAccessibilityServiceEnabled(application),
                engineEnabled = state.engineStatus == EngineStatus.Active,
                verdict = verdict,
            ),
            isDefaultHome = SystemAccess.isDefaultHome(application),
            isFoldWallpaperActive = SystemAccess.isFoldWallpaperActive(application),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = FLabUiState(device = deviceReport()),
    )

    val evidence = core.evidence

    // ------------------------------------------------------------------ navigation

    fun navigate(screen: FLabScreen) {
        _screen.value = screen
    }

    fun back() {
        _screen.value = FLabScreen.Home
    }

    /**
     * Called by the renderer when Fold Motion has settled.
     *
     * This is what actually releases the hinge-angle listener. Without it the sensor stays
     * registered for the rest of the process after the first posture change, which would quietly
     * make the "no continuous polling" property (DoD 22) untrue.
     */
    fun onMotionSettled() = core.stopHingeTracking()

    // ------------------------------------------------------------------ actions

    fun setEnabled(enabled: Boolean) = if (enabled) core.enable() else core.disable()

    /**
     * The Home setup checklist's first tap (DoD 18, 43): turns the engine on and records the
     * intent to run System effects in the same action, rather than leaving "flip that toggle too"
     * as a fourth thing to remember after granting two permissions.
     *
     * Safe to call before either permission is granted — [setSystemEffectsEnabled] only starts the
     * service once both are in place, and does nothing harmful otherwise (see [SetupProgress]).
     */
    fun beginGuidedSetup() {
        setEnabled(true)
        setSystemEffectsEnabled(true)
    }

    fun setProfile(id: ProfileId) = core.setProfile(id)

    fun setModuleEnabled(module: ModuleId, enabled: Boolean) =
        core.setModuleEnabled(module, enabled)

    fun clearModuleFailure(module: ModuleId) = core.clearModuleFailure(module)

    fun setExperimentsEnabled(enabled: Boolean) = settings.setExperimentsEnabled(enabled)

    /**
     * Turns the system-wide effect on or off.
     *
     * Refuses to start without both grants rather than starting a service that would abstain on
     * every frame: a foreground service running for nothing is a battery cost with no effect, and
     * a notification claiming F/LAB is doing something it cannot do is a lie.
     */
    fun setSystemEffectsEnabled(enabled: Boolean) {
        val context = getApplication<Application>()
        settings.setSystemEffectsEnabled(enabled)
        if (enabled && SystemAccess.canDrawOverlays(context) &&
            SystemAccess.isAccessibilityServiceEnabled(context)
        ) {
            FLabOverlayService.start(context)
        } else {
            FLabOverlayService.stop(context)
        }
        refreshAccess()
    }

    /** Runs the system-wide effect over F/LAB's own window for a moment, so it can be seen at all. */
    fun previewSystemEffects() = FLabOverlayService.preview(getApplication())

    /** Re-reads grants made outside the app. Called when F/LAB returns to the foreground. */
    fun refreshAccess() {
        accessRefresh.value += 1
        val context = getApplication<Application>()
        // Reconcile: a grant revoked while we were away must take the service down with it.
        val shouldRun = settings.current().systemEffectsEnabled &&
            SystemAccess.canDrawOverlays(context) &&
            SystemAccess.isAccessibilityServiceEnabled(context)
        if (shouldRun) FLabOverlayService.start(context) else FLabOverlayService.stop(context)
    }

    fun overlayPermissionIntent(): Intent =
        SystemAccess.overlaySettingsIntent(getApplication())

    fun accessibilitySettingsIntent(): Intent = SystemAccess.accessibilitySettingsIntent()

    fun appDetailsIntent(): Intent = SystemAccess.appDetailsIntent(getApplication())

    fun liveWallpaperIntent(): Intent = SystemAccess.changeLiveWallpaperIntent(getApplication())

    fun homeSettingsIntent(): Intent = SystemAccess.homeSettingsIntent()

    fun completeOnboarding() {
        settings.setOnboardingComplete(true)
        _screen.value = FLabScreen.Home
    }

    fun scrubPreview(progress: Float) {
        _previewProgress.value = progress.coerceIn(0f, 1f)
    }

    /**
     * Cycles an app's immersive treatment (DoD 13).
     *
     * A protected app is a no-op rather than a silently-stored preference that the policy would
     * later refuse: the switch should not move if the answer is always going to be no.
     */
    fun cycleImmersive(profile: AppProfile) {
        if (profile.locked) return
        val next = when (profile.immersive) {
            TreatmentMode.Off -> TreatmentMode.Auto
            TreatmentMode.Auto -> TreatmentMode.On
            TreatmentMode.On -> TreatmentMode.Off
        }
        settings.setAppOverride(profile.copy(immersive = next))
    }

    fun cycleContinuity(profile: AppProfile) {
        if (profile.locked) return
        val next = when (profile.continuity) {
            TreatmentMode.Off -> TreatmentMode.Auto
            TreatmentMode.Auto -> TreatmentMode.On
            TreatmentMode.On -> TreatmentMode.Off
        }
        settings.setAppOverride(profile.copy(continuity = next))
    }

    /** Reset F/LAB (DoD 21). */
    fun reset() {
        core.reset()
        _screen.value = FLabScreen.Home
    }

    // ------------------------------------------------------------------ diagnostics

    fun diagnostics(): DiagnosticsSnapshot {
        val state = core.state.value
        val lastError = state.moduleStates.entries
            .mapNotNull { (id, runtime) ->
                val message = runtime.lastErrorMessage ?: return@mapNotNull null
                val at = runtime.lastErrorAtMillis ?: return@mapNotNull null
                ModuleError(id, message, at)
            }
            .maxByOrNull { it.atMillis }

        return DiagnosticsSnapshot(
            device = deviceReport(),
            state = state,
            access = accessRequirements(),
            lastError = lastError,
            compatibilityRuleCount = registry.size,
            capturedAtMillis = System.currentTimeMillis(),
            systemEffects = systemEffectsReport(),
            displays = DisplayProbe.probe(getApplication()),
            coverBridgeStatus = CoverDisplayBridge.status.value,
        )
    }

    private fun systemEffectsReport(): SystemEffectsReport {
        val context = getApplication<Application>()
        return SystemEffectsReport(
            enabled = settings.current().systemEffectsEnabled,
            serviceRunning = FLabOverlayService.isRunning.value,
            hasOverlayPermission = SystemAccess.canDrawOverlays(context),
            accessibilityEnabled = SystemAccess.isAccessibilityServiceEnabled(context),
            blurSupported = FoldOverlayWindow.isBlurAvailable(context),
            verdictExplanation = FLabOverlayService.currentVerdict.value.explanation,
        )
    }

    fun debugReport(): String = DebugReport.build(
        snapshot = diagnostics(),
        configuredPackages = uiState.value.appProfiles
            .filterNot { it.isDisabled }
            .map { it.packageName },
    )

    /**
     * What F/LAB can ask for, and what it loses without each (DoD 17).
     *
     * The list is short because F/LAB's stable modules need nothing beyond the normal sandbox.
     * Everything that would need more is an experiment, and says so.
     */
    fun accessRequirements(): List<AccessRequirement> = buildList {
        add(
            AccessRequirement(
                title = "Hinge sensor",
                why = "Reads the hinge angle so opening motion follows the device rather than " +
                    "replaying a fixed animation.",
                whatBreaks = "Fold Motion falls back to coarse posture events. It still works, " +
                    "but it is less closely attached to the movement.",
                granted = core.isHingeSensorAvailable,
            ),
        )
        add(
            AccessRequirement(
                title = "Notifications",
                why = "Tells you when a module switches itself off after repeated failures.",
                whatBreaks = "A module can switch off without you noticing. Diagnostics still " +
                    "records it.",
                granted = notificationsGranted(),
            ),
        )
        Experiments.accessibilityDependent.forEach { experiment ->
            add(
                AccessRequirement(
                    title = experiment.title,
                    why = experiment.accessRationale ?: experiment.description,
                    whatBreaks = "Per-app treatment applies only inside F/LAB's own surfaces.",
                    granted = false,
                    experimental = true,
                ),
            )
        }
    }

    // ------------------------------------------------------------------ internals

    private fun mergedAppProfiles(configuration: FLabConfiguration): List<AppProfile> {
        val overridden = configuration.appOverrides.associateBy { it.packageName }
        val seeded = DefaultAppProfiles.seeded.map { seed ->
            overridden[seed.packageName]?.copy(
                displayName = seed.displayName,
                locked = seed.locked,
                note = seed.note,
            ) ?: seed
        }
        val extra = configuration.appOverrides.filterNot { override ->
            DefaultAppProfiles.seeded.any { it.packageName == override.packageName }
        }
        return (seeded + extra).sortedWith(
            compareBy({ it.locked }, { it.displayName.lowercase() }),
        )
    }

    private fun deviceReport(): DeviceReport {
        val context = getApplication<Application>()
        val versionName = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"

        return DeviceReport(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidRelease = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            oneUiVersion = oneUiVersion(),
            appVersionName = versionName,
            hasHingeSensor = core.isHingeSensorAvailable,
            isFoldable = core.state.value.fold.isFoldable || core.isHingeSensorAvailable,
        )
    }

    /**
     * Identifies One UI, for the Diagnostics line DoD 37 asks for.
     *
     * The exact One UI version number is not available to a normal app. It lives in
     * `ro.build.version.oneui`, and `android.os.SystemProperties` has been on Android's
     * non-SDK denylist since Android 9 — reflecting into it returns null on every device this app
     * targets while looking like it might work, which is the worst of both outcomes.
     *
     * So this reports what a public API can actually establish: whether the device runs One UI at
     * all, via Samsung's own system feature. A build number is included because it is the closest
     * public proxy and it is genuinely useful in a bug report. Nothing branches on either value.
     */
    private fun oneUiVersion(): String? {
        val packageManager = getApplication<Application>().packageManager
        val isOneUi = SAMSUNG_EXPERIENCE_FEATURES.any(packageManager::hasSystemFeature)
        if (!isOneUi) return null
        return "One UI (build ${Build.DISPLAY})"
    }

    private fun notificationsGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val context = getApplication<Application>()
        return context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /** Samsung declares one of these on every One UI build. */
        val SAMSUNG_EXPERIENCE_FEATURES = listOf(
            "com.samsung.feature.samsung_experience_mobile",
            "com.samsung.feature.samsung_experience_mobile_lite",
        )
    }
}
