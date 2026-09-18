package com.lquiroz.flab.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Fold Motion contract from DoD 3.
 *
 * These tests exist to pin down the difference between "interpolate towards physical evidence" and
 * "play an animation when an event arrives", because the two look identical in a screenshot and
 * completely different in the hand.
 */
class PerceptualInterpolatorTest {

    private val frameNanos = 16_666_666L

    private fun runFrames(
        interpolator: PerceptualInterpolator,
        count: Int,
        startNanos: Long = 0L,
    ): FoldMotionFrame {
        var now = startNanos
        var frame = interpolator.advance(now)
        repeat(count) {
            now += frameNanos
            frame = interpolator.advance(now)
        }
        return frame
    }

    @Test
    fun `progress travels towards evidence rather than jumping`() {
        val interpolator = PerceptualInterpolator(MotionTuning.Balanced)
        interpolator.submit(FoldEvidence(1f, EvidenceSource.HingeAngle, 0L))

        val afterOneFrame = runFrames(interpolator, count = 1)

        assertTrue("should have started moving", afterOneFrame.progress > 0f)
        assertTrue("should not have arrived in one frame", afterOneFrame.progress < 0.5f)
    }

    @Test
    fun `a half-open event does not complete the animation`() {
        // The heart of DoD 3: HALF_OPENED is a target of 0.5, not a trigger for a 0 -> 1 animation.
        val interpolator = PerceptualInterpolator(MotionTuning.Balanced)
        interpolator.submit(FoldEvidence(0.5f, EvidenceSource.PostureEvent, 0L))

        val settled = runFrames(interpolator, count = 120)

        assertEquals(0.5f, settled.progress, 0.01f)
        assertTrue("should settle at the evidence", settled.settled)
    }

    @Test
    fun `motion stops when the user stops moving the device`() {
        val interpolator = PerceptualInterpolator(MotionTuning.Balanced)
        var now = 0L
        // The device is opened partway, with evidence arriving as it moves...
        listOf(0.1f, 0.2f, 0.3f, 0.4f).forEach { progress ->
            interpolator.submit(FoldEvidence(progress, EvidenceSource.HingeAngle, now))
            now += frameNanos
            interpolator.advance(now)
        }
        // ...and then the hand stops. No further evidence arrives.
        var frame = interpolator.advance(now)
        repeat(60) {
            now += frameNanos
            frame = interpolator.advance(now)
        }

        assertEquals("must hold at the last observed position", 0.4f, frame.progress, 0.02f)
        assertTrue(frame.settled)
        assertEquals("a settled frame has no veil", 0f, frame.energy, 0f)
    }

    @Test
    fun `settling releases the frame loop`() {
        // DoD 22: no rendering when there is no transition.
        val interpolator = PerceptualInterpolator(MotionTuning.Balanced)
        interpolator.submit(FoldEvidence(1f, EvidenceSource.HingeAngle, 0L))

        var now = 0L
        interpolator.advance(now)
        repeat(3) {
            now += frameNanos
            interpolator.advance(now)
        }
        assertFalse("still travelling", interpolator.advance(now).settled)

        var frame = interpolator.advance(now)
        repeat(200) {
            now += frameNanos
            frame = interpolator.advance(now)
        }
        assertTrue("must eventually stop asking for frames", frame.settled)
    }

    @Test
    fun `energy tracks movement and not position`() {
        // A device held at half-open has progress 0.5 and must still have zero energy, otherwise
        // the veil channels would leave it permanently blurred.
        val interpolator = PerceptualInterpolator(MotionTuning.Balanced)
        interpolator.submit(FoldEvidence(0.5f, EvidenceSource.HingeAngle, 0L))

        var now = 0L
        interpolator.advance(now)
        now += frameNanos
        val moving = interpolator.advance(now)
        assertTrue("moving should build energy", moving.energy > 0f)

        repeat(200) {
            now += frameNanos
            interpolator.advance(now)
        }
        val held = interpolator.advance(now + frameNanos)

        assertEquals(0.5f, held.progress, 0.01f)
        assertEquals("held steady means no veil", 0f, held.energy, 0f)
    }

    @Test
    fun `progress reverses when the device is closed again`() {
        val interpolator = PerceptualInterpolator(MotionTuning.Balanced)
        interpolator.submit(FoldEvidence(1f, EvidenceSource.HingeAngle, 0L))
        var now = 0L
        repeat(200) {
            now += frameNanos
            interpolator.advance(now)
        }
        assertEquals(1f, interpolator.progress, 0.01f)

        interpolator.submit(FoldEvidence(0f, EvidenceSource.HingeAngle, now))
        repeat(200) {
            now += frameNanos
            interpolator.advance(now)
        }

        assertEquals(0f, interpolator.progress, 0.01f)
    }

    @Test
    fun `a long gap between frames cannot produce a jump`() {
        // Coming back from sleep, or a dropped frame, must not inject an impulse: DoD 30 lists an
        // abrupt scale change as a blocking artifact.
        val interpolator = PerceptualInterpolator(MotionTuning.Balanced)
        interpolator.submit(FoldEvidence(1f, EvidenceSource.HingeAngle, 0L))
        interpolator.advance(0L)

        val afterGap = interpolator.advance(5_000_000_000L)

        assertTrue("dt must be clamped", afterGap.progress < 0.5f)
    }

    @Test
    fun `progress stays within bounds under a stiff tuning`() {
        val interpolator = PerceptualInterpolator(MotionTuning.Smooth)
        var now = 0L
        repeat(300) { index ->
            interpolator.submit(
                FoldEvidence(if (index % 2 == 0) 1f else 0f, EvidenceSource.HingeAngle, now),
            )
            now += frameNanos
            val frame = interpolator.advance(now)
            assertTrue("progress escaped 0..1: ${frame.progress}", frame.progress in 0f..1f)
        }
    }

    @Test
    fun `scrubbing snaps without travel`() {
        val interpolator = PerceptualInterpolator(MotionTuning.Balanced)
        interpolator.jumpTo(0.75f, EvidenceSource.Manual, nowNanos = 0L)

        assertEquals(0.75f, interpolator.progress, 0f)
        assertEquals(0f, interpolator.velocity, 0f)
    }

    @Test
    fun `minimal tuning never overshoots`() {
        val interpolator = PerceptualInterpolator(MotionTuning.Minimal)
        interpolator.submit(FoldEvidence(1f, EvidenceSource.HingeAngle, 0L))

        var now = 0L
        repeat(200) {
            now += frameNanos
            val frame = interpolator.advance(now)
            assertTrue("overshot to ${frame.progress}", frame.progress <= 1f)
        }
    }
}
