package com.lquiroz.flab.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lquiroz.flab.core.ModuleId
import com.lquiroz.flab.core.PowerPosture
import com.lquiroz.flab.core.SetupProgress
import com.lquiroz.flab.core.SetupStep
import com.lquiroz.flab.core.SetupStepId
import com.lquiroz.flab.profiles.TreatmentMode
import com.lquiroz.flab.ui.FLabScreen
import com.lquiroz.flab.ui.FLabUiState
import com.lquiroz.flab.ui.components.Dot
import com.lquiroz.flab.ui.components.FLabButton
import com.lquiroz.flab.ui.components.FLabCard
import com.lquiroz.flab.ui.components.FLabGlassCard
import com.lquiroz.flab.ui.components.Pill
import com.lquiroz.flab.ui.components.SectionLabel
import com.lquiroz.flab.ui.components.StatusRow
import com.lquiroz.flab.ui.theme.FLabColors

/**
 * The control centre (DoD 10).
 *
 * Laid out to match the screen sketched in the Definition of Done — device, the four modules,
 * apps, performance, experiments — and to read as a product rather than a developer panel
 * (DoD 40): each row shows a state, not a switch, and the detail lives one tap deeper.
 *
 * The health line at the top is DoD 43: it says F/LAB Active when everything is running, Action
 * required when a permission is missing or the breaker has tripped, and always explains itself
 * rather than leaving a silent amber dot.
 */
@Composable
fun HomeScreen(
    ui: FLabUiState,
    wide: Boolean,
    onNavigate: (FLabScreen) -> Unit,
    onToggleEngine: (Boolean) -> Unit,
    onBeginSetup: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val setupSteps = remember(
        ui.configuration.enabled,
        ui.systemEffects.hasOverlayPermission,
        ui.systemEffects.accessibilityEnabled,
    ) {
        SetupProgress.steps(
            engineEnabled = ui.configuration.enabled,
            hasOverlayPermission = ui.systemEffects.hasOverlayPermission,
            hasAccessibilityPermission = ui.systemEffects.accessibilityEnabled,
        )
    }
    val setupComplete = SetupProgress.isComplete(setupSteps)

    Column(modifier = modifier.fillMaxWidth()) {
        HomeHeader(ui, onToggleEngine)
        Spacer(Modifier.height(24.dp))

        // Shown until the three steps are done, then gone for good — the point of a checklist is
        // that it stops asking once there is nothing left to ask (DoD 18).
        AnimatedVisibility(
            visible = !setupComplete,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                SetupCard(
                    steps = setupSteps,
                    onBeginSetup = onBeginSetup,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        AnimatedVisibility(
            visible = ui.needsAttention,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                AttentionCard(ui, onNavigate)
                Spacer(Modifier.height(16.dp))
            }
        }

        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DeviceRow(ui)
                    ModuleRows(ui, onNavigate)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SummaryRows(ui, onNavigate)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DeviceRow(ui)
                ModuleRows(ui, onNavigate)
                SummaryRows(ui, onNavigate)
            }
        }
    }
}

@Composable
private fun HomeHeader(ui: FLabUiState, onToggleEngine: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text("F/LAB", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(healthColor(ui))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ui.healthLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = healthColor(ui),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Pill(
            text = if (ui.configuration.enabled) "On" else "Off",
            accent = if (ui.configuration.enabled) MaterialTheme.colorScheme.primary else FLabColors.textSecondary,
            filled = ui.configuration.enabled,
            onClick = { onToggleEngine(!ui.configuration.enabled) },
        )
    }
}

/**
 * The guided activation checklist (DoD 18, 43): the single visible path from "just installed" to
 * "genuinely running", instead of the three separate discoveries — the Home pill, F/LAB Access,
 * the System effects toggle — that used to be scattered across screens with nothing tying them
 * together into one obvious next action.
 *
 * The one place [FLabGlassCard] is used outside the wallpaper: this is the single most important
 * thing on the screen while it is showing, which is exactly the case DoD 40's "few visible
 * settings at once" reserves it for.
 */
@Composable
private fun SetupCard(
    steps: List<SetupStep>,
    onBeginSetup: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val remaining = steps.count { !it.done }
    FLabGlassCard(Modifier.fillMaxWidth()) {
        SectionLabel("Get F/LAB fully working")
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (remaining == 1) "One step left" else "$remaining steps left",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Fold Motion, Continuity and the system-wide effect all come from these same " +
                "switches. Once they are done you will not need to come back here for them.",
            style = MaterialTheme.typography.bodySmall,
            color = FLabColors.textSecondary,
        )
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            steps.forEach { SetupStepRow(it) }
        }
        Spacer(Modifier.height(18.dp))
        when (SetupProgress.nextStep(steps)?.id) {
            SetupStepId.EngineOn -> FLabButton(
                text = "Turn F/LAB on",
                onClick = onBeginSetup,
                modifier = Modifier.fillMaxWidth(),
            )
            SetupStepId.OverlayPermission -> FLabButton(
                text = "Allow display over other apps",
                onClick = onOpenOverlaySettings,
                modifier = Modifier.fillMaxWidth(),
            )
            SetupStepId.AccessibilityPermission -> FLabButton(
                text = "Allow app awareness",
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth(),
            )
            null -> Unit
        }
    }
}

