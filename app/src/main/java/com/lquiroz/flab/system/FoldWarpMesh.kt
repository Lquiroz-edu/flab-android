package com.lquiroz.flab.system

import kotlin.math.abs

/**
 * The geometry behind Fold Wallpaper's hinge-pinch effect.
 *
 * This is the mechanism analysed from Apple's iPhone Duo opening animation: the background is not
 * cut into two halves or blurred at the seam, it is treated as one continuous, physically bendable
 * surface. A vertical band centred on the hinge is compressed toward the centreline — proportional
 * to how closed the device is — which is what makes the image look like it is folding rather than
 * being cropped. `Canvas.drawBitmapMesh` renders the result: this class only computes where the
 * mesh's vertices go.
 *
 * Two rows are enough. The pinch does not vary from top to bottom of the screen, so a taller mesh
 * would cost more vertices for a visually identical result — and this is recomputed every frame
 * during a transition, so keeping it cheap matters (DoD 22, 23).
 */
object FoldWarpMesh {

    /** Columns in the mesh. `drawBitmapMesh` needs `(COLUMNS + 1) * ROWS` vertices. */
    const val COLUMNS = 32
    const val ROWS = 2

    /** Length of the vertex array `buildVertices` fills: two floats (x, y) per vertex. */
    val VERTEX_COUNT = (COLUMNS + 1) * ROWS * 2

    /**
     * How wide the affected band is, as a fraction of the image width to each side of the hinge.
     * Chosen from the source footage: the pinch reads as a narrow band at the seam, not a warp that
     * reaches all the way to the edges of the screen.
     */
    private const val BAND_HALF_WIDTH = 0.22f

    /**
     * Fills [out] with mesh vertex positions for a [width] x [height] surface at [warpAmount].
     *
     * @param warpAmount `0f` (flat, identity mesh) to `1f` (columns at the hinge collapse onto the
     *   centreline). Values are clamped; callers pass [com.lquiroz.flab.motion.MotionChannels.warpAmount],
     *   which never exceeds [com.lquiroz.flab.motion.MotionChannels.MAX_WARP] by construction.
     * @param out must be sized at least [VERTEX_COUNT]. Passed in and reused rather than allocated
     *   here, because this runs once per frame during a transition and a fresh `FloatArray` every
     *   frame is exactly the kind of avoidable allocation the frame loop elsewhere in this project
     *   is written to skip.
     */
    fun buildVertices(width: Float, height: Float, warpAmount: Float, out: FloatArray) {
        require(out.size >= VERTEX_COUNT) { "out must hold at least $VERTEX_COUNT floats" }
        val warp = warpAmount.coerceIn(0f, 1f)

        var i = 0
        for (row in 0 until ROWS) {
            val y = if (ROWS == 1) 0f else height * row / (ROWS - 1)
            for (col in 0..COLUMNS) {
                out[i++] = displaceU(col.toFloat() / COLUMNS, warp) * width
                out[i++] = y
            }
        }
    }

    /**
     * Where a horizontal position `u` (`0f` left edge, `1f` right edge) lands at [warpAmount].
     *
     * Public because F/LAB Home places its icons through this same function: the icons ride the
     * bending wallpaper rather than sliding over it, which is the whole reading of the effect.
     */
    fun displaceU(u: Float, warpAmount: Float): Float {
        val d = u - 0.5f
        return u - d * warpAmount.coerceIn(0f, 1f) * falloff(abs(d))
    }

    /** `1f` exactly at the hinge, smoothly down to `0f` at the edge of the affected band. */
    private fun falloff(distanceFromCentre: Float): Float {
        if (distanceFromCentre >= BAND_HALF_WIDTH) return 0f
        val t = (1f - distanceFromCentre / BAND_HALF_WIDTH).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
