package com.lquiroz.flab.launcher

import com.lquiroz.flab.core.INNER_DISPLAY_MIN_DP
import com.lquiroz.flab.system.FoldWarpMesh

/**
 * The geometry of F/LAB Home. Pure, so it is tested.
 *
 * Why F/LAB has a home screen at all: the one part of the iPhone Duo's opening that no overlay,
 * wallpaper or window flag can reproduce is the *icons*. They ride the bending background and
 * reflow with the hinge, continuously. Android gives no app any way to move another launcher's
 * icons, so the only honest route to that effect is for F/LAB to be the launcher. This object
 * decides where every icon goes, and the pinch it applies is the very same displacement field
 * [FoldWarpMesh] bends the wallpaper with — icons and background cannot drift apart.
 */
data class GridSpec(
    val columns: Int,
    val rows: Int,
    val cellWidth: Float,
    val cellHeight: Float,
    /** Left inset of the first column, in px. */
    val left: Float,
) {
    val pageSize: Int get() = columns * rows
}

/** Top-left corner of a cell, in px, relative to the page. */
data class Slot(val x: Float, val y: Float)

object HomeLayout {
    const val COVER_COLUMNS = 4
    const val INNER_COLUMNS = 6
    const val MAX_ROWS = 6

    /** A window at least this wide is a Fold's inner display; the Core's own threshold. */
    const val INNER_MIN_WIDTH_DP = INNER_DISPLAY_MIN_DP

    fun columnsFor(widthDp: Int): Int =
        if (widthDp >= INNER_MIN_WIDTH_DP) INNER_COLUMNS else COVER_COLUMNS

    /**
     * Lays a page out: as many rows of [minCellHeightPx] as fit in [heightPx] (capped), spread to
     * fill the height evenly, and [columns] equal columns inside [horizontalPaddingPx].
     */
    fun spec(
        widthPx: Float,
        heightPx: Float,
        columns: Int,
        horizontalPaddingPx: Float,
        minCellHeightPx: Float,
    ): GridSpec {
        require(columns > 0) { "columns must be positive" }
        require(minCellHeightPx > 0f) { "minCellHeightPx must be positive" }
        val usableWidth = (widthPx - 2f * horizontalPaddingPx).coerceAtLeast(1f)
        val height = heightPx.coerceAtLeast(1f)
        val rows = (height / minCellHeightPx).toInt().coerceIn(1, MAX_ROWS)
        return GridSpec(
            columns = columns,
            rows = rows,
            cellWidth = usableWidth / columns,
            cellHeight = height / rows,
            left = horizontalPaddingPx,
        )
    }

    /** Row-major: the first row fills left to right before the second starts. */
    fun slot(spec: GridSpec, index: Int): Slot {
        require(index >= 0) { "index must not be negative" }
        val row = index / spec.columns
        val col = index % spec.columns
        return Slot(spec.left + col * spec.cellWidth, row * spec.cellHeight)
    }

    /** Never zero: an empty home still has one page to stand on. */
    fun pageCount(itemCount: Int, pageSize: Int): Int {
        require(pageSize > 0) { "pageSize must be positive" }
        return if (itemCount <= 0) 1 else (itemCount + pageSize - 1) / pageSize
    }

    /**
     * Where a horizontal position lands under the Duo pinch: [FoldWarpMesh]'s field, so an icon
     * centred on a wallpaper feature stays centred on it while both bend. Identity at `0f`.
     */
    fun warpX(x: Float, width: Float, warpAmount: Float): Float {
        if (width <= 0f) return x
        return FoldWarpMesh.displaceU(x / width, warpAmount) * width
    }
}
