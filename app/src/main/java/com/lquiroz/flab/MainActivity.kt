package com.lquiroz.flab

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.lquiroz.flab.motion.FrostView
import com.lquiroz.flab.motion.ModuleState

class MainActivity : ComponentActivity() {
    private var previewAnimation: ValueAnimator? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("motion", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF9AE9DE), background = Color(0xFF101416),
                surface = Color(0xFF1C2327), onSurface = Color(0xFFF1F5F4))) {
                var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", false)) }
                var strength by remember { mutableFloatStateOf(prefs.getFloat("strength", 0.7f)) }
                var disclose by remember { mutableStateOf(false) }
                var preview by remember { mutableStateOf<FrostView?>(null) }
                val connected by ModuleState.connected
                val status by ModuleState.status
                val sensor by ModuleState.sensor
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState())
                        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text("F/LAB", style = MaterialTheme.typography.displayMedium)
                        Text("Movimiento suave · V1", style = MaterialTheme.typography.titleLarge)
                        Text("Un instante de cristal difuminado al plegar. Tu One UI, tus apps.",
                            style = MaterialTheme.typography.bodyLarge)
                        Card {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Activar movimiento", modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium)
                                    Switch(checked = enabled, onCheckedChange = { value ->
                                        if (value && !prefs.getBoolean("consent", false)) disclose = true
                                        else {
                                            enabled = value
                                            prefs.edit().putBoolean("enabled", value).apply()
                                        }
                                    })
                                }
                                Text(if (!enabled) "Apagado" else if (!connected)
                                    "Falta habilitar F/LAB en Accesibilidad" else status)
                                if (!connected) Button(onClick = { disclose = true }) {
                                    Text("Configurar permiso")
                                }
                            }
                        }
                        Text("Así se ve", style = MaterialTheme.typography.titleMedium)
                        AndroidView(factory = { context ->
                            FrostView(context, previewBitmap()).also { preview = it; it.strength = strength }
                        }, update = { it.strength = strength }, modifier = Modifier.fillMaxWidth().height(210.dp))
                        OutlinedButton(onClick = {
                            previewAnimation?.cancel()
                            previewAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
                                duration = 1000
                                addUpdateListener { preview?.amount = kotlin.math.sin(
                                    Math.PI * (it.animatedValue as Float)).toFloat() }
                                start()
                            }
                        }, modifier = Modifier.fillMaxWidth()) { Text("Ver efecto · sin permisos") }
                        Text("Intensidad", style = MaterialTheme.typography.titleMedium)
                        Slider(value = strength, onValueChange = { strength = it }, valueRange = 0.2f..1.5f,
                            onValueChangeFinished = { prefs.edit().putFloat("strength", strength).apply() })
                        Text("Cómo usarlo", style = MaterialTheme.typography.titleMedium)
                        Text("Actívalo, concede el permiso y vuelve a tu pantalla de inicio. Abre y cierra el Fold a velocidad normal. Puedes apagarlo aquí en cualquier momento.")
                        Text("Transición temporizada", style = MaterialTheme.typography.titleMedium)
                        Text("V1 reacciona al cambio de postura o pantalla. Con lecturas de 0°, 90° y 180° no sigue el ángulo exacto de tu mano. No altera el encendido de las pantallas ni el bloqueo de Samsung.")
                        if (connected) Text(sensor, style = MaterialTheme.typography.bodySmall)
                        Text("Privacidad", style = MaterialTheme.typography.titleMedium)
                        Text("Usa una captura temporal en memoria para el efecto. No guarda imágenes ni las envía. Se omite en pantallas protegidas o bloqueadas. La capa deja pasar los toques y desaparece en menos de un segundo.")
                        Text("Si Android bloquea el permiso: Ajustes → Aplicaciones → F/LAB → ⋮ → Permitir ajustes restringidos; después vuelve a Accesibilidad.", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                            Text("Administrar o revocar permiso")
                        }
                        Text("F/LAB 1.0 · Independiente de Samsung y Apple", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (disclose) AlertDialog(onDismissRequest = { disclose = false },
                    title = { Text("Permitir el efecto sobre otras apps") },
                    text = { Text(getString(R.string.fold_disclosure)) },
                    confirmButton = { TextButton(onClick = {
                        prefs.edit().putBoolean("consent", true).putBoolean("enabled", true).apply()
                        enabled = true
                        disclose = false
                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }) { Text("Entendido · abrir ajustes") } },
                    dismissButton = { TextButton(onClick = { disclose = false }) { Text("Ahora no") } })
            }
        }
    }
    override fun onStop() {
        previewAnimation?.cancel()
        previewAnimation = null
        super.onStop()
    }

    private fun previewBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(900, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(25, 46, 52))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = android.graphics.Color.rgb(156, 235, 218)
        canvas.drawCircle(740f, 110f, 210f, paint)
        paint.color = android.graphics.Color.rgb(67, 111, 129)
        canvas.drawCircle(610f, 420f, 270f, paint)
        paint.color = android.graphics.Color.WHITE
        paint.textSize = 55f
        canvas.drawText("Tu mundo. Más suave.", 40f, 110f, paint)
        paint.textSize = 28f
        canvas.drawText("F/LAB · Movimiento", 40f, 160f, paint)
        for (i in 0..4) {
            paint.color = android.graphics.Color.rgb(210 - i * 20, 225 - i * 10, 225)
            val left = 45f + i * 170
            canvas.drawRoundRect(left, 330f, left + 105, 435f, 26f, 26f, paint)
        }
        return bitmap
    }
}
