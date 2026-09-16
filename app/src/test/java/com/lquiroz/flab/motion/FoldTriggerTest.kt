package com.lquiroz.flab.motion

import org.junit.Assert.*
import org.junit.Test

class FoldTriggerTest {
    @Test fun initialReadingDoesNotFlashAndCoarseSensorStaysCoarse() {
        val model = FoldTrigger()
        assertFalse(model.accept(180f, 0))
        assertTrue(model.accept(90f, 500))
        assertFalse(model.accept(90f, 600))
        assertTrue(model.accept(0f, 1000))
        assertFalse(model.hasIntermediateReadings)
    }
    @Test fun invalidReadingsCannotCauseEffects() {
        val model = FoldTrigger()
        assertFalse(model.accept(Float.NaN, 0))
        assertFalse(model.accept(-1f, 500))
        assertFalse(model.accept(181f, 1000))
        assertFalse(model.accept(180f, 1500))
    }
    @Test fun fineSensorDoesNotTriggerEverySample() {
        val model = FoldTrigger()
        model.accept(180f, 0)
        assertTrue(model.accept(170f, 500))
        for (angle in listOf(160f, 150f, 140f, 130f)) assertFalse(model.accept(angle, 1000))
        assertTrue(model.hasIntermediateReadings)
    }
    @Test fun fastEndpointChangesAreDebounced() {
        val model = FoldTrigger()
        model.accept(180f, 0)
        assertTrue(model.accept(90f, 500))
        assertFalse(model.accept(0f, 520))
        assertFalse(model.accept(0f, 1500))
        assertTrue(model.accept(90f, 1600))
    }
}
