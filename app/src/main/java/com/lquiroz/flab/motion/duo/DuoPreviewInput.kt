package com.lquiroz.flab.motion.duo

import kotlin.math.abs
import kotlin.math.roundToInt

/** Manual angles are simulation. Public-sensor availability alone proves no continuity. */
internal class DuoPreviewInput {
    private val intermediate = mutableSetOf<Int>()
    var samples = 0; private set
    var rawAngle: Float? = null; private set
    val continuousObserved: Boolean get() = intermediate.size >= 3

    fun sample(degrees: Float): Boolean {
        if (!degrees.isFinite() || degrees !in 0f..180f) return false
        samples++
        rawAngle = degrees
        if (listOf(0f, 90f, 180f).none { abs(degrees - it) < 2f }) {
            intermediate += degrees.roundToInt()
        }
        return true
    }

    companion object {
        /** Same default inner/cover shade mapping as the supplied DuoShadeCurve. */
        fun progress(angle: Float, inner: Boolean): Float {
            if (!angle.isFinite()) return 0f
            return (if (inner) (172f - angle) / 82f else angle / 110f).coerceIn(0f, 1f)
        }
    }
}
