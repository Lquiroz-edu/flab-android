package com.lquiroz.flab.motion

import com.lquiroz.flab.motion.MotionChannels.Companion.MAX_BLUR_DP
import com.lquiroz.flab.motion.MotionChannels.Companion.MAX_DIM_ALPHA
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
                }
            }
        }
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
