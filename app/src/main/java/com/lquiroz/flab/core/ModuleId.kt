package com.lquiroz.flab.core

/**
 * The F/LAB modules.
 *
 * DoD 44 requires Fold Motion, Continuity, Immersive and App Profiles for v1. Diagnostics is part
 * of the Core rather than a module, so it is not listed here — it cannot be disabled, because it is
 * what you use to work out why something else was disabled.
 */
enum class ModuleId(
    val displayName: String,
    /** Shown on the F/LAB Access screen so a permission request is never "turn this on because". */
    val purpose: String,
    /** Whether the module keeps running once the device asks F/LAB to conserve power. */
    val survivesPowerSaving: Boolean,
    /** Whether the module needs a permission beyond the normal app sandbox. */
    val requiresElevatedAccess: Boolean,
) {
    FoldMotion(
        displayName = "Fold Motion",
        purpose = "Reads the hinge angle to drive opening and closing motion inside F/LAB.",
        survivesPowerSaving = false,
        requiresElevatedAccess = false,
    ),
    Continuity(
        displayName = "Continuity",
        purpose = "Bridges the cover and inner display so a switch does not read as a restart.",
        survivesPowerSaving = true,
        requiresElevatedAccess = false,
    ),
    Immersive(
        displayName = "Immersive",
        purpose = "Blends the system bars into the content of F/LAB's own surfaces.",
        survivesPowerSaving = true,
        requiresElevatedAccess = false,
    ),
    AppProfiles(
        displayName = "App Profiles",
        purpose = "Decides which treatment each app and each context should receive.",
        survivesPowerSaving = true,
        requiresElevatedAccess = false,
    ),
}

/**
 * Per-module runtime state, owned by the Core.
 *
 * [enabled] is the user's intent and survives restarts. [disabledByBreaker] is the Core's own
 * decision after repeated crashes (DoD 20) and never overwrites the user's setting, so switching
 * the module back on later does the obvious thing.
 */
data class ModuleRuntimeState(
    val enabled: Boolean = true,
    val disabledByBreaker: Boolean = false,
    val lastErrorMessage: String? = null,
    val lastErrorAtMillis: Long? = null,
    val consecutiveFailures: Int = 0,
) {
    val isAvailable: Boolean get() = enabled && !disabledByBreaker

    /** Human-readable reason a module is not running, for the "Action required" surface (DoD 43). */
    val unavailableReason: String?
        get() = when {
            disabledByBreaker -> "Disabled automatically after repeated failures"
            !enabled -> "Turned off"
            else -> null
        }
}
