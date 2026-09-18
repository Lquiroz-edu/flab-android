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
) {
    val hasBlockingIssue: Boolean
        get() = access.any { !it.granted && !it.experimental } ||
            state.moduleStates.values.any { it.disabledByBreaker }
}
