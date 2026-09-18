package com.lquiroz.flab.immersive

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Which way the system bar icons should be drawn.
 *
 * [Fallback] is not a third style — it means "stop asking, hand the bars back to the system".
 * DoD 8 requires that exit to exist and to be automatic.
 */
enum class IconTreatment(val displayName: String) {
    Light("Light icons"),
    Dark("Dark icons"),
    Fallback("System default"),
}

data class BarAppearance(
    val treatment: IconTreatment,
    /** Contrast ratio achieved, for Diagnostics. `0f` when falling back. */
    val contrastRatio: Float,
    /** A scrim to add behind the icons when neither polarity is legible on its own. */
    val scrimAlpha: Float = 0f,
)

/**
 * Chooses a legible system bar treatment for a given background (DoD 8).
 *
 * Works in sRGB relative luminance and WCAG contrast ratios, which is a reasonable proxy for
 * "can I read this" and, more usefully, is a number that can be asserted on in a test.
 *
 * The decision has three outcomes, in order:
 *
 *  1. One of the two icon polarities clears [MIN_CONTRAST] against every sample — use it.
 *  2. Neither does, but adding a modest scrim gets the better one there — use it with the scrim.
 *  3. Not even a scrim at [MAX_SCRIM_ALPHA] helps, or the background is not measurable — fall
 *     back to the system. Illegible icons are a bug (DoD 30), a missing effect is not.
 *
 * ### Why sampling a region matters
 *
 * Against a *single* colour, step 2 is unreachable and step 1 always succeeds: the worst case is a
 * background at luminance 0.179, where both polarities tie at a ratio of 4.58, comfortably above
 * the floor. Black-or-white icons on a flat colour are simply always readable.
 *
 * The case that actually breaks is a background that is not flat — a video frame, a photo, a
 * gradient — where the bar spans a bright region and a dark one at once. Light icons vanish into
 * the highlights, dark icons vanish into the shadows, and no single polarity works. That is what
 * the scrim is for, and it is why the real entry point takes a set of samples rather than a colour.
 *
 * All colours are ARGB ints, as produced by `Color.toArgb()`.
 */
object StatusBarIntelligence {

    /** WCAG AA for large text. Icons are glyph-sized, so this is the floor, not the target. */
    const val MIN_CONTRAST = 3f
    const val MAX_SCRIM_ALPHA = 0.45f
    private const val SCRIM_STEP = 0.05f

    private const val LIGHT_ICON = 0xFFFFFFFF.toInt()
    private const val DARK_ICON = 0xFF000000.toInt()

    /**
     * @param backgroundArgb a single flat colour sampled from behind the bar.
     * @param sampleConfidence how much the sample is trusted, `0f..1f`. Fast-changing content
     *   samples poorly, so the caller lowers this and the bars are left alone.
     */
    fun appearanceFor(backgroundArgb: Int, sampleConfidence: Float = 1f): BarAppearance =
        appearanceFor(intArrayOf(backgroundArgb), sampleConfidence)

    /**
     * @param samples colours sampled across the width of the bar. A treatment must be legible
     *   against all of them, not against their average — averaging a black-and-white frame gives
     *   grey, which is legible, while the real bar is not.
     */
    fun appearanceFor(samples: IntArray, sampleConfidence: Float = 1f): BarAppearance {
        if (samples.isEmpty()) return BarAppearance(IconTreatment.Fallback, 0f)
        if (sampleConfidence < MIN_SAMPLE_CONFIDENCE) {
            return BarAppearance(IconTreatment.Fallback, 0f)
        }
        if (samples.any { alpha(it) < MIN_BACKGROUND_ALPHA }) {
            // A translucent sample tells us nothing about what will actually be behind the icons.
            return BarAppearance(IconTreatment.Fallback, 0f)
        }

        val lightContrast = samples.minOf { contrastRatio(LIGHT_ICON, it) }
        val darkContrast = samples.minOf { contrastRatio(DARK_ICON, it) }

        val best = if (lightContrast >= darkContrast) IconTreatment.Light else IconTreatment.Dark
        val bestContrast = max(lightContrast, darkContrast)
        if (bestContrast >= MIN_CONTRAST) {
            return BarAppearance(best, bestContrast)
        }

        // The bar spans too wide a luminance range for either polarity. Push the whole range away
        // from the icon colour with the smallest scrim that works, rather than jumping to the
        // maximum, so the treatment stays as light as it can be.
        val iconColor = if (best == IconTreatment.Light) LIGHT_ICON else DARK_ICON
        val scrimTowards = if (best == IconTreatment.Light) DARK_ICON else LIGHT_ICON
        // Stepped over an integer index rather than by accumulating a float, and clamped on the way
        // out: MAX_SCRIM_ALPHA is a promise to the caller, and float arithmetic must not be able to
        // return a value a hair above it.
        val steps = Math.round(MAX_SCRIM_ALPHA / SCRIM_STEP)
        for (step in 1..steps) {
            val scrim = (step * SCRIM_STEP).coerceAtMost(MAX_SCRIM_ALPHA)
            val ratio = samples.minOf { contrastRatio(iconColor, blend(it, scrimTowards, scrim)) }
            if (ratio >= MIN_CONTRAST) {
                return BarAppearance(best, ratio, scrim)
            }
        }
        return BarAppearance(IconTreatment.Fallback, 0f)
    }

    /**
     * True when two consecutive samples differ enough that the content is probably moving.
     *
     * Retreating here is what stops the bars flickering over a video, which DoD 30 lists as a
     * blocking artifact.
     */
    fun isContentUnstable(previousArgb: Int, currentArgb: Int): Boolean =
        abs(luminance(previousArgb) - luminance(currentArgb)) > STABILITY_THRESHOLD

    /** WCAG 2.1 contrast ratio between two opaque colours. */
    fun contrastRatio(foregroundArgb: Int, backgroundArgb: Int): Float {
        val a = luminance(foregroundArgb)
        val b = luminance(backgroundArgb)
        val lighter = max(a, b)
        val darker = min(a, b)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /** sRGB relative luminance, `0f..1f`. */
    fun luminance(argb: Int): Float {
        val r = channelLuminance(red(argb))
        val g = channelLuminance(green(argb))
        val b = channelLuminance(blue(argb))
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    private fun channelLuminance(component: Int): Float {
        val c = component / 255f
        return if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    }

    /** Composites [overlay] at [amount] opacity over [base]; both treated as opaque. */
    private fun blend(base: Int, overlay: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        fun mix(from: Int, to: Int) = (from + (to - from) * a).toInt().coerceIn(0, 255)
        return argb(
            255,
            mix(red(base), red(overlay)),
            mix(green(base), green(overlay)),
            mix(blue(base), blue(overlay)),
        )
    }

    private fun alpha(argb: Int) = (argb ushr 24) and 0xFF
    private fun red(argb: Int) = (argb shr 16) and 0xFF
    private fun green(argb: Int) = (argb shr 8) and 0xFF
    private fun blue(argb: Int) = argb and 0xFF
    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b

    private const val MIN_SAMPLE_CONFIDENCE = 0.6f
    private const val MIN_BACKGROUND_ALPHA = 200
    private const val STABILITY_THRESHOLD = 0.18f
}
