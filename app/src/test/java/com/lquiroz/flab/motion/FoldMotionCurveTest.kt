package com.lquiroz.flab.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldMotionCurveTest {
    @Test fun coverFrostGrowsOnlyDuringPanelHandoff() {
        assertEquals(0f, FoldMotionCurve.coverTilt(0f), .001f)
        assertTrue(FoldMotionCurve.coverTilt(.06f) in 15f..30f)
        assertEquals(42f, FoldMotionCurve.coverTilt(.4f), .001f)
    }

    @Test fun innerFrostResolvesContinuouslyTowardFlat() {
        val early = FoldMotionCurve.innerTilt(.12f)
        val middle = FoldMotionCurve.innerTilt(.55f)
        val flat = FoldMotionCurve.innerTilt(1f)
        assertTrue(early > middle)
        assertTrue(middle > flat)
        assertEquals(0f, flat, .001f)
    }

    @Test fun velocityEnergyIsBounded() {
        assertEquals(0f, FoldMotionCurve.velocityEnergy(0f), .001f)
        assertTrue(FoldMotionCurve.velocityEnergy(210f) in .49f..51f)
        assertEquals(1f, FoldMotionCurve.velocityEnergy(2_000f), .001f)
    }
}
