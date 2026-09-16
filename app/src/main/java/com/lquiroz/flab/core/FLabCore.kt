package com.lquiroz.flab.core

import android.animation.ValueAnimator
import android.content.*
import android.content.res.Configuration
import android.hardware.*
import android.hardware.display.DisplayManager
import android.os.*
import android.view.Display
import android.view.animation.AccelerateDecelerateInterpolator
import com.lquiroz.flab.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArraySet

object FLabCore : SensorEventListener, DisplayManager.DisplayListener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    fun interface Listener { fun onState(state: FLabState) }

    private lateinit var app: Context
    private lateinit var sensors: SensorManager
    private lateinit var displays: DisplayManager
    private lateinit var power: PowerManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private val motion = FoldMotionModel()
    private val listeners = CopyOnWriteArraySet<Listener>()
    private val clients = linkedMapOf<String, Boolean>()
    private var sensor: Sensor? = null
    private var listening = false
    private var fallbackAnimation: ValueAnimator? = null
    private var started = false

    private val mutableState = MutableStateFlow(FLabState())
    val state: StateFlow<FLabState> = mutableState.asStateFlow()

    @Synchronized
    fun start(context: Context) {
        if (started) return
        app = context.applicationContext
        sensors = app.getSystemService(SensorManager::class.java)
        displays = app.getSystemService(DisplayManager::class.java)
        power = app.getSystemService(PowerManager::class.java)
        prefs = app.getSharedPreferences("flab", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        displays.registerDisplayListener(this, handler)
        app.registerReceiver(powerReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        sensor = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE, false)
            ?: sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE, true)
        val config = app.resources.configuration
        mutableState.value = FLabState(
            enabled = prefs.getBoolean("global_enabled", true),
            orientation = orientation(config),
            profile = runCatching {
                VisualProfile.valueOf(prefs.getString("profile", VisualProfile.BALANCED.name)!!)
            }.getOrDefault(VisualProfile.BALANCED),
            powerSave = power.isPowerSaveMode,
            sensorMode = if (sensor == null) SensorMode.UNAVAILABLE else SensorMode.POSTURE_FALLBACK,
            lastModuleError = prefs.getString("last_error", null),
        )
        started = true
    }

    @Synchronized
    fun acquire(module: String, needsHinge: Boolean = true) {
        ensureStarted()
        clients[module] = needsHinge
        update { it.copy(activeModules = clients.keys.toSet(), lastEvent = "$module active") }
        syncSensor()
    }

    @Synchronized
    fun release(module: String) {
        if (!started) return
        clients.remove(module)
        update { it.copy(activeModules = clients.keys.toSet(), lastEvent = "$module inactive") }
        syncSensor()
    }

    fun addListener(listener: Listener) {
        ensureStarted()
        listeners += listener
        listener.onState(mutableState.value)
    }

    fun removeListener(listener: Listener) { listeners -= listener }

    fun setEnabled(enabled: Boolean) {
        ensureStarted()
        prefs.edit().putBoolean("global_enabled", enabled).apply()
        if (!enabled) fallbackAnimation?.cancel()
        update { it.copy(enabled = enabled, lastEvent = if (enabled) "F/LAB enabled" else "Kill switch used") }
        syncSensor()
    }

    fun setProfile(profile: VisualProfile) {
        ensureStarted()
        prefs.edit().putString("profile", profile.name).apply()
        update { it.copy(profile = profile, lastEvent = "Profile ${profile.name.lowercase()}") }
        if (listening) {
            sensors.unregisterListener(this)
            listening = false
            syncSensor()
        }
    }

    fun setAccessibilityConnected(connected: Boolean) {
        ensureStarted()
        update { it.copy(accessibilityConnected = connected, lastEvent = if (connected)
            "App awareness connected" else "App awareness disconnected") }
    }

    fun reportForeground(packageName: String?) {
        ensureStarted()
        if (packageName == app.packageName) return
        update { it.copy(foregroundPackage = packageName, lastEvent = "Foreground app changed") }
    }

    fun reportWindow(width: Int, height: Int, displayId: Int = Display.DEFAULT_DISPLAY) {
        ensureStarted()
        val orientation = if (width > height) "Landscape" else "Portrait"
        update { it.copy(windowWidth = width, windowHeight = height,
            activeDisplay = displayId, orientation = orientation, lastEvent = "Window ${width}×$height") }
    }

    fun recordModuleError(module: String, error: Throwable) {
        ensureStarted()
        val message = "$module · ${error.javaClass.simpleName}: ${error.message.orEmpty().take(100)}"
        prefs.edit().putString("last_error", message).apply()
        update { it.copy(lastModuleError = message, lastEvent = "$module failed safely") }
    }

    fun reset() {
        ensureStarted()
        prefs.edit().clear().putBoolean("global_enabled", true).apply()
        app.getSharedPreferences("studio", Context.MODE_PRIVATE).edit().clear().apply()
        app.filesDir.resolve("landscape.jpg").delete()
        fallbackAnimation?.cancel()
        update { FLabState(enabled = true, powerSave = power.isPowerSaveMode,
            sensorMode = if (sensor == null) SensorMode.UNAVAILABLE else SensorMode.POSTURE_FALLBACK,
            activeModules = clients.keys.toSet(), lastEvent = "F/LAB reset") }
        syncSensor()
    }

    fun diagnostics(): String {
        ensureStarted()
        val s = mutableState.value
        return buildString {
            appendLine("F/LAB Diagnostics ${BuildConfig.VERSION_NAME}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Fold: ${s.foldPosture} · ${s.hingeAngle?.let { "%.1f°".format(it) } ?: "unavailable"}")
            appendLine("Sensor: ${s.sensorMode}")
            appendLine("Window: ${s.windowWidth}×${s.windowHeight} · ${s.orientation} · display ${s.activeDisplay}")
            appendLine("F/LAB: ${if (s.enabled) "enabled" else "disabled"} · ${s.profile}")
            appendLine("Modules: ${s.activeModules.joinToString().ifBlank { "none" }}")
            appendLine("App awareness: ${if (s.accessibilityConnected) "connected" else "not connected"}")
            appendLine("Power saver: ${s.powerSave}")
            appendLine("Last event: ${s.lastEvent}")
            appendLine("Last module error: ${s.lastModuleError ?: "none"}")
            append("No screen contents, account identifiers or user text are included.")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_HINGE_ANGLE || !mutableState.value.enabled) return
        val reading = motion.read(event.values.firstOrNull() ?: return) ?: return
        fallbackAnimation?.cancel()
        if (reading.direct) {
            publishMotion(reading, reading.target)
        } else {
            val start = mutableState.value.foldProgress
            fallbackAnimation = ValueAnimator.ofFloat(start, reading.target).apply {
                duration = when (mutableState.value.profile) {
                    VisualProfile.SMOOTH -> 520L
                    VisualProfile.MINIMAL, VisualProfile.BATTERY -> 260L
                    else -> 380L
                }
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { publishMotion(reading, it.animatedValue as Float) }
                start()
            }
        }
    }

    private fun publishMotion(reading: MotionReading, progress: Float) {
        update { it.copy(foldPosture = reading.posture, hingeAngle = reading.angle,
            foldProgress = progress.coerceIn(0f, 1f), sensorMode = reading.mode,
            lastEvent = if (reading.mode == SensorMode.CONTINUOUS) "Continuous hinge sample" else "Posture fallback") }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onDisplayChanged(displayId: Int) {
        val config = app.resources.configuration
        update { it.copy(activeDisplay = displayId, orientation = orientation(config), lastEvent = "Display changed") }
    }
    override fun onDisplayAdded(displayId: Int) = onDisplayChanged(displayId)
    override fun onDisplayRemoved(displayId: Int) = onDisplayChanged(Display.DEFAULT_DISPLAY)

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "global_enabled") {
            update { it.copy(enabled = prefs.getBoolean("global_enabled", true)) }
            syncSensor()
        }
    }

    @Synchronized
    private fun syncSensor() {
        if (!started) return
        val shouldListen = clients.values.any { it } && mutableState.value.enabled && sensor != null
        if (shouldListen && !listening) {
            val sampleUs = when (mutableState.value.profile) {
                VisualProfile.BATTERY -> 33_333
                VisualProfile.MINIMAL -> 16_667
                else -> 8_333
            }
            listening = sensors.registerListener(this, sensor, sampleUs, handler)
        } else if (!shouldListen && listening) {
            sensors.unregisterListener(this)
            listening = false
        }
    }

    private fun update(transform: (FLabState) -> FLabState) {
        val next = transform(mutableState.value)
        mutableState.value = next
        listeners.forEach { runCatching { it.onState(next) } }
    }

    private fun ensureStarted() {
        check(started) { "FLabCore must be started by FLabApplication" }
    }

    private fun orientation(config: Configuration) = when (config.orientation) {
        Configuration.ORIENTATION_LANDSCAPE -> "Landscape"
        Configuration.ORIENTATION_PORTRAIT -> "Portrait"
        else -> "Unknown"
    }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            update { it.copy(powerSave = power.isPowerSaveMode, lastEvent = "Power mode changed") }
        }
    }
}
