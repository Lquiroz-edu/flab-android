package com.lquiroz.flab.motion

import kotlin.math.abs

/**
 * Decides when a new visual snapshot is useful. Sensor samples still drive the
 * animation continuously; this class only rate-limits the expensive capture.
 */
class FoldTrigger(
    private val endpointDegrees: Float = 8f,
    private val minCaptureIntervalMs: Long = 333L,
) {
    private enum class Zone { CLOSED, MOVING, OPEN }

    private var initialized = false
    private var lastZone = Zone.OPEN
    private var lastCaptureMs = Long.MIN_VALUE
    private val intermediate = linkedSetOf<Int>()

    val hasIntermediateReadings: Boolean
        get() = intermediate.size >= 3

    fun accept(rawAngle: Float, nowMs: Long): Boolean {
        if (!rawAngle.isFinite() || rawAngle !in 0f..180f) return false
        val angle = rawAngle.coerceIn(0f, 180f)
        if (angle > endpointDegrees && angle < 180f - endpointDegrees) {
            intermediate += angle.toInt()
            while (intermediate.size > 12) intermediate.remove(intermediate.first())
        }
        val zone = when {
            angle <= endpointDegrees -> Zone.CLOSED
            angle >= 180f - endpointDegrees -> Zone.OPEN
            else -> Zone.MOVING
        }
        if (!initialized) {
            initialized = true
            lastZone = zone
            return false
        }
        if (zone == lastZone) return false
        lastZone = zone
        if (lastCaptureMs != Long.MIN_VALUE && nowMs - lastCaptureMs < minCaptureIntervalMs) return false
        lastCaptureMs = nowMs
        return true
    }
}

/** Pure mappings shared by renderers and covered by local tests. */
object FoldMotionCurve {
    const val PANEL_SWAP_PROGRESS = 0.12f

    fun coverTilt(progress: Float, strength: Float = 1f): Float =
        smooth(progress.coerceIn(0f, PANEL_SWAP_PROGRESS) / PANEL_SWAP_PROGRESS) * 42f * strength

    fun innerTilt(progress: Float, strength: Float = 1f): Float {
        val normalized = ((progress - PANEL_SWAP_PROGRESS) / (1f - PANEL_SWAP_PROGRESS)).coerceIn(0f, 1f)
        return (1f - smooth(normalized)) * 42f * strength
    }

    fun velocityEnergy(degreesPerSecond: Float): Float =
        (abs(degreesPerSecond) / 420f).coerceIn(0f, 1f)

    private fun smooth(value: Float): Float = value * value * (3f - 2f * value)
}
