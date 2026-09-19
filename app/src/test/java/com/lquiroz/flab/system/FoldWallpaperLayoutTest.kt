package com.lquiroz.flab.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldWallpaperLayoutTest {

    @Test
    fun `there are exactly three cards`() {
        assertEquals(3, FoldWallpaperLayout.cardRects().size)
    }

    @Test
    fun `every card stays within the unit canvas`() {
        FoldWallpaperLayout.cardRects().forEach { rect ->
            assertTrue("left in bounds", rect.left in 0f..1f)
            assertTrue("right in bounds", rect.right in 0f..1f)
            assertTrue("top in bounds", rect.top in 0f..1f)
            assertTrue("bottom in bounds", rect.bottom in 0f..1f)
            assertTrue("positive width", rect.width > 0f)
            assertTrue("positive height", rect.height > 0f)
        }
    }

    @Test
    fun `the top third is left clear for the launcher's own clock`() {
        FoldWallpaperLayout.cardRects().forEach { rect ->
            assertTrue("card starts too high: top=${rect.top}", rect.top >= 0.29f)
        }
    }

    @Test
    fun `cards do not overlap`() {
        val rects = FoldWallpaperLayout.cardRects()
        for (i in rects.indices) {
            for (j in rects.indices) {
                if (i == j) continue
                val a = rects[i]
                val b = rects[j]
                val overlapsX = a.left < b.right && b.left < a.right
                val overlapsY = a.top < b.bottom && b.top < a.bottom
                assertTrue("cards $i and $j overlap", !(overlapsX && overlapsY))
            }
        }
    }

    @Test
    fun `the two top cards sit at the same height`() {
        val rects = FoldWallpaperLayout.cardRects()
        assertEquals(rects[0].top, rects[1].top, 0.001f)
        assertEquals(rects[0].bottom, rects[1].bottom, 0.001f)
    }

    @Test
    fun `the third card spans the full content width`() {
        val rects = FoldWallpaperLayout.cardRects()
        assertEquals(rects[0].left, rects[2].left, 0.001f)
        assertEquals(rects[1].right, rects[2].right, 0.001f)
    }

    @Test
    fun `toPixels scales a unit rect by the given canvas size`() {
        val rect = UnitRect(0.1f, 0.2f, 0.5f, 0.6f)

        val pixels = rect.toPixels(1000f, 2000f)

        assertEquals(100f, pixels.left, 0.001f)
        assertEquals(400f, pixels.top, 0.001f)
        assertEquals(500f, pixels.right, 0.001f)
        assertEquals(1200f, pixels.bottom, 0.001f)
    }

    @Test
    fun `content cards carry only real, on-device data`() {
        val cards = FoldWallpaperContent.cards(
            timeText = "10:41",
            dateText = "Fri, Sep 18",
            batteryPercent = 86,
            isCharging = false,
            foldPostureLabel = "Open",
            deviceLabel = "Galaxy Z Fold 8",
        )

        assertEquals(3, cards.size)
        assertEquals("86%", cards[1].value)
        assertEquals("Open", cards[2].value)
    }

    @Test
    fun `charging state is called out, not silently dropped`() {
        val charging = FoldWallpaperContent.cards(
            timeText = "10:41",
            dateText = "Fri, Sep 18",
            batteryPercent = 50,
            isCharging = true,
            foldPostureLabel = "Closed",
            deviceLabel = "Galaxy Z Fold 8",
        )

        assertEquals("Charging", charging[1].caption)
    }
}
