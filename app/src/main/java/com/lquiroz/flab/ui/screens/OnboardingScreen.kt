package com.lquiroz.flab.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lquiroz.flab.ui.components.FLabButton
import com.lquiroz.flab.ui.components.FLabCard
import com.lquiroz.flab.ui.components.SectionLabel
import com.lquiroz.flab.ui.theme.FLabColors
import com.lquiroz.flab.ui.theme.FLabTokens

private data class OnboardingStep(val label: String, val title: String, val body: String)

/**
 * First run (DoD 42).
 *
 * Five screens, in the order the DoD lists: what F/LAB does, what it can change, what Android will
 * not let it change, what it asks for, and how to switch it off. The fourth and fifth exist
 * because a user who does not know how to turn something off has not really consented to it.
 */
private val steps = listOf(
    OnboardingStep(
        label = "What it is",
        title = "A layer, not a replacement",
        body = "F/LAB makes a foldable feel more coherent when it opens, closes and changes " +
            "screen. One UI stays exactly as it is. F/LAB does not replace your launcher, and it " +
            "does not need root.",
    ),
    OnboardingStep(
        label = "What it changes",
        title = "Motion, continuity and system bars",
        body = "Opening motion follows the hinge instead of replaying a fixed animation. " +
            "Switching between the cover and inner screens is bridged rather than cut. System " +
            "bars are blended into content where that helps and left alone where it does not.",
    ),
    OnboardingStep(
        label = "What it cannot",
        title = "Android draws the line, not F/LAB",
        body = "A normal app cannot change another app's animations, restyle another app's " +
            "status bar from the inside, or alter One UI's own transitions. F/LAB works within " +
            "what Android allows, and says so on every screen where the limit matters.",
    ),
    OnboardingStep(
        label = "What it asks for",
        title = "As little as possible",
        body = "The four modules need nothing beyond the normal app sandbox. Anything that " +
            "would need more — an accessibility service, for instance — lives in F/LAB " +
            "Experiments, is off by default, and explains itself before you turn it on.",
    ),
    OnboardingStep(
        label = "How to stop it",
        title = "One switch, any time",
        body = "The pill on the Home screen turns F/LAB off instantly and everything returns to " +
            "stock behaviour. Reset F/LAB, in Diagnostics, clears its settings. Uninstalling " +
            "leaves nothing behind.",
    ),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index]

    Column(modifier = modifier.fillMaxWidth()) {
        Text("F/LAB", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(28.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = steps.indexOf(targetState) >= steps.indexOf(initialState)
                val offset = if (forward) 1 else -1
                (
                    slideInHorizontally(spring(stiffness = 420f)) { it / 6 * offset } +
                        fadeIn(spring(stiffness = 420f))
                    ) togetherWith (
                    slideOutHorizontally(spring(stiffness = 420f)) { -it / 6 * offset } +
                        fadeOut(spring(stiffness = 420f))
                    ) using SizeTransform(clip = false)
            },
            label = "onboardingStep",
        ) { current ->
            FLabCard(Modifier.fillMaxWidth(), contentPadding = 24) {
                SectionLabel(current.label)
                Spacer(Modifier.height(14.dp))
                Text(current.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = current.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FLabColors.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.indices.forEach { position ->
                    Box(
                        modifier = Modifier
                            .size(width = if (position == index) 22.dp else 7.dp, height = 7.dp)
                            .clip(RoundedCornerShape(FLabTokens.RadiusPill))
                            .background(
                                if (position == index) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    FLabColors.outline
                                },
                            ),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (index > 0) {
                    FLabButton(text = "Back", onClick = { index-- }, prominent = false)
                }
                FLabButton(
                    text = if (index == steps.lastIndex) "Start" else "Next",
                    onClick = { if (index == steps.lastIndex) onFinish() else index++ },
                )
            }
        }
    }
}
