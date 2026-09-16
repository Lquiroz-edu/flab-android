package com.lquiroz.flab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.area.*
import androidx.window.core.ExperimentalWindowApi
import com.lquiroz.flab.ui.theme.FLabTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A foreground-only capability probe, not a system animation or Good Lock plugin.
 * No background service, private API, automatic retry or device-state override.
 */
@OptIn(ExperimentalWindowApi::class)
class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var controller: WindowAreaController
    private var rear: WindowAreaInfo? = null
    private var session: WindowAreaSessionPresenter? = null
    private var inFlight = false
    private var armed = false
    private var generation = 0
    private var startMs = 0L
    private var lastAngle: Float? = null
    private val angles = linkedSetOf<Int>()
    private val events = mutableStateListOf<String>()

    private var enabled by mutableStateOf(false)
    private var capability by mutableStateOf("Sin datos todavía")
    private var sessionStatus by mutableStateOf("Desactivada")
    private var sensorStatus by mutableStateOf("Sin medir")
    private var sampleCount by mutableIntStateOf(0)
    private var angle by mutableStateOf<Float?>(null)
    private var observedAngles by mutableStateOf("—")
    private var copied by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        controller = WindowAreaController.getOrCreate()
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                controller.windowAreaInfos.collect { infos ->
                    rear = infos.firstOrNull { it.type == WindowAreaInfo.Type.TYPE_REAR_FACING }
                    val current = rear?.getCapability(
                        WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA
                    )?.status?.toString() ?: "No expuesta por el sistema"
                    if (current != capability) {
                        capability = current
                        record("Capacidad: $current")
                    }
                    requestIfReady()
                }
            }
        }
        setContent { FLabTheme { ModuleScreen() } }
    }

    private fun record(message: String) {
        if (!enabled && startMs == 0L) return
        val elapsed = SystemClock.elapsedRealtime() - startMs
        if (events.size >= 150) events.removeAt(0)
        events.add("+${elapsed} ms · $message")
        copied = false
    }

    private fun enableProbe() {
        if (inFlight) return
        events.clear()
        angles.clear()
        lastAngle = null
        angle = null
        observedAngles = "—"
        sampleCount = 0
        startMs = SystemClock.elapsedRealtime()
        enabled = true
        generation++
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        record("Inicio: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT}")
        record("Firmware: ${Build.DISPLAY}")
        record("Capacidad: $capability")
        recordGeometry()
        val hinge = if (Build.VERSION.SDK_INT >= 30) sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) else null
        sensorStatus = if (hinge == null) "Sensor público no disponible" else {
            val registered = runCatching {
                sensors.registerListener(this, hinge, SensorManager.SENSOR_DELAY_GAME)
            }.getOrDefault(false)
            if (registered) "Escuchando: ${hinge.name}" else "No se pudo registrar el sensor"
        }
        record(sensorStatus)
        sessionStatus = "Pulsa Probar dos pantallas"
    }

    private fun disableProbe(reason: String) {
        armed = false
        enabled = false
        generation++
        sensors.unregisterListener(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val previous = session
        session = null
        val failure = runCatching { previous?.close() }.exceptionOrNull()
        sessionStatus = if (failure == null) "Desactivada" else "No se confirmó el cierre; sal de la app"
        record(reason)
        if (failure != null) record("Cierre: ${failure.javaClass.simpleName}")
    }

    private fun requestIfReady() {
        if (!enabled || !armed || inFlight || session != null) return
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val area = rear ?: return
        val status = area.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA).status
        if (status != WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE) return
        val token = area.token ?: return
        armed = false
        inFlight = true
        val requestGeneration = generation
        sessionStatus = "Solicitud enviada"
        record("Solicitud de presentación")
        val callback = object : WindowAreaPresentationSessionCallback {
            override fun onSessionStarted(presenter: WindowAreaSessionPresenter) {
                inFlight = false
                if (!enabled || generation != requestGeneration) {
                    runCatching { presenter.close() }
                    return
                }
                session = presenter
                sessionStatus = "Sesión concedida"
                record("Sesión concedida; bounds=${area.metrics.bounds}")
                // Deliberately no guessed cover/inner role, rotation or artificial angle.
                val view = object : TextView(presenter.context) {
                    private var reported = false
                    override fun onDraw(canvas: Canvas) {
                        super.onDraw(canvas)
                        if (!reported) {
                            reported = true
                            post {
                                if (enabled && generation == requestGeneration) {
                                    record("Primer onDraw secundario (no mide encendido físico)")
                                }
                            }
                        }
                    }
                }.apply {
                    text = "F/LAB\nSegunda superficie\n\nPrueba técnica · sin animación"
                    textSize = 24f
                    gravity = Gravity.CENTER
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(android.graphics.Color.rgb(24, 40, 52))
                    keepScreenOn = true
                }
                runCatching { presenter.setContentView(view) }.onFailure {
                    record("Fallo al dibujar: ${it.javaClass.simpleName}")
                    runCatching { presenter.close() }
                    session = null
                    sessionStatus = "Prueba fallida; sin reintento automático"
                }
            }

            override fun onSessionEnded(t: Throwable?) {
                inFlight = false
                if (generation != requestGeneration) return
                session = null
                sessionStatus = "Sesión terminada; reintento manual"
                record("Fin de sesión: ${t?.javaClass?.simpleName ?: "sin error"}")
            }

            override fun onContainerVisibilityChanged(isVisible: Boolean) {
                if (generation == requestGeneration) record("Contenedor visible: $isVisible")
            }
        }
        runCatching {
            controller.presentContentOnWindowArea(token, this, ContextCompat.getMainExecutor(this), callback)
        }.onFailure {
            inFlight = false
            sessionStatus = "Solicitud rechazada; reintento manual"
            record("Rechazo: ${it.javaClass.simpleName}")
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!enabled || event.sensor.type != Sensor.TYPE_HINGE_ANGLE) return
        val value = event.values.firstOrNull() ?: return
        if (!value.isFinite()) return
        sampleCount++
        angle = value
        if (angles.size < 181) angles.add(value.roundToInt())
        observedAngles = angles.take(16).joinToString() + if (angles.size > 16) " … (${angles.size} valores)" else ""
        if (lastAngle == null || kotlin.math.abs(value - lastAngle!!) >= 5f) {
            record("Ángulo recibido: ${value}°")
            lastAngle = value
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun recordGeometry() {
        record("Ventana: ${resources.configuration.screenWidthDp} × ${resources.configuration.screenHeightDp} dp")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (enabled) { record("Cambio de configuración"); recordGeometry() }
    }

    override fun onStop() {
        if (enabled) disableProbe("Prueba detenida al pasar a segundo plano")
        super.onStop()
    }

    override fun onDestroy() {
        sensors.unregisterListener(this)
        runCatching { session?.close() }
        super.onDestroy()
    }

    private fun copyReport() {
        val report = "F/LAB · 1.0.0-alpha04-probe\n" +
            "Muestras: $sampleCount\nÁngulos redondeados: ${angles.joinToString()}\n" +
            "Últimos ${events.size} eventos. onDraw no prueba visibilidad física.\n" +
            events.joinToString("\n")
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("F/LAB diagnóstico", report))
        copied = true
    }

    @Composable
    private fun ModuleScreen() {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("F/LAB", style = MaterialTheme.typography.headlineLarge)
                Text("Movimiento al plegar", style = MaterialTheme.typography.titleLarge)
                Text("Laboratorio · Alpha 04\nConserva One UI Home. No es un módulo oficial de Good Lock ni modifica otras apps.")
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Diagnóstico", style = MaterialTheme.typography.titleMedium)
                            Text(if (enabled) "Activo solo en esta app" else "Desactivado")
                        }
                        Switch(checked = enabled, enabled = enabled || !inFlight,
                            onCheckedChange = { if (it) enableProbe() else disableProbe("Desactivado por el usuario") })
                    }
                }
                Text("Abre el Fold, activa el diagnóstico y pulsa Probar dos pantallas. Pliega lentamente sin salir de F/LAB.")
                Text("Pantallas: $capability\nSesión: $sessionStatus")
                Button(enabled = enabled && !inFlight && session == null && !armed,
                    onClick = {
                        armed = true
                        sessionStatus = "Esperando capacidad disponible; apaga el interruptor para cancelar"
                        record("Prueba solicitada por el usuario")
                        requestIfReady()
                    }) { Text("Probar dos pantallas") }
                Text("$sensorStatus\nÁngulo real recibido: ${angle?.let { "$it°" } ?: "—"}\nMuestras: $sampleCount\nValores observados: $observedAngles")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Acceso avanzado", style = MaterialTheme.typography.titleMedium)
                        Text("Shizuku no conectado ni integrado en esta versión. No se solicitan permisos elevados ni se fuerzan estados de pantalla.")
                    }
                }
                OutlinedButton(enabled = events.isNotEmpty(), onClick = ::copyReport) {
                    Text(if (copied) "Informe copiado" else "Copiar informe")
                }
                Text("Sin franja ni animación de prueba. Este diagnóstico determina qué permite tu firmware antes de implementar el efecto soft blur.")
                Text(events.takeLast(8).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
