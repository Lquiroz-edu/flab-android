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
import com.lquiroz.flab.diagnostics.AccessRequirement
import com.lquiroz.flab.ui.components.FLabCard
import com.lquiroz.flab.ui.components.Pill
import com.lquiroz.flab.ui.components.SectionLabel
import com.lquiroz.flab.ui.theme.FLabColors

/**
 * F/LAB Access (DoD 17).
 *
 * Minimum privilege, explained. Each entry answers the same three questions in the same order —
 * what it is, what F/LAB does with it, and what stops working without it — because the rule the
 * DoD sets is that F/LAB may never say "turn this on because".
 */
@Composable
fun AccessScreen(
    requirements: List<AccessRequirement>,
    modifier: Modifier = Modifier,
) {
    val (experimental, standard) = requirements.partition { it.experimental }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            title = "F/LAB Access",
            subtitle = "F/LAB asks for as little as it can. Everything below says what it is " +
                "for and what you lose by saying no.",
        )
        Spacer(Modifier.height(20.dp))

        FLabCard(Modifier.fillMaxWidth()) {
            SectionLabel("What F/LAB does not need")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "No root. No device administrator. No accessibility service to open the " +
                    "app or to run Fold Motion, Continuity, Immersive or App Profiles. F/LAB " +
                    "cannot read your screen, and it does not draw over other apps.",
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
            Spacer(Modifier.height(6.dp))
            Text(
                text = "These are needed only by features in F/LAB Experiments. Nothing in the " +
                    "stable app asks for them.",
                style = MaterialTheme.typography.bodySmall,
                color = FLabColors.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                experimental.forEach { RequirementCard(it) }
            }
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
