package com.lquiroz.flab.studio

import org.junit.Assert.assertEquals
import org.junit.Test

class PageGeometryTest {
    @Test fun pageHasStableEndsAndMaximumLiftAtMidpoint() {
        assertEquals(1f, PageGeometry.edge(0f), .001f)
        assertEquals(-1f, PageGeometry.edge(1f), .001f)
        assertEquals(0f, PageGeometry.edge(.5f), .001f)
        assertEquals(1f, PageGeometry.lift(.5f), .001f)
    }

    @Test fun invalidAndOutOfRangeProgressIsContained() {
        assertEquals(0f, PageGeometry.progress(Float.NaN), 0f)
        assertEquals(0f, PageGeometry.progress(-4f), 0f)
        assertEquals(1f, PageGeometry.progress(4f), 0f)
    }
}
