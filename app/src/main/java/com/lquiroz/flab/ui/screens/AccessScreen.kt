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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lquiroz.flab.diagnostics.AccessRequirement
import com.lquiroz.flab.system.SystemAccess
import com.lquiroz.flab.ui.SystemEffectsState
import com.lquiroz.flab.ui.components.FLabButton
import com.lquiroz.flab.ui.components.FLabCard
import com.lquiroz.flab.ui.components.Pill
import com.lquiroz.flab.ui.components.SectionLabel
import com.lquiroz.flab.ui.theme.FLabColors

/**
 * F/LAB Access (DoD 17), and the place the system-wide effect is switched on.
 *
 * The rule the DoD sets is that F/LAB may never say "turn this on because". So the two grants that
 * actually reach outside F/LAB get the most space on the screen, each one stating what is read,
 * what is not read, and what stops working without it — before the button, not after it.
 */
@Composable
fun AccessScreen(
    requirements: List<AccessRequirement>,
    systemEffects: SystemEffectsState,
    onToggleSystemEffects: (Boolean) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (experimental, standard) = requirements.partition { it.experimental }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            title = "F/LAB Access",
            subtitle = "F/LAB asks for as little as it can. Everything below says what it is for " +
                "and what you lose by saying no.",
        )
        Spacer(Modifier.height(20.dp))

        SystemEffectsCard(
            systemEffects = systemEffects,
            onToggle = onToggleSystemEffects,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onOpenAccessibilitySettings = onOpenAccessibilitySettings,
            onOpenAppDetails = onOpenAppDetails,
        )

        Spacer(Modifier.height(20.dp))

        FLabCard(Modifier.fillMaxWidth()) {
            SectionLabel("What F/LAB still does not do")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "No root. No device administrator. It cannot read your screen, cannot see " +
                    "what you type, and cannot list your installed apps. The layer it draws never " +
                    "takes a touch — every tap, swipe and gesture passes through to the app " +
                    "underneath. Fold Motion, Continuity, Live Preview and Profiles all work " +
                    "inside F/LAB with none of the grants above.",
                style = MaterialTheme.typography.bodyMedium,
                color = FLabColors.textSecondary,
            )
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Used by F/LAB")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            standard.forEach { RequirementCard(it) }
        }

        if (experimental.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Only for experiments")
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                experimental.forEach { RequirementCard(it) }
            }
        }
    }
}

@Composable
private fun SystemEffectsCard(
    systemEffects: SystemEffectsState,
    onToggle: (Boolean) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppDetails: () -> Unit,
) {
    var showBlockedHelp by remember { mutableStateOf(false) }

    FLabCard(Modifier.fillMaxWidth(), contentPadding = 24) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("System effects", style = MaterialTheme.typography.headlineSmall)
            Pill(
                text = if (systemEffects.enabled) "On" else "Off",
                accent = if (systemEffects.enabled && systemEffects.canRun) {
                    MaterialTheme.colorScheme.primary
                } else if (systemEffects.enabled) {
                    FLabColors.warning
                } else {
                    FLabColors.textSecondary
                },
                filled = systemEffects.enabled && systemEffects.canRun,
                onClick = { onToggle(!systemEffects.enabled) },
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Applies the fold treatment across the whole system rather than only inside " +
                "F/LAB. While you open or close the device, what is on screen softens and settles " +
                "with the hinge. It needs two things, and it will not start without both.",
            style = MaterialTheme.typography.bodyMedium,
            color = FLabColors.textSecondary,
        )

        Spacer(Modifier.height(18.dp))

        GrantRow(
            title = "Display over other apps",
            granted = systemEffects.hasOverlayPermission,
            why = "Lets F/LAB draw one transparent layer above whatever is on screen, and ask the " +
                "system to blur and dim what is behind it.",
            whatItIsNot = "The layer is click-through and paints nothing of its own. It cannot " +
                "move, restyle or read the app underneath.",
            onGrant = onOpenOverlaySettings,
        )

        Spacer(Modifier.height(14.dp))

        GrantRow(
            title = "App awareness",
            granted = systemEffects.accessibilityEnabled,
            why = "Reads the package name of the app in front — that string and nothing else — so " +
                "the layer can refuse to appear over your bank, an authenticator, a payment sheet " +
                "or the camera.",
            whatItIsNot = "It does not read screen contents, text, or anything you type. Android " +
                "is not even asked for that: the service declares no window-content access, so " +
                "the system never hands it any.",
            onGrant = onOpenAccessibilitySettings,
        )

        if (!systemEffects.accessibilityEnabled) {
            Spacer(Modifier.height(16.dp))
            FLabButton(
                text = if (showBlockedHelp) "Hide" else "Android blocked the switch?",
                onClick = { showBlockedHelp = !showBlockedHelp },
                prominent = false,
                accent = FLabColors.warning,
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(
                visible = showBlockedHelp,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                RestrictedSettingsHelp(onOpenAppDetails)
            }
        }

        if (systemEffects.enabled && systemEffects.canRun) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Running. A notification stays in your shade while this is on, with a " +
                    "one-tap stop — something that can draw over other apps should never be " +
                    "running invisibly.",
                style = MaterialTheme.typography.bodySmall,
                color = FLabColors.textSecondary,
            )
        }
    }
}

/**
 * The Restricted Settings escape hatch (Android 13+).
 *
 * F/LAB cannot lift this on itself — there is no API, by design, because the block exists to stop
 * malware doing exactly that. All the app can honestly do is explain the route, in the order that
 * works, and open the one screen it is allowed to open.
 */
@Composable
private fun RestrictedSettingsHelp(onOpenAppDetails: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Android blocks accessibility for apps installed outside an app store. F/LAB " +
                "cannot unlock that itself — no app can, and that is deliberate.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        SystemAccess.restrictedSettingsSteps.forEach { step ->
            Text(
                text = step.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = step.body,
                style = MaterialTheme.typography.bodySmall,
                color = FLabColors.textSecondary,
            )
            Spacer(Modifier.height(14.dp))
        }
        FLabButton(
            text = "Open F/LAB app info",
            onClick = onOpenAppDetails,
            prominent = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GrantRow(
    title: String,
    granted: Boolean,
    why: String,
    whatItIsNot: String,
    onGrant: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Pill(
                text = if (granted) "Granted" else "Not granted",
                accent = if (granted) FLabColors.ok else FLabColors.textSecondary,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(why, style = MaterialTheme.typography.bodySmall, color = FLabColors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Text(
            text = whatItIsNot,
            style = MaterialTheme.typography.bodySmall,
            color = FLabColors.textSecondary,
        )
        if (!granted) {
            Spacer(Modifier.height(10.dp))
            FLabButton(
                text = "Grant",
                onClick = onGrant,
                prominent = false,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RequirementCard(requirement: AccessRequirement) {
    FLabCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = requirement.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Pill(
                text = if (requirement.granted) "Granted" else "Not granted",
                accent = if (requirement.granted) FLabColors.ok else FLabColors.textSecondary,
            )
        }
        Spacer(Modifier.height(12.dp))
        LabelledParagraph("What it is used for", requirement.why)
        Spacer(Modifier.height(10.dp))
        LabelledParagraph("Without it", requirement.whatBreaks)
    }
}

@Composable
private fun LabelledParagraph(label: String, body: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = FLabColors.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
