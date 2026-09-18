package com.lquiroz.flab.continuity

import com.lquiroz.flab.core.ActiveDisplay
import com.lquiroz.flab.core.WindowSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DoD 4 and DoD 30: cover the seam, hide latency, and never leave a layer behind. */
class ContinuityEngineTest {

    private val cover = WindowSize(360, 900)
    private val inner = WindowSize(840, 1000)

    @Test
    fun `a display switch is worth bridging`() {
        val engine = ContinuityEngine()

        assertTrue(engine.shouldBridge(ActiveDisplay.Cover, ActiveDisplay.Inner, cover, inner))
    }

    @Test
    fun `a trivial resize is not worth bridging`() {
        val engine = ContinuityEngine()

        assertFalse(
            "F/LAB must not add motion for its own sake",
            engine.shouldBridge(ActiveDisplay.Inner, ActiveDisplay.Inner, inner, inner.copy(widthDp = 850)),
        )
    }

    @Test
    fun `an unknown size is never bridged`() {
        val engine = ContinuityEngine()

        assertFalse(
            engine.shouldBridge(
                ActiveDisplay.Unknown,
                ActiveDisplay.Unknown,
                WindowSize.Unknown,
                inner,
            ),
        )
    }

    @Test
    fun `the veil never becomes opaque`() {
        val engine = ContinuityEngine(budgetMillis = 220L)
        engine.begin(ContinuityTrigger.DisplaySwitch, nowMillis = 0L)

        for (millis in 0..220 step 5) {
            val frame = engine.frameAt(millis.toLong())
            assertTrue(
                "veil reached ${frame.veilAlpha} at $millis ms",
                frame.veilAlpha <= ContinuityFrame.MAX_VEIL_ALPHA,
            )
        }
    }

    @Test
    fun `content arriving early ends the transition early`() {
        // The DoD 4 rule: the visual layer hides latency, it does not add any. If the relayout is
        // done at 40 ms, F/LAB must not keep a veil up for its full budget.
        val engine = ContinuityEngine(budgetMillis = 220L)
        engine.begin(ContinuityTrigger.DisplaySwitch, nowMillis = 0L)
        engine.frameAt(40L)
        engine.onContentReady(40L)

        val duringReveal = engine.frameAt(60L)
        assertEquals(ContinuityPhase.Revealing, duringReveal.phase)

        val afterReveal = engine.frameAt(40L + ContinuityEngine.REVEAL_MILLIS)
        assertFalse("the layer must be gone", afterReveal.isActive)
    }

    @Test
    fun `a transition that is never marked ready still ends`() {
        // DoD 30 lists a frozen overlay as a blocking artifact, so the budget is a hard stop.
        val engine = ContinuityEngine(budgetMillis = 220L)
        engine.begin(ContinuityTrigger.DisplaySwitch, nowMillis = 0L)

        assertTrue(engine.frameAt(100L).isActive)
        assertFalse("the budget must expire the layer", engine.frameAt(221L).isActive)
    }

    @Test
    fun `an idle engine renders nothing`() {
        val engine = ContinuityEngine()

        assertEquals(ContinuityFrame.Idle, engine.frameAt(1_000L))
    }

    @Test
    fun `beginning twice does not restart the transition`() {
        val engine = ContinuityEngine(budgetMillis = 220L)
        engine.begin(ContinuityTrigger.DisplaySwitch, nowMillis = 0L)
        engine.begin(ContinuityTrigger.WindowResize, nowMillis = 100L)

        assertEquals(ContinuityTrigger.DisplaySwitch, engine.activeTrigger)
        assertEquals(150L, engine.frameAt(150L).elapsedMillis)
    }

    @Test
    fun `cancel drops the layer immediately`() {
        val engine = ContinuityEngine()
        engine.begin(ContinuityTrigger.DisplaySwitch, nowMillis = 0L)
        engine.cancel()

        assertFalse(engine.frameAt(10L).isActive)
    }

    @Test
    fun `a full cover to inner and back cycle leaves nothing behind`() {
        // DoD 36's stress test in miniature: repeated switches must not accumulate state.
        val engine = ContinuityEngine(budgetMillis = 220L)
        var now = 0L

        repeat(50) {
            engine.begin(ContinuityTrigger.DisplaySwitch, now)
            now += 40L
            engine.frameAt(now)
            engine.onContentReady(now)
            now += ContinuityEngine.REVEAL_MILLIS
            val frame = engine.frameAt(now)
            assertFalse("cycle $it left a layer up", frame.isActive)
            now += 500L
        }

        assertEquals(ContinuityFrame.Idle, engine.frameAt(now))
    }

    @Test
    fun `scale stays subtle throughout`() {
        val engine = ContinuityEngine(budgetMillis = 220L)
        engine.begin(ContinuityTrigger.DisplaySwitch, nowMillis = 0L)

        for (millis in 0..220 step 5) {
            val frame = engine.frameAt(millis.toLong())
            assertTrue(
                "scale ${frame.scale} at $millis ms would read as a zoom",
                frame.scale in ContinuityFrame.MIN_SCALE..1f,
            )
        }
    }
}
