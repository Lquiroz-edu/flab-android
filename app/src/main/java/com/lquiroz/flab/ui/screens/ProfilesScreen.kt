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
import com.lquiroz.flab.profiles.FLabProfile
import com.lquiroz.flab.profiles.ProfileId
import com.lquiroz.flab.ui.components.Dot
import com.lquiroz.flab.ui.components.FLabCard
import com.lquiroz.flab.ui.components.KeyValueRow
import com.lquiroz.flab.ui.theme.FLabColors
import kotlin.math.roundToInt

/** The global profiles from DoD 12. */
@Composable
fun ProfilesScreen(
    active: ProfileId,
    onSelect: (ProfileId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            title = "Profiles",
            subtitle = "One choice that sets how much F/LAB does. Battery keeps the engine " +
                "tracking state but stops it painting anything.",
        )
        Spacer(Modifier.height(20.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileId.entries.filter { it != ProfileId.Custom }.forEach { id ->
                ProfileCard(id, id == active) { onSelect(id) }
            }
        }
    }
}

@Composable
private fun ProfileCard(id: ProfileId, selected: Boolean, onSelect: () -> Unit) {
    val profile = FLabProfile.of(id)
    FLabCard(Modifier.fillMaxWidth(), onClick = onSelect) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(id.displayName, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = id.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = FLabColors.textSecondary,
                )
            }
            Dot(
                color = if (selected) MaterialTheme.colorScheme.primary else FLabColors.outline,
                size = if (selected) 12 else 10,
            )
        }

        if (selected) {
            Spacer(Modifier.height(14.dp))
            KeyValueRow("Motion", if (profile.motion.isInert) "Off" else "On")
            KeyValueRow("Blur", profile.motion.blurIntensity.percent())
            KeyValueRow("Dimming", profile.motion.dimIntensity.percent())
            KeyValueRow("Continuity", if (profile.continuityEnabled) "On" else "Off")
            KeyValueRow("Transition budget", "${profile.continuityBudgetMillis} ms")
        }
    }
}

private fun Float.percent(): String = "${(this * 100).roundToInt()}%"
