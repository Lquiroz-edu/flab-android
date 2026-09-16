package com.lquiroz.flab.motion

/** Posture changes trigger a timed effect; this does not reconstruct missing angles. */
class FoldTrigger {
    private var posture: Int? = null
    private var lastTrigger = Long.MIN_VALUE
    private val intermediate = mutableSetOf<Int>()
    val hasIntermediateReadings get() = intermediate.size >= 5

    fun accept(angle: Float, now: Long): Boolean {
        if (!angle.isFinite() || angle !in 0f..180f) return false
        if (angle in 5f..175f && kotlin.math.abs(angle - 90f) > 3f) {
            if (intermediate.size < 5) intermediate.add(angle.toInt())
        }
        val next = when {
            angle <= 5f -> 0
            angle >= 175f -> 2
            else -> 1
        }
        val previous = posture
        posture = next
        if (previous == null || next == previous) return false
        if (lastTrigger != Long.MIN_VALUE && now - lastTrigger < 450L) return false
        lastTrigger = now
        return true
    }
}
