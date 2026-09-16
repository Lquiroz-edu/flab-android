package com.lquiroz.flab.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowModeTest {
    @Test
    fun `compact below 600 dp`() {
        assertEquals(WindowMode.Compact, windowModeForWidth(599))
    }

    @Test
    fun `medium from 600 to 839 dp`() {
        assertEquals(WindowMode.Medium, windowModeForWidth(600))
        assertEquals(WindowMode.Medium, windowModeForWidth(839))
    }

    @Test
    fun `expanded from 840 dp`() {
        assertEquals(WindowMode.Expanded, windowModeForWidth(840))
    }

    @Test
    fun `frost begins immediately and saturates before fully open`() {
        assertEquals(0f, frostAmount(0f))
        assertEquals(1f, frostAmount(1f))
    }

    @Test
    fun `dashboard waits until the shared rail is established`() {
        assertEquals(0f, dashboardReveal(0.18f))
        assertEquals(1f, dashboardReveal(1f))
    }

    @Test
    fun `hinge angle maps to physical open progress`() {
        assertEquals(0f, hingeOpenFraction(angle = 0f, mode = WindowMode.Compact))
        assertEquals(0.5f, hingeOpenFraction(angle = 90f, mode = WindowMode.Medium))
        assertEquals(1f, hingeOpenFraction(angle = 180f, mode = WindowMode.Expanded))
        assertEquals(1f, hingeOpenFraction(angle = null, mode = WindowMode.Medium))
    }
}
