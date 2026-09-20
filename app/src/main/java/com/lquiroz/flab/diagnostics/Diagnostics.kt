package com.lquiroz.flab.diagnostics

import com.lquiroz.flab.core.FLabState
import com.lquiroz.flab.core.ModuleId

/** Static facts about the device and build, gathered once. */
data class DeviceReport(
    val manufacturer: String,
    val model: String,
    val androidRelease: String,
    val sdkInt: Int,
    val oneUiVersion: String?,
    val appVersionName: String,
    val hasHingeSensor: Boolean,
    val isFoldable: Boolean,
) {
    /** The device line shown on the Home screen (DoD 10). */
    val displayName: String
        get() = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
}

/** One thing that went wrong, kept for Diagnostics. */
data class ModuleError(
    val module: ModuleId,
    val message: String,
    val atMillis: Long,
)

/**
 * State of the system-wide overlay, for Diagnostics (DoD 37).
 *
 * [verdictExplanation] is the important field: it answers "why is nothing happening right now"
 * without a debugger, which is the entire reason Diagnostics exists.
 */
data class SystemEffectsReport(
    val enabled: Boolean,
    val serviceRunning: Boolean,
    val hasOverlayPermission: Boolean,
    val accessibilityEnabled: Boolean,
    val blurSupported: Boolean,
    val verdictExplanation: String,
)

/**
 * One display the platform exposes to this app, as `DisplayManager` reports it.
 *
 * This is the probe behind the cover-display bridge: whether a Galaxy Fold offers its cover panel
 * as a second display an app can present on — and in what state — is not documented anywhere, so
 * Diagnostics shows exactly what the device says rather than what a spec sheet implies.
 */
data class DisplayReport(
    val id: Int,
    val name: String,
    val widthPx: Int,
    val heightPx: Int,
    val state: String,
    val isDefault: Boolean,
)

/**
 * A grantable capability and what F/LAB loses without it (DoD 17).
 *
 * The `whatBreaks` field is the point: the Access screen is not allowed to say "turn this on",
 * it has to say what stops working if you do not.
 */
data class AccessRequirement(
    val title: String,
    val why: String,
    val whatBreaks: String,
    val granted: Boolean,
    val experimental: Boolean = false,
)

/**
 * The Diagnostics snapshot (DoD 37).
 *
 * Part of the Core rather than a module, and not switchable: this is the surface you use to find
 * out why a module switched itself off, so it has to outlive every module.
 */
data class DiagnosticsSnapshot(
    val device: DeviceReport,
    val state: FLabState,
    val access: List<AccessRequirement>,
    val lastError: ModuleError?,
    val compatibilityRuleCount: Int,
    val capturedAtMillis: Long,
    /** State of the system-wide overlay, or null when the feature is off. */
    val systemEffects: SystemEffectsReport? = null,
    /** Every display the platform exposes to F/LAB right now. */
    val displays: List<DisplayReport> = emptyList(),
    /** What F/LAB Home's cover-display bridge last did, in its own words. */
    val coverBridgeStatus: String? = null,
) {
    val hasBlockingIssue: Boolean
        get() = access.any { !it.granted && !it.experimental } ||
            state.moduleStates.values.any { it.disabledByBreaker }
}
