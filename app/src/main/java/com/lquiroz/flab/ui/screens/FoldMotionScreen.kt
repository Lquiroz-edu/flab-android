package com.lquiroz.flab.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lquiroz.flab.motion.EvidenceSource
import com.lquiroz.flab.motion.FoldMotionFrame
import com.lquiroz.flab.motion.MotionChannelMapper
import com.lquiroz.flab.profiles.FLabProfile
import com.lquiroz.flab.ui.components.FLabCard
import com.lquiroz.flab.ui.components.KeyValueRow
import com.lquiroz.flab.ui.components.SectionLabel
import com.lquiroz.flab.ui.motion.MotionLayer
import com.lquiroz.flab.ui.theme.FLabColors
import com.lquiroz.flab.ui.theme.FLabTokens
import kotlin.math.roundToInt

/**
 * Live Preview for Fold Motion (DoD 11).
 *
 * The scrubber is the point. Dragging it feeds the same [MotionChannelMapper] the real engine
 * uses, at the same tuning, so what you see here is what the device will do — and because you
 * control the progress by hand, you can stop halfway and confirm that the treatment stops with
 * you, which is the property DoD 3 is really asking for.
 *
 * Preview state is local. Nothing here writes to the running configuration until the profile is
 * changed on the Profiles screen, so experimenting is free.
 */
@Composable
fun FoldMotionScreen(
    profile: FLabProfile,
    progress: Float,
    onScrub: (Float) -> Unit,
    hasHingeSensor: Boolean,
    modifier: Modifier = Modifier,
) {
    // Energy is synthesised from the drag so the veil channels are visible while scrubbing. On the
    // device this comes from real velocity; here the finger is the velocity.
    val frame = remember(progress) {
        FoldMotionFrame(
            progress = progress,
            velocity = 0f,
            energy = 0f,
            settled = true,
            source = EvidenceSource.Manual,
        )
    }
    val channels = remember(frame, profile.motion) {
        MotionChannelMapper.map(frame, profile.motion)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ScreenHeader(
            title = "Fold Motion",
            subtitle = "Drag to move the device through its opening. " +
                "The treatment follows your finger the way it follows the hinge.",
        )
        Spacer(Modifier.height(20.dp))

        FLabCard(Modifier.fillMaxWidth()) {
            SectionLabel("Preview")
            Spacer(Modifier.height(16.dp))
            DevicePreview(progress, channels.contentScale, channels.expansion)
            Spacer(Modifier.height(20.dp))
            Scrubber(progress, onScrub)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Closed", style = MaterialTheme.typography.bodySmall, color = FLabColors.textSecondary)
                Text(
                    "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("Open", style = MaterialTheme.typography.bodySmall, color = FLabColors.textSecondary)
            }
        }

        Spacer(Modifier.height(14.dp))

        FLabCard(Modifier.fillMaxWidth()) {
            SectionLabel("Channels at this position")
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Scale", channels.contentScale.percent())
            KeyValueRow("Expansion", channels.expansion.percent())
            KeyValueRow("Offset", "${channels.translationYDp.roundToInt()} dp")
            KeyValueRow("Blur", "${channels.blurRadiusDp.roundToInt()} dp")
            KeyValueRow("Dim", channels.dimAlpha.percent())
            KeyValueRow("Depth", "${channels.elevationDp.roundToInt()} dp")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Blur, dim and depth follow movement rather than position, so they read " +
                    "as zero here and appear only while the device is actually moving.",
                style = MaterialTheme.typography.bodySmall,
                color = FLabColors.textSecondary,
            )
        }

        Spacer(Modifier.height(14.dp))

        FLabCard(Modifier.fillMaxWidth()) {
            SectionLabel("Input")
            Spacer(Modifier.height(10.dp))
            KeyValueRow("Profile", profile.id.displayName)
            KeyValueRow("Spring stiffness", profile.motion.stiffness.roundToInt().toString())
            KeyValueRow("Damping", profile.motion.damping.roundToInt().toString())
            KeyValueRow("Hinge angle sensor", if (hasHingeSensor) "Available" else "Not available")
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hasHingeSensor) {
                    "This device reports a continuous hinge angle, so motion is driven directly " +
                        "by how far the device is open."
                } else {
                    "This device reports posture changes only. Motion is interpolated between " +
                        "them and will be coarser, but it still stops when the device stops."
                },
                style = MaterialTheme.typography.bodySmall,
                color = FLabColors.textSecondary,
            )
        }
    }
}

/**
 * An abstract foldable that opens as progress rises.
 *
 * Note what it does not draw: no hinge line, no crease, no centre seam. The two panels share one
 * continuous gradient so the surface reads as a single plane, which is the whole visual argument
 * of the project.
 */
@Composable
private fun DevicePreview(progress: Float, scale: Float, expansion: Float) {
    val ratio = 0.62f + progress * 0.58f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp),
        contentAlignment = Alignment.Center,
    ) {
        MotionLayer(
            channels = MotionChannelMapper.map(
                FoldMotionFrame(progress, 0f, 0f, settled = true, source = EvidenceSource.Manual),
                com.lquiroz.flab.motion.MotionTuning.Balanced,
            ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f + progress * 0.5f)
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(FLabTokens.RadiusCard))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                            ),
                            start = Offset.Zero,
                            end = Offset.Infinite,
                        ),
                    )
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.18f),
                        RoundedCornerShape(FLabTokens.RadiusCard),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (index == 2) 0.55f else expansion)
                                .height(10.dp)
                                .clip(RoundedCornerShape(FLabTokens.RadiusPill))
                                .background(Color.White.copy(alpha = 0.22f)),
                        )
                    }
                }
            }
        }
    }
}

/** A drag track. Deliberately not a Slider: the point is direct manipulation, not a control. */
@Composable
private fun Scrubber(progress: Float, onScrub: (Float) -> Unit) {
    val layoutDirection = LocalLayoutDirection.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(FLabTokens.RadiusPill))
            .background(FLabColors.raised)
            .pointerInput(layoutDirection) {
                val width = size.width.toFloat().coerceAtLeast(1f)
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    val raw = change.position.x / width
                    onScrub(if (layoutDirection == LayoutDirection.Rtl) 1f - raw else raw)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0.02f, 1f))
                .height(44.dp)
                .clip(RoundedCornerShape(FLabTokens.RadiusPill))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.secondary,
                            MaterialTheme.colorScheme.primary,
                        ),
                    ),
                ),
        )
    }
}

private fun Float.percent(): String = "${(this * 100).roundToInt()}%"
