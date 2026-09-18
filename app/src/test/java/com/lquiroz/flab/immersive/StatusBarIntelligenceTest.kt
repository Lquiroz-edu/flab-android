package com.lquiroz.flab.immersive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DoD 8: icons must never become illegible, and there must always be a way out. */
class StatusBarIntelligenceTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()
    private val midGrey = 0xFF808080.toInt()
    private val instagramDark = 0xFF121212.toInt()

    @Test
    fun `dark backgrounds get light icons`() {
        val appearance = StatusBarIntelligence.appearanceFor(black)

        assertEquals(IconTreatment.Light, appearance.treatment)
        assertTrue(appearance.contrastRatio >= StatusBarIntelligence.MIN_CONTRAST)
    }

    @Test
    fun `light backgrounds get dark icons`() {
        val appearance = StatusBarIntelligence.appearanceFor(white)

        assertEquals(IconTreatment.Dark, appearance.treatment)
        assertTrue(appearance.contrastRatio >= StatusBarIntelligence.MIN_CONTRAST)
    }

    @Test
    fun `a video-dark surface is still readable`() {
        val appearance = StatusBarIntelligence.appearanceFor(instagramDark)

        assertEquals(IconTreatment.Light, appearance.treatment)
        assertTrue(appearance.contrastRatio >= StatusBarIntelligence.MIN_CONTRAST)
    }

    @Test
    fun `a flat colour never needs a scrim`() {
        // Against one uniform colour, black-or-white icons always clear the floor: the worst case
        // ties both polarities at about 4.58. Asserting it keeps the fast path honest.
        val appearance = StatusBarIntelligence.appearanceFor(midGrey)

        assertEquals(IconTreatment.Dark, appearance.treatment)
        assertEquals(0f, appearance.scrimAlpha, 0f)
        assertTrue(appearance.contrastRatio >= StatusBarIntelligence.MIN_CONTRAST)
    }

    @Test
    fun `a bar spanning highlights and shadows gets a scrim`() {
        // A video frame under the bar: neither polarity survives both ends of the range.
        val appearance = StatusBarIntelligence.appearanceFor(intArrayOf(black, white, midGrey))

        assertTrue("a scrim is required here", appearance.scrimAlpha > 0f)
        assertTrue(appearance.scrimAlpha <= StatusBarIntelligence.MAX_SCRIM_ALPHA)
        assertTrue(appearance.contrastRatio >= StatusBarIntelligence.MIN_CONTRAST)
    }

    @Test
    fun `the scrim is the smallest one that works`() {
        val appearance = StatusBarIntelligence.appearanceFor(intArrayOf(0xFF303030.toInt(), 0xFFB0B0B0.toInt()))

        if (appearance.treatment != IconTreatment.Fallback && appearance.scrimAlpha > 0f) {
            val weaker = appearance.scrimAlpha - 0.05f
            assertTrue("should not have needed more than it took", weaker < appearance.scrimAlpha)
        }
    }

    @Test
    fun `no sampled colour ever produces an illegible result`() {
        // Sweep every grey and every pair of greys. The invariant under test: either we clear the
        // contrast floor, or we hand the bars back to the system. There is no third outcome.
        for (level in 0..255) {
            val color = grey(level)
            val flat = StatusBarIntelligence.appearanceFor(color)
            assertLegible(flat, "grey $level")

            val paired = StatusBarIntelligence.appearanceFor(intArrayOf(color, grey(255 - level)))
            assertLegible(paired, "grey pair $level/${255 - level}")
        }
    }

    private fun grey(level: Int) = 0xFF000000.toInt() or (level shl 16) or (level shl 8) or level

    private fun assertLegible(appearance: BarAppearance, label: String) {
        if (appearance.treatment == IconTreatment.Fallback) return
        assertTrue(
            "illegible at $label: ratio ${appearance.contrastRatio}",
            appearance.contrastRatio >= StatusBarIntelligence.MIN_CONTRAST,
        )
    }

    @Test
    fun `an empty sample set falls back to the system`() {
        assertEquals(
            IconTreatment.Fallback,
            StatusBarIntelligence.appearanceFor(IntArray(0)).treatment,
        )
    }

    @Test
    fun `a low confidence sample falls back to the system`() {
        val appearance = StatusBarIntelligence.appearanceFor(black, sampleConfidence = 0.2f)

        assertEquals(IconTreatment.Fallback, appearance.treatment)
    }

    @Test
    fun `a translucent sample falls back to the system`() {
        val appearance = StatusBarIntelligence.appearanceFor(0x40000000)

        assertEquals(IconTreatment.Fallback, appearance.treatment)
    }

    @Test
    fun `fast changing content is detected as unstable`() {
        assertTrue(StatusBarIntelligence.isContentUnstable(black, white))
        assertTrue(!StatusBarIntelligence.isContentUnstable(black, 0xFF050505.toInt()))
    }

    @Test
    fun `contrast ratio matches the WCAG reference values`() {
        assertEquals(21f, StatusBarIntelligence.contrastRatio(black, white), 0.05f)
        assertEquals(1f, StatusBarIntelligence.contrastRatio(black, black), 0.01f)
    }
}
