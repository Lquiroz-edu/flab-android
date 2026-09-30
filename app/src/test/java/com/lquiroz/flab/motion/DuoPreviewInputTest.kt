package com.lquiroz.flab.motion

import com.lquiroz.flab.motion.duo.DuoPreviewInput
import com.lquiroz.flab.motion.duo.FrameSmoothing
import org.junit.Assert.*
import org.junit.Test

class DuoPreviewInputTest {
    @Test fun steppedSensorIsNotClassifiedAsContinuous() {
        val input = DuoPreviewInput()
        listOf(0f, 90f, 180f, 90f, 0f).forEach { input.sample(it) }
        assertFalse(input.continuousObserved)
        assertEquals(5, input.samples)
    }
    @Test fun continuityRequiresDistinctIntermediateAngles() {
        val input = DuoPreviewInput()
        repeat(10) { input.sample(45f) }
        assertFalse(input.continuousObserved)
        input.sample(46f); input.sample(48f)
        assertTrue(input.continuousObserved)
    }
    @Test fun invalidSamplesDoNotOverwriteValidAngle() {
        val input = DuoPreviewInput()
        input.sample(35f)
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 181f).forEach { assertFalse(input.sample(it)) }
        assertEquals(1, input.samples)
        assertEquals(35f, input.rawAngle!!, 0f)
    }
    @Test fun referenceProgressHasCorrectPanelEndpoints() {
        assertEquals(0f, DuoPreviewInput.progress(180f, true), 0f)
        assertEquals(1f, DuoPreviewInput.progress(90f, true), 0f)
        assertEquals(0f, DuoPreviewInput.progress(0f, false), 0f)
        assertEquals(1f, DuoPreviewInput.progress(110f, false), 0f)
        assertEquals(0f, DuoPreviewInput.progress(Float.NaN, false), 0f)
    }
    @Test fun smoothingReversesWithoutOvershootOrPrediction() {
        val opening = FrameSmoothing.step(30f, 120f, 16f)
        assertTrue(opening in 30f..120f)
        val reversed = FrameSmoothing.step(opening, 20f, 16f)
        assertTrue(reversed in 20f..opening)
        assertEquals(opening, FrameSmoothing.step(opening, Float.NaN, 16f), 0f)
    }
}
