package com.lquiroz.flab

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lquiroz.flab.studio.FoldWallpaper
import com.lquiroz.flab.studio.PageView
import java.io.File

private val Paper = Color(0xFFF5F4EE)
private val Ink = Color(0xFF111111)
private val Muted = Color(0xFF6E706F)
private val Blue = Color(0xFF315CFF)
private val Panel = Color(0xFFFFFFFF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(245, 244, 238)
        window.navigationBarColor = android.graphics.Color.rgb(245, 244, 238)
        setContent { Studio() }
    }

    @Composable
    private fun Studio() {
        val prefs = remember { getSharedPreferences("studio", MODE_PRIVATE) }
        var preview by remember { mutableStateOf<PageView?>(null) }
        var progress by remember { mutableFloatStateOf(1f) }
        var blur by remember { mutableFloatStateOf(prefs.getFloat("blur", .45f)) }
        var radius by remember { mutableFloatStateOf(prefs.getFloat("radius", .045f)) }
        var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
        var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
        var imageRevision by remember { mutableIntStateOf(0) }

        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    File(filesDir, "landscape.jpg").outputStream().use { output -> input.copyTo(output) }
                } ?: error("No se pudo abrir la imagen")
            }.onSuccess {
                imageRevision++
                prefs.edit().putLong("image", System.currentTimeMillis()).apply()
                preview?.renderer?.reload()
                preview?.invalidate()
            }
        }

        MaterialTheme(
            colorScheme = lightColorScheme(
                primary = Blue,
                onPrimary = Color.White,
                background = Paper,
                onBackground = Ink,
                surface = Panel,
                onSurface = Ink,
            ),
        ) {
            Surface(Modifier.fillMaxSize(), color = Paper) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 22.dp, vertical = 18.dp),
                ) {
                    Text("F/LAB", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.4.sp)
                    Spacer(Modifier.height(24.dp))
                    Text(
                        "Papel en\nmovimiento.",
                        fontSize = 47.sp,
                        lineHeight = 45.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1.8).sp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Una transición suave para abrir tu Fold. Desliza la imagen y ajusta cómo se siente.",
                        color = Muted,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                    )
                    Spacer(Modifier.height(28.dp))

                    AndroidView(
                        factory = { context ->
                            PageView(context).also { view ->
                                preview = view
                                view.onProgress = { progress = it }
                                view.renderer.blur = blur
                                view.renderer.radius = radius
                                view.renderer.dark = dark
                                view.enabled = enabled
                            }
                        },
                        update = { view ->
                            preview = view
                            view.renderer.blur = blur
                            view.renderer.radius = radius
                            view.renderer.dark = dark
                            view.enabled = enabled
                            imageRevision.hashCode()
                            view.invalidate()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(360.dp),
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { preview?.play() },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f).height(58.dp),
                        ) { Text("VER MOVIMIENTO", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        OutlinedButton(
                            onClick = { imagePicker.launch("image/*") },
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.weight(1f).height(58.dp),
                        ) { Text("ELEGIR IMAGEN", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    }

                    Spacer(Modifier.height(30.dp))
                    Text("ESTUDIO", color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.8.sp)
                    Spacer(Modifier.height(10.dp))
                    ControlPanel {
                        LabeledSlider("Apertura", progress, 0f..1f) {
                            progress = it
                            preview?.setProgress(it)
                        }
                        LabeledSlider("Desenfoque suave", blur, 0f..1f) {
                            blur = it
                            prefs.edit().putFloat("blur", it).apply()
                        }
                        LabeledSlider("Esquinas", radius, 0f..0.15f) {
                            radius = it
                            prefs.edit().putFloat("radius", it).apply()
                        }
                        ToggleRow("Escenario oscuro", dark) {
                            dark = it
                            prefs.edit().putBoolean("dark", it).apply()
                        }
                        ToggleRow("Responder al Fold", enabled) {
                            enabled = it
                            prefs.edit().putBoolean("enabled", it).apply()
                        }
                    }

                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = { openWallpaperPicker() },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                    ) { Text("USAR COMO FONDO", fontWeight = FontWeight.Black, letterSpacing = .7.sp) }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "El efecto vive en el fondo de inicio. One UI conserva tus iconos y aplicaciones. F/LAB no captura la pantalla y no necesita Accesibilidad.",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    @Composable
    private fun ControlPanel(content: @Composable ColumnScope.() -> Unit) {
        Surface(color = Panel, shape = RoundedCornerShape(28.dp), shadowElevation = 1.dp) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), content = content)
        }
    }

    @Composable
    private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("${(value / range.endInclusive * 100).toInt()}%", color = Muted, fontSize = 13.sp)
        }
        Slider(value = value, onValueChange = change, valueRange = range)
        Spacer(Modifier.height(7.dp))
    }

    @Composable
    private fun ToggleRow(label: String, checked: Boolean, change: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(checked = checked, onCheckedChange = change)
        }
    }

    private fun openWallpaperPicker() {
        val component = ComponentName(this, FoldWallpaper::class.java)
        try {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            })
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }
}
