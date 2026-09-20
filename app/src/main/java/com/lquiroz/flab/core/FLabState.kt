package com.lquiroz.flab.core

import com.lquiroz.flab.motion.EvidenceSource
import com.lquiroz.flab.motion.MotionTuning
import com.lquiroz.flab.profiles.ProfileId

/**
 * Physical posture of the hinge.
 *
 * Deliberately coarse. Anything that wants a smooth value reads
 * [FoldState.progress] instead — posture is for decisions, progress is for motion.
 */
enum class FoldPosture(val label: String) {
    Closed("Closed"),
    Transitioning("Transitioning"),
    HalfOpened("Half open"),
    Open("Open"),
    Unknown("Unknown"),
}

enum class HingeOrientation(val label: String) {
    Vertical("Vertical"),
    Horizontal("Horizontal"),
    None("None"),
}

enum class ScreenOrientation(val label: String) {
    Portrait("Portrait"),
    Landscape("Landscape"),
}

/**
 * A window whose smaller side is at least this wide is a Fold's inner display. Shared by the Core's
 * own guess, F/LAB Home's column count and the wallpaper's warp gate, so all three agree on which
 * panel they are looking at.
 */
const val INNER_DISPLAY_MIN_DP = 560

/** Which physical panel the app is currently on, as far as we can tell. */
enum class ActiveDisplay(val label: String) {
    Cover("Cover display"),
    Inner("Inner display"),
    External("External display"),
    Unknown("Unknown"),
}

/**
 * Window presentation mode.
 *
 * [Companion.of] treats anything that is not the full display area as multi-window. Several
 * modules (notably Immersive) must disable themselves outright in that case, because they cannot
 * assume they own the status bar any more (DoD 33).
 */
enum class WindowPresentation(val label: String) {
    FullScreen("Full screen"),
    SplitScreen("Split screen"),
    FreeForm("Pop-up view"),
    PictureInPicture("Picture in picture"),
    Unknown("Unknown");

    val isMultiWindow: Boolean
        get() = this == SplitScreen || this == FreeForm || this == PictureInPicture
}

/** Size of the current window in density-independent pixels. */
data class WindowSize(val widthDp: Int, val heightDp: Int) {
    val orientation: ScreenOrientation
        get() = if (widthDp >= heightDp) ScreenOrientation.Landscape else ScreenOrientation.Portrait

    companion object {
        val Unknown = WindowSize(0, 0)
    }
}

/**
 * How much of the device's power budget F/LAB is allowed to spend.
 *
 * The engine folds Android's battery saver and thermal signals into one value so modules do not
 * each have to ask the platform, and so a single place decides what "back off" means (DoD 25, 26).
 */
enum class PowerPosture(val label: String) {
    /** Full visual treatment. */
    Normal("Normal"),
    /** Reduced treatment: veil channels off, position channels kept. */
    Conserving("Conserving"),
    /** No treatment at all until the device recovers. */
    Restricted("Restricted"),
}

/**
 * Which platform signal produced the current [PowerPosture], so Home can say "battery saver is
 * on" or "the device is warm" instead of a generic "conserving" the user cannot act on (DoD 43).
 */
enum class PowerSignal(val label: String) {
    None("Normal"),
    BatterySaver("Battery saver"),
    Thermal("Device temperature"),
}

/**
 * The tuning a renderer should actually run at under [power].
 *
 * Conserving keeps the motion and drops the veil ([MotionTuning.withoutVeil]); Restricted is the
 * inert Battery tuning, so a frame loop never starts at all. One function, used by every consumer
 * — the in-app host, the overlay service, the wallpaper — so they cannot disagree about what a
 * power posture means.
 */
fun MotionTuning.forPower(power: PowerPosture): MotionTuning = when (power) {
    PowerPosture.Normal -> this
    PowerPosture.Conserving -> withoutVeil()
    PowerPosture.Restricted -> MotionTuning.Battery
}

/** Whether F/LAB is doing anything at all. The kill switch (DoD 21) moves this to [Disabled]. */
enum class EngineStatus(val label: String) {
    Active("Active"),
    Paused("Paused"),
    Disabled("Disabled"),
    ActionRequired("Action required"),
}

/**
 * Everything F/LAB knows about the device and about itself, in one immutable value.
 *
 * This is the single source of truth required by DoD 2. No module is allowed to build its own
 * fold detection, subscribe to `WindowInfoTracker` directly, or read the hinge sensor: they read
 * this, and only this. That rule is what makes a continuous
 * `closed -> part open -> open -> part closed -> closed` session possible without restarting a
 * service or dropping configuration, and it is why the engine can be reasoned about in tests.
 */
data class FLabState(
    val engineStatus: EngineStatus = EngineStatus.Paused,
    val fold: FoldState = FoldState(),
    val window: WindowState = WindowState(),
    val foregroundPackage: String? = null,
    val activeProfile: ProfileId = ProfileId.Balanced,
    val moduleStates: Map<ModuleId, ModuleRuntimeState> = ModuleId.entries.associateWith {
        ModuleRuntimeState()
    },
    val power: PowerPosture = PowerPosture.Normal,
    val powerSignal: PowerSignal = PowerSignal.None,
    val sessionStartedAtMillis: Long = 0L,
) {
    val activeModules: List<ModuleId>
        get() = ModuleId.entries.filter { isModuleRunning(it) }

    /**
     * A module runs only when the engine is active, the user enabled it, the circuit breaker has
     * not tripped it, and the power posture still allows it.
     */
    fun isModuleRunning(module: ModuleId): Boolean {
        if (engineStatus != EngineStatus.Active) return false
        val state = moduleStates[module] ?: return false
        if (!state.isAvailable) return false
        return when (power) {
            PowerPosture.Normal -> true
            PowerPosture.Conserving -> module.survivesPowerSaving
            PowerPosture.Restricted -> false
        }
    }
}

/** Fold-specific slice of [FLabState]. */
data class FoldState(
    val posture: FoldPosture = FoldPosture.Unknown,
    val hingeOrientation: HingeOrientation = HingeOrientation.None,
    /** Continuous opening, `0f` closed to `1f` flat. See `PerceptualInterpolator`. */
    val progress: Float = 0f,
    /** Raw hinge angle in degrees when the device exposes one, otherwise null. */
    val hingeAngleDegrees: Float? = null,
    val evidenceSource: EvidenceSource = EvidenceSource.PostureEvent,
    val isSeparating: Boolean = false,
    /** True while progress is still travelling, i.e. the frame loop is running. */
    val isTransitioning: Boolean = false,
) {
    /** True when the device reports a hinge at all — false on a phone or a tablet. */
    val isFoldable: Boolean
        get() = hingeOrientation != HingeOrientation.None || hingeAngleDegrees != null
}

/** Window-specific slice of [FLabState]. */
data class WindowState(
    val size: WindowSize = WindowSize.Unknown,
    val presentation: WindowPresentation = WindowPresentation.Unknown,
    val display: ActiveDisplay = ActiveDisplay.Unknown,
    val orientation: ScreenOrientation = ScreenOrientation.Portrait,
) {
    /** True when F/LAB owns the whole window and may therefore treat the system bars. */
    val ownsSystemBars: Boolean
        get() = presentation == WindowPresentation.FullScreen
}
