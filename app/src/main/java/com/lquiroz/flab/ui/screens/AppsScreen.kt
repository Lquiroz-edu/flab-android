package com.lquiroz.flab.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lquiroz.flab.profiles.AppProfile
import com.lquiroz.flab.profiles.TreatmentMode
import com.lquiroz.flab.ui.components.FLabCard
import com.lquiroz.flab.ui.components.Pill
import com.lquiroz.flab.ui.components.SectionLabel
import com.lquiroz.flab.ui.theme.FLabColors

/**
 * Per-app control (DoD 13) and the safe-app policy made visible (DoD 14).
 *
 * A protected app is shown rather than hidden, with its switches inert and a line saying why.
 * Hiding it would look like an omission; showing it locked is a statement about what F/LAB will
 * not do.
 */
@Composable
fun AppsScreen(
    profiles: List<AppProfile>,
    onCycleImmersive: (AppProfile) -> Unit,
    onCycleContinuity: (AppProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (locked, configurable) = profiles.partition { it.locked }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            title = "Apps",
            subtitle = "F/LAB treats each app differently. Auto means F/LAB decides per screen " +
                "and stands back where the app already does it well.",
        )
        Spacer(Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            configurable.forEach { profile ->
                AppRow(profile, onCycleImmersive, onCycleContinuity)
            }
        }

        if (locked.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionLabel("Protected")
            Spacer(Modifier.height(6.dp))
            Text(
                text = "F/LAB never places a layer over these. Banking, authenticators, password " +
                    "managers, payments, the camera, permission prompts and system UI are " +
                    "protected whatever the settings say.",
                style = MaterialTheme.typography.bodySmall,
                color = FLabColors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                locked.forEach { profile ->
                    AppRow(profile, onCycleImmersive, onCycleContinuity)
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    profile: AppProfile,
    onCycleImmersive: (AppProfile) -> Unit,
    onCycleContinuity: (AppProfile) -> Unit,
) {
    FLabCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = profile.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = FLabColors.textSecondary,
                )
            }
        }

        profile.note?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = FLabColors.textSecondary)
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TreatmentPill("Immersive", profile.immersive, profile.locked) {
                onCycleImmersive(profile)
            }
            TreatmentPill("Continuity", profile.continuity, profile.locked) {
                onCycleContinuity(profile)
            }
        }
    }
}

@Composable
private fun TreatmentPill(
    label: String,
    mode: TreatmentMode,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val accent = when {
        locked -> FLabColors.textSecondary
        mode == TreatmentMode.On -> MaterialTheme.colorScheme.primary
        mode == TreatmentMode.Auto -> MaterialTheme.colorScheme.secondary
        else -> FLabColors.textSecondary
    }
    Pill(
        text = if (locked) "$label locked" else "$label ${mode.displayName}",
        accent = accent,
        filled = !locked && mode == TreatmentMode.On,
        onClick = if (locked) null else onClick,
    )
}
