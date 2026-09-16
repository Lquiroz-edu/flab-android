package com.lquiroz.flab.motion

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.app.KeyguardManager
import android.content.*
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.*
import android.hardware.display.DisplayManager
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

object ModuleState {
    val connected = mutableStateOf(false)
    val status = mutableStateOf("Permiso de Accesibilidad pendiente")
    val sensor = mutableStateOf("Se comprobará al activar")
}

class FoldService : AccessibilityService(), SensorEventListener,
    SharedPreferences.OnSharedPreferenceChangeListener, DisplayManager.DisplayListener {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var prefs: SharedPreferences
    private lateinit var sensors: SensorManager
    private lateinit var displays: DisplayManager
    private var registered = false
    private var trigger = FoldTrigger()
    private var overlay: FrostView? = null
    private var manager: WindowManager? = null
    private var animation: ValueAnimator? = null
    private var generation = 0
    private var lastCapture = -1000L
    private var size = ""
    private var active = false
    private val pending = Runnable { capture() }
    private val watchdog = Runnable { clearOverlay() }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) stop()
            else sync()
        }
    }

    override fun onServiceConnected() {
        prefs = getSharedPreferences("motion", MODE_PRIVATE)
        sensors = getSystemService(SensorManager::class.java)
        displays = getSystemService(DisplayManager::class.java)
        prefs.registerOnSharedPreferenceChangeListener(this)
        displays.registerDisplayListener(this, handler)
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        ModuleState.connected.value = true
        size = displaySize()
        sync()
    }

    private fun canRun() = active && prefs.getBoolean("enabled", false) &&
        getSystemService(PowerManager::class.java).isInteractive &&
        !getSystemService(KeyguardManager::class.java).isKeyguardLocked &&
        ValueAnimator.areAnimatorsEnabled()

    private fun sync() {
        stop()
        if (!prefs.getBoolean("enabled", false)) {
            ModuleState.status.value = "Movimiento desactivado"
            return
        }
        if (!getSystemService(PowerManager::class.java).isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            ModuleState.status.value = "En pausa con la pantalla bloqueada"
            return
        }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            ModuleState.status.value = "En pausa: animaciones desactivadas en Android"
            return
        }
        active = true
        trigger = FoldTrigger()
        val hinge = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE, false)
            ?: sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE, true)
        registered = hinge != null && sensors.registerListener(this, hinge, 20_000)
        ModuleState.sensor.value = if (registered) "Modo temporizado · comprobando lecturas" else
            "Sin sensor público · activación por cambio de pantalla"
        ModuleState.status.value = "Activado · vuelve a One UI y pliega el teléfono"
        size = displaySize()
    }

    private fun stop() {
        active = false
        if (::sensors.isInitialized) sensors.unregisterListener(this)
        registered = false
        generation++
        handler.removeCallbacks(pending)
        clearOverlay()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!canRun()) return
        val angle = event.values.firstOrNull() ?: return
        val changed = trigger.accept(angle, SystemClock.elapsedRealtime())
        ModuleState.sensor.value = if (trigger.hasIntermediateReadings)
            "Lecturas intermedias detectadas · efecto temporizado V1"
        else "Modo temporizado · sin precisión continua confirmada"
        if (changed) schedule(35)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun displaySize(): String {
        val mode = displays.getDisplay(Display.DEFAULT_DISPLAY)?.mode ?: return ""
        return "${mode.physicalWidth}x${mode.physicalHeight}"
    }
    override fun onDisplayChanged(displayId: Int) {
        if (displayId != Display.DEFAULT_DISPLAY) return
        val next = displaySize()
        if (size == next) return
        size = next
        generation++
        clearOverlay()
        if (canRun()) schedule(180)
    }
    override fun onDisplayAdded(displayId: Int) = Unit
    override fun onDisplayRemoved(displayId: Int) = Unit
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::displays.isInitialized) return
        onDisplayChanged(Display.DEFAULT_DISPLAY)
    }

    private fun schedule(delay: Long) {
        if (!canRun()) return
        handler.removeCallbacks(pending)
        // Always allow the previous overlay to disappear before taking another screenshot.
        val wait = (lastCapture + 850L - SystemClock.elapsedRealtime()).coerceAtLeast(delay)
        handler.postDelayed(pending, wait)
    }

    private fun capture() {
        if (!canRun() || overlay != null) return
        val token = ++generation
        val capturedSize = displaySize()
        lastCapture = SystemClock.elapsedRealtime()
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    try {
                        if (token != generation || !canRun() || capturedSize != displaySize()) return
                        val wrapped = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace) ?: return
                        val bitmap = try { wrapped.copy(Bitmap.Config.ARGB_8888, false) }
                            finally { wrapped.recycle() }
                        if (bitmap == null) return
                        // A newly powered panel can still be black. Skip it rather than hide the UI.
                        if (isDark(bitmap)) { bitmap.recycle(); return }
                        show(bitmap)
                    } catch (_: RuntimeException) {
                        clearOverlay()
                        ModuleState.status.value = "Efecto omitido: esta pantalla no pudo dibujarse"
                    } finally { buffer.close() }
                }
                override fun onFailure(errorCode: Int) {
                    if (token != generation) return
                    ModuleState.status.value = "Pantalla no capturable ($errorCode); efecto omitido"
                }
            })
        } catch (_: RuntimeException) {
            ModuleState.status.value = "Captura no disponible; efecto omitido"
        }
    }

    private fun isDark(bitmap: Bitmap): Boolean {
        var light = 0
        for (y in 1..6) for (x in 1..6) {
            val c = bitmap.getPixel(bitmap.width * x / 7, bitmap.height * y / 7)
            if (Color.red(c) + Color.green(c) + Color.blue(c) > 45) light++
        }
        return light < 3
    }

    private fun show(bitmap: Bitmap) {
        clearOverlay()
        val display = displays.getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val windowContext = createDisplayContext(display).createWindowContext(
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        val wm = windowContext.getSystemService(WindowManager::class.java)
        val view = FrostView(windowContext, bitmap).apply {
            strength = prefs.getFloat("strength", 0.7f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        manager = wm
        overlay = view
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT)
        params.setFitInsetsTypes(0)
        params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        wm.addView(view, params)
        handler.postDelayed(watchdog, 900)
        animation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 620
            interpolator = android.view.animation.LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                view.amount = kotlin.math.sin(Math.PI * t).toFloat()
                view.alpha = 1f - ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                if (t >= 1f) clearOverlay()
            }
            start()
        }
        ModuleState.status.value = "Activo · última transición aplicada"
    }

    private fun clearOverlay() {
        handler.removeCallbacks(watchdog)
        animation?.removeAllUpdateListeners()
        animation?.cancel()
        animation = null
        overlay?.let { view -> runCatching { manager?.removeViewImmediate(view) } }
        overlay = null
        manager = null
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "enabled") sync()
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { stop() }
    override fun onDestroy() {
        stop()
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (::displays.isInitialized) displays.unregisterDisplayListener(this)
        runCatching { unregisterReceiver(screenReceiver) }
        ModuleState.connected.value = false
        ModuleState.status.value = "Servicio desconectado"
        super.onDestroy()
    }
}