@Composable
private fun SetupStepRow(step: SetupStep) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(if (step.done) FLabColors.ok else FLabColors.textSecondary, size = if (step.done) 10 else 8)
        Spacer(Modifier.width(10.dp))
        Text(
            text = step.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (step.done) MaterialTheme.colorScheme.onSurface else FLabColors.textSecondary,
            fontWeight = if (step.done) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun healthColor(ui: FLabUiState): Color = when {
    !ui.configuration.enabled -> FLabColors.textSecondary
    ui.needsAttention -> FLabColors.warning
    else -> FLabColors.ok
}

/** DoD 43: a module that is off must explain itself, not just go grey. */
@Composable
private fun AttentionCard(ui: FLabUiState, onNavigate: (FLabScreen) -> Unit) {
    val tripped = ui.state.moduleStates.entries.filter { it.value.disabledByBreaker }
    FLabCard(Modifier.fillMaxWidth()) {
        SectionLabel("Action required")
        Spacer(Modifier.height(10.dp))
        tripped.forEach { (module, runtime) ->
            Text(
                text = "${module.displayName} switched itself off after repeated failures.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            runtime.lastErrorMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = FLabColors.textSecondary)
            }
            Spacer(Modifier.height(12.dp))
        }
        FLabButton(
            text = "Open Diagnostics",
            onClick = { onNavigate(FLabScreen.Diagnostics) },
            prominent = false,
            accent = FLabColors.warning,
        )
    }
}

@Composable
private fun DeviceRow(ui: FLabUiState) {
    val device = ui.device
    StatusRow(
        title = "Device",
        value = device?.displayName ?: "Detecting",
        accent = MaterialTheme.colorScheme.onSurface,
        detail = buildString {
            append(
                when {
                    device == null -> "Reading device information"
                    device.isFoldable && device.hasHingeSensor -> "Foldable, continuous hinge angle"
                    device.isFoldable -> "Foldable, posture events only"
                    else -> "No hinge reported — Fold Motion has nothing to follow"
                },
            )
        },
    )
}

@Composable
private fun ModuleRows(ui: FLabUiState, onNavigate: (FLabScreen) -> Unit) {
    ModuleId.entries.forEach { module ->
        val runtime = ui.state.moduleStates[module]
        val running = ui.state.isModuleRunning(module)
        StatusRow(
            title = module.displayName,
            value = if (running) "ON" else "OFF",
            accent = if (running) MaterialTheme.colorScheme.primary else FLabColors.textSecondary,
            detail = when {
                running && module == ModuleId.FoldMotion -> "Following the hinge"
                running -> null
                !ui.configuration.enabled -> "F/LAB is off"
                ui.state.power != PowerPosture.Normal -> ui.state.power.label
                else -> runtime?.unavailableReason
            },
            onClick = when (module) {
                ModuleId.FoldMotion -> {
                    { onNavigate(FLabScreen.FoldMotion) }
                }
                ModuleId.AppProfiles, ModuleId.Immersive -> {
                    { onNavigate(FLabScreen.Apps) }
                }
                else -> null
            },
        )
    }
}

/**
 * The system-wide effect (DoD 43).
 *
 * The row never shows a bare "On" when the effect cannot actually run: if a grant is missing it
 * names the missing one and routes to F/LAB Access, because a switch that reads On while nothing
 * happens is the worst thing this screen could say.
 */
@Composable
private fun SystemEffectsRow(ui: FLabUiState, onNavigate: (FLabScreen) -> Unit) {
    val effects = ui.systemEffects
    val value = when {
        !effects.enabled -> "OFF"
        !effects.canRun -> "Action required"
        effects.serviceRunning -> "ON"
        else -> "Starting"
    }
    val accent = when {
        !effects.enabled -> FLabColors.textSecondary
        !effects.canRun -> FLabColors.warning
        else -> MaterialTheme.colorScheme.primary
    }
    StatusRow(
        title = "System effects",
        value = value,
        accent = accent,
        detail = when {
            !effects.enabled -> "Apply fold motion outside F/LAB, across the system"
            !effects.canRun -> "Needs: ${effects.missing.joinToString(" and ")}"
            else -> effects.verdict.explanation
        },
        onClick = { onNavigate(FLabScreen.Access) },
    )
}

@Composable
private fun SummaryRows(ui: FLabUiState, onNavigate: (FLabScreen) -> Unit) {
    SystemEffectsRow(ui, onNavigate)
    StatusRow(
        title = "Apps",
        value = "${ui.configuredAppCount} configured",
        accent = MaterialTheme.colorScheme.secondary,
        detail = ui.appProfiles
            .firstOrNull { it.immersive == TreatmentMode.On }
            ?.let { "${it.displayName} has the strongest treatment" },
        onClick = { onNavigate(FLabScreen.Apps) },
    )
    StatusRow(
        title = "Performance",
        value = ui.profile.id.displayName,
        accent = when (ui.state.power) {
            PowerPosture.Normal -> FLabColors.ok
            PowerPosture.Conserving -> FLabColors.warning
            PowerPosture.Restricted -> FLabColors.danger
        },
        detail = when (ui.state.power) {
            PowerPosture.Normal -> ui.profile.id.summary
            PowerPosture.Conserving -> "The device asked F/LAB to conserve power"
            PowerPosture.Restricted -> "Paused while the device cools down"
        },
        onClick = { onNavigate(FLabScreen.Profiles) },
    )
    StatusRow(
        title = "Experiments",
        value = if (ui.configuration.experimentsEnabled) "Enabled" else "Off",
        accent = if (ui.configuration.experimentsEnabled) FLabColors.warning else FLabColors.textSecondary,
        detail = "Features that are not reliable yet",
        onClick = { onNavigate(FLabScreen.Experiments) },
    )
    StatusRow(
        title = "Diagnostics",
        value = "Open",
        accent = FLabColors.textSecondary,
        detail = "Device, modules, permissions, last error",
        onClick = { onNavigate(FLabScreen.Diagnostics) },
    )
    Spacer(Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FLabButton(
            text = "F/LAB Access",
            onClick = { onNavigate(FLabScreen.Access) },
            prominent = false,
            modifier = Modifier.weight(1f),
        )
    }
}
