package com.lquiroz.flab

import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import com.lquiroz.flab.ui.home.DuoSurface
import com.lquiroz.flab.ui.home.SurfaceRole
import com.lquiroz.flab.ui.theme.FLabTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalWindowApi::class)
class MainActivity : ComponentActivity(), WindowAreaPresentationSessionCallback, SensorEventListener {
    private lateinit var windowAreaController: WindowAreaController
    private lateinit var sensorManager: SensorManager

    private var rearArea: WindowAreaInfo? = null
    private var presentation: WindowAreaSessionPresenter? = null
    private var requestingPresentation = false
    private var nextClaimAtMs = 0L
    private var motionJob: Job? = null

    private var unfoldProgress by mutableFloatStateOf(0f)
    private var rawHingeAngle by mutableStateOf<Float?>(null)
    private var coverWidthMm by mutableFloatStateOf(DEFAULT_COVER_WIDTH_MM)
    private var dualScreenStatus by mutableStateOf("Esperando segunda pantalla")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        enableEdgeToEdge()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        windowAreaController = WindowAreaController.getOrCreate()

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                windowAreaController.windowAreaInfos.collect { areas ->
                    rearArea = areas.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
                    val capability = rearArea?.getCapability(
                        WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA,
                    )?.status
                    dualScreenStatus = when {
                        presentation != null -> "Dos pantallas activas"
                        rearArea == null -> "One UI aún no expone la segunda pantalla"
                        else -> "Segunda pantalla: $capability"
                    }
                    claimSecondDisplay()
                }
            }
        }

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    claimSecondDisplay()
                    delay(CLAIM_INTERVAL_MS)
                }
            }
        }

        setContent {
            FLabTheme {
                DuoSurface(
                    role = if (isInnerDisplay()) SurfaceRole.Inner else SurfaceRole.Cover,
                    unfoldProgress = unfoldProgress,
                    rawHingeAngle = rawHingeAngle,
                    coverWidthMm = coverWidthMm,
                    status = dualScreenStatus,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val hinge = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        if (hinge != null) {
            sensorManager.registerListener(this, hinge, SensorManager.SENSOR_DELAY_GAME)
        } else {
            animateTo(if (isInnerDisplay()) 1f else 0f)
        }
    }

    override fun onStop() {
        sensorManager.unregisterListener(this)
        super.onStop()
    }

    override fun onDestroy() {
        motionJob?.cancel()
        runCatching { presentation?.close() }
        presentation = null
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HINGE_ANGLE) return
        val angle = event.values.firstOrNull()?.coerceIn(0f, 180f) ?: return
        rawHingeAngle = angle
        val target = angle / 180f

        if (kotlin.math.abs(target - unfoldProgress) < 0.08f) {
            motionJob?.cancel()
            unfoldProgress = target
        } else {
            animateTo(target)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (rawHingeAngle == null) animateTo(if (isInnerDisplay()) 1f else 0f)
        claimSecondDisplay()
    }

    private fun animateTo(target: Float) {
        motionJob?.cancel()
        val start = unfoldProgress
        motionJob = lifecycleScope.launch {
            val startTime = SystemClock.uptimeMillis()
            do {
                val elapsed = (SystemClock.uptimeMillis() - startTime).toFloat()
                val linear = (elapsed / MOTION_DURATION_MS).coerceIn(0f, 1f)
                val eased = linear * linear * (3f - 2f * linear)
                unfoldProgress = start + (target - start) * eased
                delay(16)
            } while (linear < 1f)
            unfoldProgress = target
        }
    }

    private fun claimSecondDisplay() {
        if (presentation != null || requestingPresentation) return
        if (SystemClock.elapsedRealtime() < nextClaimAtMs) return
        val token = rearArea?.token ?: return

        requestingPresentation = true
        runCatching {
            windowAreaController.presentContentOnWindowArea(
                token,
                this,
                ContextCompat.getMainExecutor(this),
                this,
            )
        }.onFailure {
            requestingPresentation = false
            dualScreenStatus = "One UI rechazó la segunda pantalla"
            nextClaimAtMs = SystemClock.elapsedRealtime() + CLAIM_BACKOFF_MS
        }
    }

    override fun onSessionStarted(session: WindowAreaSessionPresenter) {
        requestingPresentation = false
        presentation = session
        nextClaimAtMs = 0L

        val metrics = session.context.resources.displayMetrics
        if (metrics.xdpi > 0f) {
            val measured = metrics.widthPixels / (metrics.xdpi / 25.4f)
            if (measured in 45f..100f) coverWidthMm = measured
        }
        dualScreenStatus = "Dos pantallas activas"

        val role = if (metrics.widthPixels > INNER_DISPLAY_MIN_WIDTH_PX) {
            SurfaceRole.Inner
        } else {
            SurfaceRole.Cover
        }
        val view = ComposeView(session.context).apply {
            keepScreenOn = true
            setBackgroundColor(Color.BLACK)
            setViewTreeLifecycleOwner(this@MainActivity)
            setViewTreeViewModelStoreOwner(this@MainActivity)
            setViewTreeSavedStateRegistryOwner(this@MainActivity)
            setContent {
                FLabTheme {
                    DuoSurface(
                        role = role,
                        unfoldProgress = unfoldProgress,
                        rawHingeAngle = rawHingeAngle,
                        coverWidthMm = coverWidthMm,
                        status = dualScreenStatus,
                    )
                }
            }
        }
        session.setContentView(view)
    }

    override fun onSessionEnded(t: Throwable?) {
        requestingPresentation = false
        presentation = null
        dualScreenStatus = if (t == null) {
            "Segunda pantalla liberada"
        } else {
            "One UI cerró la segunda pantalla"
        }
        nextClaimAtMs = SystemClock.elapsedRealtime() + CLAIM_BACKOFF_MS
    }

    override fun onContainerVisibilityChanged(isVisible: Boolean) = Unit

    private fun isInnerDisplay(): Boolean {
        val mode = display?.mode ?: return resources.configuration.screenWidthDp >= 600
        return maxOf(mode.physicalWidth, mode.physicalHeight) > INNER_DISPLAY_LONG_EDGE_PX &&
            minOf(mode.physicalWidth, mode.physicalHeight) > INNER_DISPLAY_SHORT_EDGE_PX
    }

    private companion object {
        const val DEFAULT_COVER_WIDTH_MM = 64f
        const val INNER_DISPLAY_MIN_WIDTH_PX = 1600
        const val INNER_DISPLAY_LONG_EDGE_PX = 2100
        const val INNER_DISPLAY_SHORT_EDGE_PX = 1500
        const val CLAIM_INTERVAL_MS = 300L
        const val CLAIM_BACKOFF_MS = 900L
        const val MOTION_DURATION_MS = 670f
    }
}
