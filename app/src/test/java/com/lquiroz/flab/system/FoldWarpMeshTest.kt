package com.lquiroz.flab.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The geometry behind the Fold Wallpaper hinge-pinch effect (analysed from the Duo footage). */
class FoldWarpMeshTest {

    private val width = 1080f
    private val height = 2340f

    private fun vertices(warpAmount: Float): FloatArray {
        val out = FloatArray(FoldWarpMesh.VERTEX_COUNT)
        FoldWarpMesh.buildVertices(width, height, warpAmount, out)
        return out
    }

    private fun x(verts: FloatArray, row: Int, col: Int): Float {
        val rowStride = (FoldWarpMesh.COLUMNS + 1) * 2
        return verts[row * rowStride + col * 2]
    }

    @Test
    fun `a flat device produces an unwarped identity grid`() {
        val verts = vertices(0f)
        for (col in 0..FoldWarpMesh.COLUMNS) {
            val expectedX = width * col / FoldWarpMesh.COLUMNS
            assertEquals("column $col", expectedX, x(verts, 0, col), 0.01f)
        }
    }

    @Test
    fun `the edges never move, at any warp`() {
        for (warp in listOf(0f, 0.3f, 0.62f, 1f)) {
            val verts = vertices(warp)
            assertEquals("left edge at warp $warp", 0f, x(verts, 0, 0), 0.001f)
            assertEquals(
                "right edge at warp $warp",
                width,
                x(verts, 0, FoldWarpMesh.COLUMNS),
                0.001f,
            )
        }
    }

    @Test
    fun `the hinge column never moves`() {
        // It is the pivot: nothing to pull it toward.
        val centreCol = FoldWarpMesh.COLUMNS / 2
        for (warp in listOf(0.2f, 0.62f, 1f)) {
            val verts = vertices(warp)
            assertEquals(width / 2f, x(verts, 0, centreCol), 0.5f)
        }
    }

    @Test
    fun `columns outside the affected band are untouched`() {
        // The whole point of a falloff rather than a hard cutoff: content far from the hinge must
        // be pixel-identical to the flat case, or the wallpaper would visibly distort content that
        // has nothing to do with the fold.
        val flat = vertices(0f)
        val warped = vertices(1f)
        for (col in 0..FoldWarpMesh.COLUMNS) {
            val u = col.toFloat() / FoldWarpMesh.COLUMNS
            if (kotlin.math.abs(u - 0.5f) >= 0.22f) {
                assertEquals("column $col should be untouched", x(flat, 0, col), x(warped, 0, col), 0.01f)
            }
        }
    }

    @Test
    fun `columns never cross — the mesh cannot fold pixels backwards`() {
        for (warp in listOf(0f, 0.1f, 0.3f, 0.62f, 1f)) {
            val verts = vertices(warp)
            for (col in 0 until FoldWarpMesh.COLUMNS) {
                assertTrue(
                    "columns $col and ${col + 1} crossed at warp $warp",
                    x(verts, 0, col) < x(verts, 0, col + 1),
                )
            }
        }
    }

    @Test
    fun `both mesh rows are identical — the pinch does not vary vertically`() {
        val verts = vertices(0.5f)
        val rowStride = (FoldWarpMesh.COLUMNS + 1) * 2
        for (col in 0..FoldWarpMesh.COLUMNS) {
            assertEquals(x(verts, 0, col), verts[rowStride + col * 2], 0.001f)
        }
    }

    @Test
    fun `more warp pulls the band columns further toward the hinge`() {
        val nearHingeCol = FoldWarpMesh.COLUMNS / 2 + 2
        val lessWarp = x(vertices(0.2f), 0, nearHingeCol)
        val moreWarp = x(vertices(0.6f), 0, nearHingeCol)
        val centre = width / 2f

        assertTrue(
            "more warp should pull the column closer to the hinge",
            kotlin.math.abs(moreWarp - centre) < kotlin.math.abs(lessWarp - centre),
        )
    }

    @Test
    fun `warp amount is clamped rather than trusted`() {
        val overOne = vertices(5f)
        val atOne = vertices(1f)
        for (col in 0..FoldWarpMesh.COLUMNS) {
            assertEquals(x(atOne, 0, col), x(overOne, 0, col), 0.01f)
        }
    }

    @Test
    fun `the output array is sized for two full rows`() {
        assertEquals((FoldWarpMesh.COLUMNS + 1) * FoldWarpMesh.ROWS * 2, FoldWarpMesh.VERTEX_COUNT)
    }
}
