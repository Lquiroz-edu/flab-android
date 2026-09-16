package com.lquiroz.flab.core

enum class FoldPosture { CLOSED, PARTIAL, OPEN, UNKNOWN }
enum class SensorMode { CONTINUOUS, POSTURE_FALLBACK, UNAVAILABLE }
enum class VisualProfile { BALANCED, SMOOTH, MINIMAL, BATTERY, CUSTOM }

data class FLabState(
    val enabled: Boolean = true,
    val foldPosture: FoldPosture = FoldPosture.UNKNOWN,
    val hingeAngle: Float? = null,
    val hingeVelocityDegPerSecond: Float = 0f,
    val foldProgress: Float = 1f,
    val sensorMode: SensorMode = SensorMode.UNAVAILABLE,
    val orientation: String = "Unknown",
    val windowWidth: Int = 0,
    val windowHeight: Int = 0,
    val activeDisplay: Int = 0,
    val foregroundPackage: String? = null,
    val accessibilityConnected: Boolean = false,
    val activeModules: Set<String> = emptySet(),
    val profile: VisualProfile = VisualProfile.BALANCED,
    val powerSave: Boolean = false,
    val lastModuleError: String? = null,
    val lastEvent: String = "Core initialized",
)

data class MotionReading(
    val angle: Float,
    val target: Float,
    val posture: FoldPosture,
    val mode: SensorMode,
    val direct: Boolean,
)

/** Pure policy used by the Android Core and covered by local unit tests. */
class FoldMotionModel {
    private val intermediateAngles = linkedSetOf<Int>()

    fun read(rawAngle: Float): MotionReading? {
        if (!rawAngle.isFinite() || rawAngle !in 0f..180f) return null
        val angle = rawAngle.coerceIn(0f, 180f)
        if (angle in 4f..176f && kotlin.math.abs(angle - 90f) > 2f) {
            intermediateAngles += angle.toInt()
            while (intermediateAngles.size > 12) intermediateAngles.remove(intermediateAngles.first())
        }
        val continuous = intermediateAngles.size >= 3
        val posture = when {
            angle <= 4f -> FoldPosture.CLOSED
            angle >= 176f -> FoldPosture.OPEN
            else -> FoldPosture.PARTIAL
        }
        return MotionReading(
            angle = angle,
            target = angle / 180f,
            posture = posture,
            mode = if (continuous) SensorMode.CONTINUOUS else SensorMode.POSTURE_FALLBACK,
            direct = continuous,
        )
    }
}
