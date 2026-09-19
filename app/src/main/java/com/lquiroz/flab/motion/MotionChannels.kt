package com.lquiroz.flab.motion

/**
 * The visual output of the Fold Motion Engine for one frame.
 *
 * There are two families of channel here, and keeping them apart is the whole idea:
 *
 *  - **Position channels** ([contentScale], [expansion], [translationYDp]) follow
 *    [FoldMotionFrame.progress]. They describe where the content sits for a given device opening,
 *    and they are stable when the device is held still.
 *  - **Veil channels** ([blurRadiusDp], [dimAlpha], [contentAlpha], [elevationDp]) follow
 *    [FoldMotionFrame.energy], i.e. how fast the device is *currently* moving. They exist to soften
 *    a change that is happening, so they must be zero whenever nothing is happening — including at
 *    half-open, which is a resting posture and not a permanent transition.
 *
 * Deriving the veil from position instead of energy is the classic mistake: it leaves a device
 * parked at half-open permanently blurred and dimmed, which reads as a broken overlay.
 *
 * ### Channels that deliberately do not exist
 *
 * There is no seam, hinge line, crease mask, curtain or centre band channel, and there must never
 * be one on F/LAB's own Compose surfaces. [warpAmount] looks like it breaks that rule and does not:
 * it exists only for a surface that owns a single continuous background image top to bottom — a
 * wallpaper — where bending that one image at the hinge is what makes the fold *less* visible, the
 * same argument in reverse. Applied to a layered Compose screen it would do nothing useful, which
 * is why no Compose surface reads it.
 *
 * The same goes for a full-bleed opaque scrim: [dimAlpha] is capped well below opacity so a
 * transition can never present as a black flash.
 */
data class MotionChannels(
    val contentScale: Float,
    val contentAlpha: Float,
    val blurRadiusDp: Float,
    val dimAlpha: Float,
    val elevationDp: Float,
    val translationYDp: Float,
    val expansion: Float,
    /**
     * How strongly a continuous background should pinch at the hinge, `0f` (flat, no pinch) to
     * [MAX_WARP]. Position-driven, like [contentScale]: a device held half-open shows a stable
     * pinch rather than one that fades based on how fast the hand is moving. Consumed by
     * `FoldWallpaperService`'s bitmap-mesh warp; meaningless to anything else.
     */
    val warpAmount: Float = 0f,
) {
    /** True when this frame is visually identical to doing nothing, so the layer can be skipped. */
    val isNeutral: Boolean
        get() = contentScale == 1f &&
            contentAlpha == 1f &&
            blurRadiusDp == 0f &&
            dimAlpha == 0f &&
            elevationDp == 0f &&
            translationYDp == 0f &&
            expansion == 1f &&
            warpAmount == 0f

    companion object {
        val Neutral = MotionChannels(
            contentScale = 1f,
            contentAlpha = 1f,
            blurRadiusDp = 0f,
            dimAlpha = 0f,
            elevationDp = 0f,
            translationYDp = 0f,
            expansion = 1f,
            warpAmount = 0f,
        )

        /** Hard ceilings. Exceeding any of these is a DoD 30 "visible artifact" bug, not a taste call. */
        const val MAX_BLUR_DP = 18f
        const val MAX_DIM_ALPHA = 0.34f
        const val MIN_CONTENT_ALPHA = 0.82f
        const val MIN_CONTENT_SCALE = 0.93f
        const val MAX_ELEVATION_DP = 14f
        const val MAX_TRANSLATION_DP = 10f

        /**
         * The pinch at a fully closed device, as a fraction of the image's half-width that the
         * hinge column is allowed to swallow. Kept well short of 1f: a pinch that reaches the edges
         * of the image would fold content from the *other* half on top of itself, which reads as a
         * glitch rather than as paper bending.
         */
        const val MAX_WARP = 0.62f
    }
}

/**
 * Maps a [FoldMotionFrame] onto [MotionChannels] for the given [MotionTuning].
 *
 * Pure and allocation-light: this runs once per frame during a transition.
 */
object MotionChannelMapper {

    fun map(frame: FoldMotionFrame, tuning: MotionTuning): MotionChannels {
        if (tuning.isInert) return MotionChannels.Neutral

        val progress = frame.progress.coerceIn(0f, 1f)
        val energy = frame.energy.coerceIn(0f, 1f)
        // Veil strength is eased so that small, slow adjustments stay completely untouched and only
        // deliberate movement earns a treatment.
        val veil = energy * energy * (3f - 2f * energy)

        val scale = lerp(
            MotionChannels.MIN_CONTENT_SCALE,
            1f,
            // Scale is driven by position but attenuated by intensity, so a Minimal profile shrinks
            // the travel rather than shortening it in time.
            1f - (1f - easeInOutCubic(progress)) * tuning.scaleIntensity,
        )

        return MotionChannels(
            contentScale = scale,
            contentAlpha = lerp(1f, MotionChannels.MIN_CONTENT_ALPHA, veil * tuning.blurIntensity),
            blurRadiusDp = MotionChannels.MAX_BLUR_DP * veil * tuning.blurIntensity,
            dimAlpha = MotionChannels.MAX_DIM_ALPHA * veil * tuning.dimIntensity,
            elevationDp = MotionChannels.MAX_ELEVATION_DP * veil * tuning.depthIntensity,
            // Content settles downward as the device opens; the travel is small on purpose.
            translationYDp = MotionChannels.MAX_TRANSLATION_DP *
                (1f - easeInOutCubic(progress)) * tuning.offsetIntensity,
            expansion = lerp(MIN_EXPANSION, 1f, 1f - (1f - progress) * tuning.expansionIntensity),
            // Position-driven, same family as scale and offset: strongest closed, gone once flat.
            warpAmount = MotionChannels.MAX_WARP *
                (1f - easeInOutCubic(progress)) * tuning.warpIntensity,
        )
    }

    private const val MIN_EXPANSION = 0.9f

    private fun lerp(start: Float, stop: Float, fraction: Float): Float =
        start + (stop - start) * fraction.coerceIn(0f, 1f)

    private fun easeInOutCubic(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return if (x < 0.5f) 4f * x * x * x else 1f - pow3(-2f * x + 2f) / 2f
    }

    private fun pow3(value: Float): Float = value * value * value
}
