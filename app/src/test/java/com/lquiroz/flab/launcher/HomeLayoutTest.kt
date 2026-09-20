package com.lquiroz.flab.launcher

import com.lquiroz.flab.motion.MotionChannels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The geometry of F/LAB Home: where icons sit, and how they ride the Duo pinch. */
class HomeLayoutTest {

    @Test
    fun `cover and inner displays get their own column counts`() {
        assertEquals(HomeLayout.COVER_COLUMNS, HomeLayout.columnsFor(360))
        assertEquals(HomeLayout.COVER_COLUMNS, HomeLayout.columnsFor(HomeLayout.INNER_MIN_WIDTH_DP - 1))
        assertEquals(HomeLayout.INNER_COLUMNS, HomeLayout.columnsFor(HomeLayout.INNER_MIN_WIDTH_DP))
        assertEquals(HomeLayout.INNER_COLUMNS, HomeLayout.columnsFor(840))
    }

    @Test
    fun `rows fit the height and are capped`() {
        val spec = HomeLayout.spec(widthPx = 1000f, heightPx = 1000f, columns = 4, horizontalPaddingPx = 20f, minCellHeightPx = 300f)
        assertEquals(3, spec.rows)
        assertEquals(240f, spec.cellWidth, 0.001f)
        assertEquals(1000f / 3f, spec.cellHeight, 0.001f)
        assertEquals(12, spec.pageSize)

        val tall = HomeLayout.spec(1000f, 100_000f, 4, 0f, 100f)
        assertEquals(HomeLayout.MAX_ROWS, tall.rows)

        val tiny = HomeLayout.spec(1000f, 10f, 4, 0f, 100f)
        assertEquals("never zero rows", 1, tiny.rows)
    }

    @Test
    fun `slots are row major`() {
        val spec = HomeLayout.spec(1000f, 900f, 4, 20f, 300f)
        assertEquals(Slot(20f, 0f), HomeLayout.slot(spec, 0))
        assertEquals(Slot(20f + 3 * 240f, 0f), HomeLayout.slot(spec, 3))
        assertEquals(Slot(20f, 300f), HomeLayout.slot(spec, 4))
    }

    @Test
    fun `an empty home still has a page`() {
        assertEquals(1, HomeLayout.pageCount(0, 24))
        assertEquals(1, HomeLayout.pageCount(24, 24))
        assertEquals(2, HomeLayout.pageCount(25, 24))
    }

    @Test
    fun `no pinch is the identity`() {
        for (x in listOf(0f, 137f, 500f, 863f, 1000f)) {
            assertEquals(x, HomeLayout.warpX(x, 1000f, 0f), 0.0001f)
        }
    }

    @Test
    fun `the pinch is symmetric about the hinge and leaves the edges alone`() {
        val width = 1000f
        val warp = MotionChannels.MAX_WARP
        assertEquals(500f, HomeLayout.warpX(500f, width, warp), 0.0001f)
        val left = HomeLayout.warpX(420f, width, warp)
        val right = HomeLayout.warpX(580f, width, warp)
        assertEquals(500f - left, right - 500f, 0.001f)
        assertTrue("the left icon moves toward the hinge", left > 420f)
        // Outside the band nothing moves — the pinch is a seam effect, not a whole-screen squeeze.
        assertEquals(100f, HomeLayout.warpX(100f, width, warp), 0.0001f)
        assertEquals(900f, HomeLayout.warpX(900f, width, warp), 0.0001f)
    }

    @Test
    fun `icons never cross each other at the strongest pinch`() {
        // If the field were not monotonic, two neighbouring columns could swap sides of the hinge
        // and the grid would visibly fold over itself — the "glitch rather than paper" DoD 30 bans.
        val width = 2000f
        val xs = (0..200).map { it * 10f }
        val warped = xs.map { HomeLayout.warpX(it, width, MotionChannels.MAX_WARP) }
        warped.zipWithNext().forEach { (a, b) -> assertTrue("$a must stay left of $b", a < b) }
    }
}
