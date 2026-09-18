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
import com.lquiroz.flab.core.Experiments
import com.lquiroz.flab.ui.components.FLabCard
import com.lquiroz.flab.ui.components.Pill
import com.lquiroz.flab.ui.components.SectionLabel
import com.lquiroz.flab.ui.theme.FLabColors

/**
 * F/LAB Experiments (DoD 15).
 *
 * Everything here is off until the user opens this screen and turns it on, and each entry states
 * its risk above the switch rather than in a footnote. DoD 16's rule is visible in the copy: none
 * of F/LAB's stable modules appear on this screen, because none of them needs anything from it.
 */
@Composable
fun ExperimentsScreen(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            title = "F/LAB Experiments",
            subtitle = "Features that are not reliable enough to be on by default. Some need an " +
                "accessibility service; some depend on a specific One UI version.",
        )
        Spacer(Modifier.height(20.dp))

        FLabCard(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Experiments", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Fold Motion, Continuity, Immersive and App Profiles all work " +
                            "without anything on this screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = FLabColors.textSecondary,
                    )
                }
                Pill(
                    text = if (enabled) "On" else "Off",
                    accent = if (enabled) FLabColors.warning else FLabColors.textSecondary,
                    filled = enabled,
                    onClick = { onToggle(!enabled) },
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Available experiments")
        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Experiments.all.forEach { experiment ->
                FLabCard(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = experiment.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Pill(experiment.risk.label, FLabColors.warning)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = experiment.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = FLabColors.textSecondary,
                    )
                    experiment.accessRationale?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = FLabColors.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (enabled) {
                            "Not yet implemented in this build."
                        } else {
                            "Turn experiments on to use this."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = FLabColors.textSecondary,
                    )
                }
            }
        }
    }
}
