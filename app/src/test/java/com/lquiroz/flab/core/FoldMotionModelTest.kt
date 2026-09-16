package com.lquiroz.flab.core

import org.junit.Assert.*
import org.junit.Test

class FoldMotionModelTest {
    @Test fun invalidSamplesAreRejected() {
        val model = FoldMotionModel()
        assertNull(model.read(Float.NaN))
        assertNull(model.read(-1f))
        assertNull(model.read(181f))
    }

    @Test fun coarsePosturesUsePerceptualFallback() {
        val model = FoldMotionModel()
        val closed = model.read(0f)!!
        val half = model.read(90f)!!
        val open = model.read(180f)!!
        assertEquals(FoldPosture.CLOSED, closed.posture)
        assertEquals(.5f, half.target, .001f)
        assertEquals(FoldPosture.OPEN, open.posture)
        assertEquals(SensorMode.POSTURE_FALLBACK, half.mode)
        assertFalse(half.direct)
    }

    @Test fun realIntermediateSamplesEnableDirectPhysicalTracking() {
        val model = FoldMotionModel()
        model.read(25f)
        model.read(55f)
        val reading = model.read(118f)!!
        assertEquals(SensorMode.CONTINUOUS, reading.mode)
        assertTrue(reading.direct)
        assertEquals(118f / 180f, reading.target, .001f)
    }
}
