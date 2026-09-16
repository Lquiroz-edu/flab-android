package com.lquiroz.flab.fold

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberHingeAngle(): State<Float?> {
    val context = LocalContext.current
    val angle = remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val hingeSensor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        } else {
            null
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                angle.value = event.values.firstOrNull()?.coerceIn(0f, 180f)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (hingeSensor != null) {
            sensorManager.registerListener(listener, hingeSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    return angle
}
