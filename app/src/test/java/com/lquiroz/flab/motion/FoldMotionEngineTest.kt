package com.lquiroz.flab.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DoD 22: the engine must stop asking for frames the moment there is nothing to draw. */
class FoldMotionEngineTest {

    private val frameNanos = 16_666_666L

    @Test
    fun `a fresh engine asks for no frames`() {
        val engine = FoldMotionEngine(MotionTuning.Balanced)

        assertFalse("nothing has happened yet", engine.needsFrames)
        assertTrue(engine.channels.isNeutral)
    }

    @Test
    fun `evidence starts the loop and settling stops it`() {
        val engine = FoldMotionEngine(MotionTuning.Balanced)
        engine.submit(FoldEvidence(1f, EvidenceSource.HingeAngle, 0L))

        var now = 0L
        engine.advance(now)
        now += frameNanos
        engine.advance(now)
        assertTrue("should be animating", engine.needsFrames)

        var guard = 0
        while (engine.needsFrames && guard++ < 1_000) {
            now += frameNanos
            engine.advance(now)
        }

        assertFalse("must eventually settle", engine.needsFrames)
        assertEquals(1f, engine.frame.progress, 0.01f)
    }

    @Test
    fun `the battery profile never asks for a frame`() {
        val engine = FoldMotionEngine(MotionTuning.Battery)
        engine.submit(FoldEvidence(1f, EvidenceSource.HingeAngle, 0L))
        engine.advance(frameNanos)

        assertFalse("an inert tuning must never schedule work", engine.needsFrames)
        assertTrue(engine.channels.isNeutral)
    }

    @Test
    fun `changing profile re-maps without waiting for movement`() {
        val engine = FoldMotionEngine(MotionTuning.Balanced)
        engine.scrubTo(0.5f, nowNanos = 0L)
        val before = engine.channels

        engine.updateTuning(MotionTuning.Minimal)

        assertTrue(
            "a profile change should be visible immediately",
            engine.channels != before || before.isNeutral,
        )
    }

    @Test
    fun `scrubbing settles immediately`() {
        val engine = FoldMotionEngine(MotionTuning.Balanced)
        engine.scrubTo(0.3f, nowNanos = 0L)

        assertEquals(0.3f, engine.frame.progress, 0f)
        assertFalse("the finger is the evidence; there is nothing to animate", engine.needsFrames)
    }
}
