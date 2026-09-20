package com.lquiroz.flab.motion

import com.lquiroz.flab.motion.MotionChannels.Companion.MAX_BLUR_DP
import com.lquiroz.flab.motion.MotionChannels.Companion.MAX_DIM_ALPHA
import com.lquiroz.flab.motion.MotionChannels.Companion.MAX_WARP
import com.lquiroz.flab.motion.MotionChannels.Companion.MIN_CONTENT_ALPHA
import com.lquiroz.flab.motion.MotionChannels.Companion.MIN_CONTENT_SCALE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The artifact prohibitions from DoD 3 and DoD 30, expressed as assertions. */
class MotionChannelsTest {

    private fun frame(progress: Float, energy: Float) = FoldMotionFrame(
        progress = progress,
        velocity = 0f,
        energy = energy,
        settled = energy == 0f,
        source = EvidenceSource.HingeAngle,
    )

    @Test
    fun `a still device gets no treatment at all`() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            val channels = MotionChannelMapper.map(frame(progress, energy = 0f), MotionTuning.Balanced)
            assertEquals("blur at rest, progress=$progress", 0f, channels.blurRadiusDp, 0f)
            assertEquals("dim at rest, progress=$progress", 0f, channels.dimAlpha, 0f)
            assertEquals("alpha at rest, progress=$progress", 1f, channels.contentAlpha, 0f)
        }
    }

    @Test
    fun `half open held steady is not treated as a transition`() {
        // The regression this guards against: veil driven by position rather than movement, which
        // leaves a device parked at half-open permanently blurred and reads as a stuck overlay.
        val channels = MotionChannelMapper.map(frame(0.5f, energy = 0f), MotionTuning.Smooth)

        assertEquals(0f, channels.blurRadiusDp, 0f)
        assertEquals(0f, channels.dimAlpha, 0f)
        assertEquals(0f, channels.elevationDp, 0f)
    }

    @Test
    fun `channels never exceed their artifact ceilings`() {
        val tunings = listOf(
            MotionTuning.Balanced,
            MotionTuning.Smooth,
            MotionTuning.Minimal,
            MotionTuning.Battery,
        )
        for (tuning in tunings) {
            for (step in 0..20) {
                val progress = step / 20f
                for (energyStep in 0..20) {
                    val channels = MotionChannelMapper.map(
                        frame(progress, energyStep / 20f),
                        tuning,
                    )
                    assertTrue("blur", channels.blurRadiusDp in 0f..MAX_BLUR_DP)
                    assertTrue("dim never opaque", channels.dimAlpha in 0f..MAX_DIM_ALPHA)
                    assertTrue("alpha", channels.contentAlpha in MIN_CONTENT_ALPHA..1f)
                    assertTrue("scale", channels.contentScale in MIN_CONTENT_SCALE..1f)
                    assertTrue("warp", channels.warpAmount in 0f..MAX_WARP)
                    assertTrue("hand-off", channels.handoffAmount in 0f..1f)
                }
            }
        }
    }

    @Test
    fun `the hand-off starts at the first degree, peaks at the panel switch and is gone by half open`() {
        val at = { progress: Float ->
            MotionChannelMapper.map(frame(progress, energy = 0f), MotionTuning.Balanced).handoffAmount
        }
        assertEquals("a closed device at rest must sit still", 0f, at(0f), 0f)
        assertEquals(1f, at(MotionChannels.HANDOFF_PEAK_PROGRESS), 0.001f)
        assertEquals(0f, at(MotionChannels.HANDOFF_END_PROGRESS), 0.001f)
        assertEquals("an open device must never be displaced", 0f, at(1f), 0f)

        var previous = 0f
        for (step in 1..10) {
            val value = at(step / 10f * MotionChannels.HANDOFF_PEAK_PROGRESS)
            assertTrue("hand-off must grow with the opening", value >= previous)
            previous = value
        }
        assertEquals(0f, MotionChannelMapper.map(frame(0.2f, 0f), MotionTuning.Battery).handoffAmount, 0f)
    }

    @Test
    fun `warp is strongest closed and gone once flat`() {
        val closed = MotionChannelMapper.map(frame(0f, energy = 0f), MotionTuning.Balanced)
        val open = MotionChannelMapper.map(frame(1f, energy = 0f), MotionTuning.Balanced)

        assertEquals(MAX_WARP, closed.warpAmount, 0.001f)
        assertEquals(0f, open.warpAmount, 0f)
    }

    @Test
    fun `warp is position-driven, not movement-driven`() {
        // Same regression class as the veil test above: a background held half-open must show a
        // stable pinch, not one that depends on how fast the hand happened to be moving.
        val still = MotionChannelMapper.map(frame(0.5f, energy = 0f), MotionTuning.Balanced)
        val moving = MotionChannelMapper.map(frame(0.5f, energy = 1f), MotionTuning.Balanced)

        assertEquals(still.warpAmount, moving.warpAmount, 0.001f)
        assertTrue("half open should still show some pinch", still.warpAmount > 0f)
    }

    @Test
    fun `the battery profile has no warp`() {
        val channels = MotionChannelMapper.map(frame(0f, energy = 0f), MotionTuning.Battery)

        assertEquals(0f, channels.warpAmount, 0f)
    }

    @Test
    fun `dimming can never present as a black flash`() {
        val fullEnergy = MotionChannelMapper.map(frame(0.5f, energy = 1f), MotionTuning.Smooth)

        assertTrue("dim must stay well below opaque", fullEnergy.dimAlpha < 0.5f)
        assertTrue("content must stay visible", fullEnergy.contentAlpha > 0.5f)
    }

    @Test
    fun `the battery profile paints nothing`() {
        val channels = MotionChannelMapper.map(frame(0.5f, energy = 1f), MotionTuning.Battery)

        assertTrue("battery profile must be a no-op", channels.isNeutral)
    }

    @Test
    fun `the minimal profile has no veil`() {
        val channels = MotionChannelMapper.map(frame(0.4f, energy = 1f), MotionTuning.Minimal)

        assertEquals(0f, channels.blurRadiusDp, 0f)
    }

    @Test
    fun `position channels move monotonically with progress`() {
        var previousScale = -1f
        var previousExpansion = -1f
        for (step in 0..20) {
            val channels = MotionChannelMapper.map(frame(step / 20f, energy = 0f), MotionTuning.Balanced)
            assertTrue("scale must not go backwards", channels.contentScale >= previousScale)
            assertTrue("expansion must not go backwards", channels.expansion >= previousExpansion)
            previousScale = channels.contentScale
            previousExpansion = channels.expansion
        }
    }

    @Test
    fun `an inert tuning short-circuits to neutral`() {
        assertTrue(MotionTuning.Battery.isInert)
        assertTrue(MotionChannels.Neutral.isNeutral)
    }
}
