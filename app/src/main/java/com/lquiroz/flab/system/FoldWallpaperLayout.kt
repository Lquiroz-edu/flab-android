package com.lquiroz.flab.system

/**
 * A rectangle in `0f..1f` fractions of the wallpaper canvas, not pixels.
 *
 * Fractions rather than pixels because the same layout has to work on a cover-display-sized crop
 * and on the full inner-display canvas without being recomputed for every possible resolution — a
 * live wallpaper is asked to render at whatever size the launcher decides, sight unseen.
 */
data class UnitRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun toPixels(canvasWidth: Float, canvasHeight: Float): UnitRect = UnitRect(
        left = left * canvasWidth,
        top = top * canvasHeight,
        right = right * canvasWidth,
        bottom = bottom * canvasHeight,
    )
}

/** What one frosted-glass card shows. Real device data only — see [FoldWallpaperContent]. */
data class GlassCard(val rect: UnitRect, val title: String, val value: String, val caption: String? = null)

/**
 * Lays out Fold Wallpaper's glass cards.
 *
 * Pure geometry, kept apart from `FoldWallpaperService`'s Canvas drawing so the placement — no
 * overlap, clear of the status bar, clear of where a launcher usually puts its own clock widget —
 * is a fact that can be asserted in a JVM test rather than eyeballed on a device.
 */
object FoldWallpaperLayout {

    private const val MARGIN_X = 0.07f
    private const val TOP_CLEARANCE = 0.30f
    private const val GAP = 0.035f
    private const val TOP_ROW_HEIGHT = 0.16f
    private const val BOTTOM_ROW_HEIGHT = 0.14f

    /**
     * The three card rectangles, top row split in two plus one full-width row below.
     *
     * [TOP_CLEARANCE] leaves the upper ~30% of the screen untouched, because most launchers place
     * their own clock or search bar there — Fold Wallpaper's cards are meant to sit alongside that,
     * not compete with it.
     */
    fun cardRects(): List<UnitRect> {
        val columnWidth = (1f - 2f * MARGIN_X - GAP) / 2f
        val leftColumnEnd = MARGIN_X + columnWidth
        val topRowBottom = TOP_CLEARANCE + TOP_ROW_HEIGHT
        val bottomRowTop = topRowBottom + GAP

        return listOf(
            UnitRect(MARGIN_X, TOP_CLEARANCE, leftColumnEnd, topRowBottom),
            UnitRect(leftColumnEnd + GAP, TOP_CLEARANCE, 1f - MARGIN_X, topRowBottom),
            UnitRect(MARGIN_X, bottomRowTop, 1f - MARGIN_X, bottomRowTop + BOTTOM_ROW_HEIGHT),
        )
    }
}

/**
 * Builds the three cards' content from real, on-device signals.
 *
 * Nothing here is invented: time and date come from the clock, battery from `BatteryManager`, and
 * fold state from `FLabCore` — the same source every other module reads (DoD 2). There is no
 * weather, calendar or account data, because F/LAB has no honest source for any of that.
 */
object FoldWallpaperContent {

    fun cards(
        timeText: String,
        dateText: String,
        batteryPercent: Int,
        isCharging: Boolean,
        foldPostureLabel: String,
        deviceLabel: String,
    ): List<GlassCard> {
        val rects = FoldWallpaperLayout.cardRects()
        return listOf(
            GlassCard(rects[0], title = "TIME", value = timeText, caption = dateText),
            GlassCard(
                rects[1],
                title = "BATTERY",
                value = "$batteryPercent%",
                caption = if (isCharging) "Charging" else null,
            ),
            GlassCard(rects[2], title = "FOLD", value = foldPostureLabel, caption = deviceLabel),
        )
    }
}
