package com.lquiroz.flab.ui.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.lquiroz.flab.motion.FoldEvidence
import com.lquiroz.flab.motion.FoldMotionEngine
import com.lquiroz.flab.motion.MotionChannels
import com.lquiroz.flab.motion.MotionTuning
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Runs the Fold Motion frame loop and applies the result to [content].
 *
 * ### Why the loop is shaped like this
 *
 * The loop is not a `while (true) { withFrameNanos { } }`. It is a `while (engine.needsFrames)`,
 * started by new evidence and ending the moment the engine settles. When the device is still, this
 * composable holds no frame callback at all, which is the difference between DoD 22's
 * "mayoritariamente event-driven" and a permanent 120 Hz tick that quietly costs a percent of
 * battery an hour.
 *
 * `withFrameNanos` also means the engine advances on the choreographer's clock, so on a 120 Hz
 * panel it is sampled 120 times a second without anyone having to know the refresh rate (DoD 23),
 * and the first frame after evidence arrives is the very next vsync (DoD 24).
 *
 * ### What is deliberately not drawn
 *
 * No seam, hinge line, crease or centre mask (DoD 3). The dim channel is drawn as a full-bleed
 * scrim whose alpha is capped well below opaque, so a transition can never read as a black flash.
 */
@Composable
fun FoldMotionHost(
    evidence: Flow<FoldEvidence>,
    tuning: MotionTuning,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSettled: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val engine = remember { FoldMotionEngine(tuning) }
    val channelsState = remember { mutableStateOf(MotionChannels.Neutral) }
    var channels by channelsState

    LaunchedEffect(tuning) { engine.updateTuning(tuning) }

    LaunchedEffect(evidence, enabled) {
        if (!enabled) {
            channels = MotionChannels.Neutral
            return@LaunchedEffect
        }
        coroutineScope {
            // Collection and the frame loop are separate coroutines on purpose. Collecting inside
            // the loop would mean no sample could reach the engine until it had already settled,
            // so a real fold — which produces samples continuously while the engine is animating —
            // would be tracked as a series of stale jumps instead of as movement.
            val wake = Channel<Unit>(Channel.CONFLATED)
            launch {
                evidence.collect { observation ->
                    engine.submit(observation)
                    wake.trySend(Unit)
                }
            }
            for (signal in wake) {
                // Drive frames only while there is something to draw, then hand the CPU back.
                while (engine.needsFrames) {
                    withFrameNanos { nanos -> channels = engine.advance(nanos) }
                }
                channels = engine.channels
                onSettled()
            }
        }
    }

    CompositionLocalProvider(LocalMotionChannels provides channelsState) {
        MotionLayer(channels, modifier, content)
    }
}

/**
 * The live channels of the nearest [FoldMotionHost], as a [State] rather than a value on purpose:
 * a layout that reads `.value` inside its placement block is re-placed on every frame of a fold
 * and never recomposed or re-measured for it. F/LAB Home's icon grid is the consumer.
 */
val LocalMotionChannels = staticCompositionLocalOf<State<MotionChannels>> {
    mutableStateOf(MotionChannels.Neutral)
}

/**
 * Applies [MotionChannels] to a subtree.
 *
 * Split out from the loop so Live Preview can drive the same visual treatment from a scrubber
 * instead of from the hinge, and so the two can never drift apart.
 */
@Composable
fun MotionLayer(
    channels: MotionChannels,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (channels.isNeutral) {
        // Fast path: no graphics layer, no blur pass, no scrim. An untreated frame should cost
        // exactly what it costs without F/LAB.
        Box(modifier) { content() }
        return
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                translationY = channels.translationYDp.dp.toPx()
                shadowElevation = channels.elevationDp.dp.toPx()
            }
            .scale(channels.contentScale)
            .alpha(channels.contentAlpha)
            .then(
                if (channels.blurRadiusDp > 0.25f) {
                    Modifier.blur(channels.blurRadiusDp.dp)
                } else {
                    Modifier
                },
            )
            .drawWithContent {
                drawContent()
                if (channels.dimAlpha > 0f) {
                    drawRect(Color.Black.copy(alpha = channels.dimAlpha))
                }
            },
    ) {
        content()
    }
}

/** A full-size [MotionLayer], the common case for a screen root. */
@Composable
fun MotionScreen(channels: MotionChannels, content: @Composable () -> Unit) {
    MotionLayer(channels, Modifier.fillMaxSize(), content)
}
