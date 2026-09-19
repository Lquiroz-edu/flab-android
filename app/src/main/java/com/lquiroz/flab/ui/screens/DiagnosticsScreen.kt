package com.lquiroz.flab.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lquiroz.flab.core.ModuleId
import com.lquiroz.flab.diagnostics.DiagnosticsSnapshot
import com.lquiroz.flab.ui.components.FLabButton
import com.lquiroz.flab.ui.components.FLabCard
import com.lquiroz.flab.ui.components.KeyValueRow
import com.lquiroz.flab.ui.components.SectionLabel
import com.lquiroz.flab.ui.theme.FLabColors

/**
 * F/LAB Diagnostics (DoD 37) and the debug report (DoD 38).
 *
 * The purpose is stated in the DoD itself: make it possible to work out what a build is doing
 * without attaching a debugger. So everything the Core knows is on this screen, the report is
 * generated on demand rather than collected in the background, and it is shown in full before it
 * is shared — you can read exactly what you would be sending.
 */
@Composable
fun DiagnosticsScreen(
    snapshot: DiagnosticsSnapshot,
    report: () -> String,
    onShare: (String) -> Unit,
    onClearModuleFailure: (ModuleId) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var generatedReport by remember { mutableStateOf<String?>(null) }
    var confirmingReset by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            title = "F/LAB Diagnostics",
            subtitle = "Everything the engine currently believes. Nothing here is collected in " +
                "the background or sent anywhere on its own.",
        )
        Spacer(Modifier.height(20.dp))

        FLabCard(Modifier.fillMaxWidth()) {
            SectionLabel("Device")
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Model", snapshot.device.displayName)
            KeyValueRow("Android", "${snapshot.device.androidRelease} (API ${snapshot.device.sdkInt})")
            KeyValueRow("One UI", snapshot.device.oneUiVersion ?: "Not reported")
            KeyValueRow("Foldable", snapshot.device.isFoldable.yesNo())
            KeyValueRow("Hinge sensor", snapshot.device.hasHingeSensor.yesNo())
            KeyValueRow("F/LAB", snapshot.device.appVersionName)
        }

        Spacer(Modifier.height(12.dp))

        FLabCard(Modifier.fillMaxWidth()) {
            SectionLabel("Fold state")
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Posture", snapshot.state.fold.posture.label)
            KeyValueRow("Progress", "%.2f".format(snapshot.state.fold.progress))
            KeyValueRow(
                "Hinge angle",
                snapshot.state.fold.hingeAngleDegrees?.let { "%.1f°".format(it) } ?: "Not reported",
            )
            KeyValueRow("Evidence source", snapshot.state.fold.evidenceSource.label)
            KeyValueRow("Transitioning", snapshot.state.fold.isTransitioning.yesNo())
            KeyValueRow("Active display", snapshot.state.window.display.label)
            KeyValueRow(
                "Window",
                "${snapshot.state.window.size.widthDp} x ${snapshot.state.window.size.heightDp} dp",
            )
            KeyValueRow("Presentation", snapshot.state.window.presentation.label)
        }

        Spacer(Modifier.height(12.dp))

        FLabCard(Modifier.fillMaxWidth()) {
            SectionLabel("Modules")
            Spacer(Modifier.height(10.dp))
            ModuleId.entries.forEach { module ->
                val runtime = snapshot.state.moduleStates[module]
                KeyValueRow(
                    label = module.displayName,
                    value = when {
                        snapshot.state.isModuleRunning(module) -> "Running"
                        else -> runtime?.unavailableReason ?: "Not running"
                    },
                )
                runtime?.lastErrorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = FLabColors.danger,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (runtime.disabledByBreaker) {
                        Spacer(Modifier.height(8.dp))
                        FLabButton(
                            text = "Re-enable ${module.displayName}",
                            onClick = { onClearModuleFailure(module) },
                            prominent = false,
                            accent = FLabColors.warning,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        snapshot.systemEffects?.let { effects ->
            FLabCard(Modifier.fillMaxWidth()) {
                SectionLabel("System effects")
                Spacer(Modifier.height(10.dp))
                KeyValueRow("Enabled", effects.enabled.yesNo())
                KeyValueRow("Service", if (effects.serviceRunning) "Running" else "Stopped")
                KeyValueRow(
                    "Display over apps",
                    if (effects.hasOverlayPermission) "Granted" else "Not granted",
                )
                KeyValueRow(
                    "App awareness",
                    if (effects.accessibilityEnabled) "Granted" else "Not granted",
                )
                KeyValueRow(
                    "Blur behind",
                    if (effects.blurSupported) "Supported" else "Unavailable — using dim",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Last decision: ${effects.verdictExplanation}",
                    style = MaterialTheme.typography.bodySmall,
                    color = FLabColors.textSecondary,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        FLabCard(Modifier.fillMaxWidth()) {
            SectionLabel("Permissions")
            Spacer(Modifier.height(10.dp))
            snapshot.access.forEach {
                KeyValueRow(
                    label = it.title + if (it.experimental) " (experimental)" else "",
                    value = if (it.granted) "Granted" else "Not granted",
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        FLabCard(Modifier.fillMaxWidth()) {
            SectionLabel("Debug report")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Generated only when you ask. It contains no account identifiers, no " +
                    "screen contents, no list of your installed apps, and no wall-clock times.",
                style = MaterialTheme.typography.bodySmall,
                color = FLabColors.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FLabButton(
                    text = if (generatedReport == null) "Generate" else "Regenerate",
                    onClick = { generatedReport = report() },
                    prominent = false,
                )
                generatedReport?.let { text ->
                    FLabButton(text = "Share", onClick = { onShare(text) })
                }
            }
            generatedReport?.let { text ->
                Spacer(Modifier.height(14.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = FLabColors.textSecondary,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        FLabCard(Modifier.fillMaxWidth()) {
            SectionLabel("Reset")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Clears F/LAB's own configuration and returns it to first-run defaults. " +
                    "Nothing outside F/LAB is touched.",
                style = MaterialTheme.typography.bodySmall,
                color = FLabColors.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
            if (confirmingReset) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FLabButton(
                        text = "Reset F/LAB",
                        onClick = {
                            confirmingReset = false
                            onReset()
                        },
                        accent = FLabColors.danger,
                    )
                    FLabButton(
                        text = "Cancel",
                        onClick = { confirmingReset = false },
                        prominent = false,
                    )
                }
            } else {
                FLabButton(
                    text = "Reset F/LAB",
                    onClick = { confirmingReset = true },
                    prominent = false,
                    accent = FLabColors.danger,
                )
            }
        }
    }
}

private fun Boolean.yesNo() = if (this) "Yes" else "No"
