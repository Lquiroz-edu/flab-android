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
}
