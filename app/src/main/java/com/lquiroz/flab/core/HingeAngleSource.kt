package com.lquiroz.flab.core

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Reads `Sensor.TYPE_HINGE_ANGLE`, the one genuinely continuous fold signal a normal app gets.
 *
 * This is what makes DoD 3 implementable rather than aspirational. `FoldingFeature.State` gives
 * three values; the hinge sensor gives degrees, at sensor rate, on Samsung foldables from
 * Android 11 onwards. When it is missing the engine falls back to posture events and the
 * interpolator does more of the work — the motion is coarser, but it is still evidence-driven.
 *
 * Registration is owned by the collector: the flow registers on collect and unregisters on
 * cancellation, and the engine only collects while a transition is in flight. That is how DoD 22's
 * "no continuous polling" is satisfied — an idle F/LAB holds no sensor listener at all.
 */
class HingeAngleSource(private val context: Context) {

    private val sensorManager: SensorManager? =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val hingeSensor: Sensor? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            null
        } else {
            sensorManager?.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        }
    }

    /** True when this device reports a hinge angle. Cheap; safe to call from the UI. */
    val isAvailable: Boolean get() = hingeSensor != null

    /** The maximum angle the device reports, used to normalise. Most foldables report 180. */
    val maxAngleDegrees: Float get() = hingeSensor?.maximumRange?.takeIf { it > 0f } ?: DEFAULT_MAX_ANGLE

    /**
     * Emits hinge angles in degrees while collected.
     *
     * Uses `SENSOR_DELAY_GAME` rather than `FASTEST`: at 120 Hz the extra samples from FASTEST do
     * not survive into a frame, and they cost power for nothing (DoD 25).
     *
     * Conflated, because this is a position: if the consumer is busy, the newest angle is the only
     * one worth having, and a queue that backs up would make the motion lag the hand.
     */
    fun angles(): Flow<Float> = callbackFlow {
        val manager = sensorManager
        val sensor = hingeSensor
        if (manager == null || sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                event.values.firstOrNull()?.let { trySend(it) }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()

    /** Normalises a hinge angle to the `0f..1f` progress the motion engine speaks. */
    fun normalise(angleDegrees: Float): Float =
        (angleDegrees / maxAngleDegrees).coerceIn(0f, 1f)

    private companion object {
        const val DEFAULT_MAX_ANGLE = 180f
    }
}
