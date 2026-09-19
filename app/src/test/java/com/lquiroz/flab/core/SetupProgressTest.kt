package com.lquiroz.flab.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupProgressTest {

    @Test
    fun `three steps, in the order they should be tackled`() {
        val steps = SetupProgress.steps(false, false, false)

        assertEquals(3, steps.size)
        assertEquals(SetupStepId.EngineOn, steps[0].id)
        assertEquals(SetupStepId.OverlayPermission, steps[1].id)
        assertEquals(SetupStepId.AccessibilityPermission, steps[2].id)
    }

    @Test
    fun `nothing granted means nothing is complete`() {
        val steps = SetupProgress.steps(false, false, false)

        assertFalse(SetupProgress.isComplete(steps))
        assertEquals(SetupStepId.EngineOn, SetupProgress.nextStep(steps)?.id)
    }

    @Test
    fun `next step is the first undone one, regardless of which are done`() {
        // The two permissions can be granted in either order; the checklist must not assume one.
        val overlayFirst = SetupProgress.steps(true, false, true)
        assertEquals(SetupStepId.OverlayPermission, SetupProgress.nextStep(overlayFirst)?.id)

        val accessibilityFirst = SetupProgress.steps(true, true, false)
        assertEquals(SetupStepId.AccessibilityPermission, SetupProgress.nextStep(accessibilityFirst)?.id)
    }

    @Test
    fun `complete once all three are true, in any order they were done`() {
        val steps = SetupProgress.steps(true, true, true)

        assertTrue(SetupProgress.isComplete(steps))
        assertNull(SetupProgress.nextStep(steps))
    }

    @Test
    fun `engine off alone is enough to be incomplete`() {
        val steps = SetupProgress.steps(false, true, true)

        assertFalse(SetupProgress.isComplete(steps))
        assertEquals(SetupStepId.EngineOn, SetupProgress.nextStep(steps)?.id)
    }
}
