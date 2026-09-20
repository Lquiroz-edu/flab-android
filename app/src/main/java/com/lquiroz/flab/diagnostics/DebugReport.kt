package com.lquiroz.flab.diagnostics

import com.lquiroz.flab.core.ModuleId
import java.util.Locale

/**
 * Builds the shareable debug report (DoD 38).
 *
 * The report has to answer "what was F/LAB doing when this went wrong" and nothing else. What it
 * deliberately does not contain:
 *
 *  - No installed-app inventory. Only packages the user has explicitly configured appear, and only
 *    those the user configured — the report never enumerates what is on the device.
 *  - No account, device or advertising identifiers, no serial number, no IMEI.
 *  - No screen contents, no sampled colours from other apps, no window titles.
 *  - No absolute timestamps. Everything is relative to the capture, so the report does not
 *    reconstruct when the device was used.
 *
 * Package names of configured apps are the one borderline item, and they are included because a
 * bug report about Instagram is useless without knowing it was Instagram. They can be dropped with
 * [includeConfiguredApps].
 */
object DebugReport {

    fun build(
        snapshot: DiagnosticsSnapshot,
        configuredPackages: List<String> = emptyList(),
        includeConfiguredApps: Boolean = true,
    ): String = buildString {
        val device = snapshot.device
        appendLine("F/LAB debug report")
        appendLine("==================")
        appendLine()

        appendLine("Device")
        appendLine("  Model          ${device.displayName}")
        appendLine("  Android        ${device.androidRelease} (API ${device.sdkInt})")
        appendLine("  One UI         ${device.oneUiVersion ?: "not reported"}")
        appendLine("  Foldable       ${device.isFoldable.yesNo()}")
        appendLine("  Hinge sensor   ${device.hasHingeSensor.yesNo()}")
        appendLine("  F/LAB          ${device.appVersionName}")
        appendLine()

        val state = snapshot.state
        appendLine("Engine")
        appendLine("  Status         ${state.engineStatus.label}")
        appendLine("  Profile        ${state.activeProfile.displayName}")
        appendLine("  Power          ${state.power.label}")
        appendLine("  Uptime         ${relativeDuration(snapshot.capturedAtMillis - state.sessionStartedAtMillis)}")
        appendLine()

        appendLine("Fold")
        appendLine("  Posture        ${state.fold.posture.label}")
        appendLine("  Progress       ${state.fold.progress.format()}")
        appendLine("  Hinge angle    ${state.fold.hingeAngleDegrees?.format()?.plus("°") ?: "not reported"}")
        appendLine("  Evidence       ${state.fold.evidenceSource.label}")
        appendLine("  Hinge listener ${if (state.fold.isHingeTracking) "active" else "idle"}")
        appendLine("  Separating     ${state.fold.isSeparating.yesNo()}")
        appendLine()

        appendLine("Window")
        appendLine("  Size           ${state.window.size.widthDp} x ${state.window.size.heightDp} dp")
        appendLine("  Display        ${state.window.display.label}")
        appendLine("  Presentation   ${state.window.presentation.label}")
        appendLine("  Orientation    ${state.window.orientation.label}")
        appendLine()

        appendLine("Modules")
        ModuleId.entries.forEach { module ->
            val runtime = state.moduleStates[module]
            val status = when {
                runtime == null -> "unknown"
                state.isModuleRunning(module) -> "running"
                else -> runtime.unavailableReason ?: "not running"
            }
            appendLine("  ${module.displayName.padEnd(14)} $status")
            runtime?.lastErrorMessage?.let { appendLine("    last error   $it") }
        }
        appendLine()

        appendLine("Permissions")
        snapshot.access.forEach {
            val suffix = if (it.experimental) " (experimental)" else ""
            appendLine("  ${it.title.padEnd(14)} ${it.granted.granted()}$suffix")
        }
        appendLine()

        snapshot.systemEffects?.let { effects ->
            appendLine("System effects")
            appendLine("  Enabled        ${effects.enabled.yesNo()}")
            appendLine("  Service        ${if (effects.serviceRunning) "running" else "stopped"}")
            appendLine("  Overlay perm   ${effects.hasOverlayPermission.granted()}")
            appendLine("  App awareness  ${effects.accessibilityEnabled.granted()}")
            appendLine("  Blur behind    ${if (effects.blurSupported) "supported" else "unavailable, using dim"}")
            appendLine("  Last verdict   ${effects.verdictExplanation}")
            appendLine()
        }

        appendLine("Displays")
        if (snapshot.displays.isEmpty()) appendLine("  none reported")
        snapshot.displays.forEach { display ->
            val tags = listOfNotNull(
                "default".takeIf { display.isDefault },
                "disabled".takeIf { display.hidden },
            )
            val suffix = if (tags.isEmpty()) "" else " (${tags.joinToString()})"
            appendLine("  #${display.id} ${display.name}$suffix: ${display.widthPx}x${display.heightPx}, ${display.state}")
        }
        appendLine("  Cover bridge   ${snapshot.coverBridgeStatus ?: "not started"}")
        snapshot.coverBridgeLog.forEach { appendLine("    $it") }
        appendLine()

        appendLine("Compatibility")
        appendLine("  Rules loaded   ${snapshot.compatibilityRuleCount}")
        if (includeConfiguredApps && configuredPackages.isNotEmpty()) {
            appendLine("  Configured     ${configuredPackages.size} app(s)")
            configuredPackages.sorted().forEach { appendLine("    $it") }
        } else {
            appendLine("  Configured     omitted")
        }
        appendLine()

        appendLine("Last error")
        val error = snapshot.lastError
        if (error == null) {
            appendLine("  none recorded")
        } else {
            appendLine("  Module         ${error.module.displayName}")
            appendLine("  When           ${relativeDuration(snapshot.capturedAtMillis - error.atMillis)} ago")
            appendLine("  Message        ${error.message}")
        }
    }

    /** Renders a duration without ever revealing a wall-clock time. */
    internal fun relativeDuration(millis: Long): String {
        if (millis <= 0L) return "0s"
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    private fun Boolean.yesNo() = if (this) "yes" else "no"
    private fun Boolean.granted() = if (this) "granted" else "not granted"
    private fun Float.format() = String.format(Locale.US, "%.2f", this)
}
