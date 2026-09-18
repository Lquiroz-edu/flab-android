package com.lquiroz.flab.continuity

import com.lquiroz.flab.core.ActiveDisplay
import com.lquiroz.flab.core.WindowSize

/**
 * The kinds of discontinuity F/LAB tries to cover (DoD 4).
 *
 * Android tells us about some of these directly and lets us infer others. The ones it does not
 * expose to a normal app at all — another app's Activity being recreated, another app being
 * relaunched — are absent on purpose rather than guessed at.
 */
enum class ContinuityTrigger(val displayName: String) {
    DisplaySwitch("Display switch"),
    WindowResize("Window resize"),
    ConfigurationChange("Configuration change"),
    OrientationChange("Orientation change"),
    Recreation("Activity recreation"),
}

/** Phase of a continuity transition. */
enum class ContinuityPhase {
    /** Nothing happening. No layer, no frames. */
    Idle,

    /** The old content is being veiled, before the real relayout lands. */
    Veiling,

    /** The relayout is happening underneath. */
    Bridging,

    /** The new content is being revealed. */
    Revealing,
}

/**
 * State of an in-flight continuity transition.
 *
 * [veilAlpha] is deliberately capped below opaque: a transition that goes fully black is a black
 * flash, which DoD 30 lists as a blocking artifact. The point is to blur the seam between two
 * layouts, not to replace one with a hole.
 */
data class ContinuityFrame(
    val phase: ContinuityPhase,
    val veilAlpha: Float,
    val scale: Float,
    val elapsedMillis: Long,
) {
    val isActive: Boolean get() = phase != ContinuityPhase.Idle

    companion object {
        val Idle = ContinuityFrame(ContinuityPhase.Idle, 0f, 1f, 0L)
        const val MAX_VEIL_ALPHA = 0.55f
        const val MIN_SCALE = 0.985f
    }
}

/**
 * Bridges cover <-> inner display switches (DoD 4).
 *
 * The rule that shapes the whole design: **the visual layer must hide latency, never add it.**
 * So the transition is bounded by a budget rather than driven by a duration. When the new layout
 * arrives, [onContentReady] ends the transition immediately, even mid-veil. The budget is only a
 * backstop for the case where the content never reports ready, and it is short enough that a
 * stuck transition is over before the user could call it stuck (DoD 30's "overlay congelado").
 *
 * Pure and clock-injected, so the whole state machine is testable without a device.
 */
class ContinuityEngine(private val budgetMillis: Long = DEFAULT_BUDGET_MILLIS) {

    private var startedAtMillis: Long = 0L
    private var readyAtMillis: Long? = null
    private var trigger: ContinuityTrigger? = null
    private var active = false

    val activeTrigger: ContinuityTrigger? get() = trigger

    /**
     * Decides whether a change is worth covering at all.
     *
     * A resize of a few dp, or a "switch" that lands on the same display, is not a discontinuity
     * the user would have noticed — covering it would be F/LAB adding motion for its own sake,
     * which DoD 48 rules out.
     */
    fun shouldBridge(
        fromDisplay: ActiveDisplay,
        toDisplay: ActiveDisplay,
        fromSize: WindowSize,
        toSize: WindowSize,
    ): Boolean {
        if (fromDisplay != ActiveDisplay.Unknown && toDisplay != ActiveDisplay.Unknown &&
            fromDisplay != toDisplay
        ) {
            return true
        }
        if (fromSize == WindowSize.Unknown || toSize == WindowSize.Unknown) return false
        val widthDelta = kotlin.math.abs(toSize.widthDp - fromSize.widthDp)
        val heightDelta = kotlin.math.abs(toSize.heightDp - fromSize.heightDp)
        return widthDelta >= SIGNIFICANT_RESIZE_DP || heightDelta >= SIGNIFICANT_RESIZE_DP
    }

    /** Starts covering a transition. Does nothing if one is already in flight. */
    fun begin(trigger: ContinuityTrigger, nowMillis: Long) {
        if (active) return
        active = true
        this.trigger = trigger
        startedAtMillis = nowMillis
        readyAtMillis = null
    }

    /**
     * Signals that the new layout is on screen.
     *
     * The transition does not stop dead here — that would be a visible cut. It moves into
     * [ContinuityPhase.Revealing] and unveils over [REVEAL_MILLIS], which is short enough to stay
     * inside the budget in the common case.
     */
    fun onContentReady(nowMillis: Long) {
        if (!active || readyAtMillis != null) return
        readyAtMillis = nowMillis
    }

    /** Ends the transition immediately and drops the layer. Used by the kill switch and on pause. */
    fun cancel() {
        active = false
        trigger = null
        readyAtMillis = null
    }

    /** The frame to render at [nowMillis]. Returns [ContinuityFrame.Idle] when there is nothing to do. */
    fun frameAt(nowMillis: Long): ContinuityFrame {
        if (!active) return ContinuityFrame.Idle
        val elapsed = (nowMillis - startedAtMillis).coerceAtLeast(0L)

        val ready = readyAtMillis
        if (ready != null) {
            val sinceReady = (nowMillis - ready).coerceAtLeast(0L)
            if (sinceReady >= REVEAL_MILLIS) {
                cancel()
                return ContinuityFrame.Idle
            }
            val remaining = 1f - sinceReady / REVEAL_MILLIS.toFloat()
            return ContinuityFrame(
                phase = ContinuityPhase.Revealing,
                veilAlpha = ContinuityFrame.MAX_VEIL_ALPHA * ease(remaining),
                scale = lerp(ContinuityFrame.MIN_SCALE, 1f, 1f - ease(remaining)),
                elapsedMillis = elapsed,
            )
        }

        if (elapsed >= budgetMillis) {
            // The content never reported ready. Get out of the way rather than hold a stale layer.
            cancel()
            return ContinuityFrame.Idle
        }

        val veilMillis = (budgetMillis * VEIL_FRACTION).toLong().coerceAtLeast(1L)
        return if (elapsed < veilMillis) {
            val t = elapsed / veilMillis.toFloat()
            ContinuityFrame(
                phase = ContinuityPhase.Veiling,
                veilAlpha = ContinuityFrame.MAX_VEIL_ALPHA * ease(t),
                scale = lerp(1f, ContinuityFrame.MIN_SCALE, ease(t)),
                elapsedMillis = elapsed,
            )
        } else {
            ContinuityFrame(
                phase = ContinuityPhase.Bridging,
                veilAlpha = ContinuityFrame.MAX_VEIL_ALPHA,
                scale = ContinuityFrame.MIN_SCALE,
                elapsedMillis = elapsed,
            )
        }
    }

    private fun ease(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private fun lerp(start: Float, stop: Float, fraction: Float) =
        start + (stop - start) * fraction.coerceIn(0f, 1f)

    companion object {
        const val DEFAULT_BUDGET_MILLIS = 220L
        const val REVEAL_MILLIS = 90L
        const val SIGNIFICANT_RESIZE_DP = 120
        private const val VEIL_FRACTION = 0.35f
    }
}
