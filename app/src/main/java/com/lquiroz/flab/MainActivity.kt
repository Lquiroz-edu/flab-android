package com.lquiroz.flab

import android.app.WallpaperManager
import android.content.*
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lquiroz.flab.core.*
import com.lquiroz.flab.profiles.*
import com.lquiroz.flab.studio.FoldWallpaper
import com.lquiroz.flab.studio.PageView
import java.io.File

private enum class Screen { HOME, MOTION, APPS, DIAGNOSTICS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FLabCore.start(applicationContext)
        setContent { FLabApp() }
    }

    override fun onStart() {
        super.onStart()
        FLabCore.acquire("Control center")
        window.decorView.post { FLabCore.reportWindow(window.decorView.width, window.decorView.height) }
    }

    override fun onStop() {
        FLabCore.release("Control center")
        super.onStop()
    }

    @Composable
    private fun FLabApp() {
        val prefs = remember { getSharedPreferences("flab", MODE_PRIVATE) }
        var onboardingDone by rememberSaveable { mutableStateOf(prefs.getBoolean("onboarding_done", false)) }
        if (!onboardingDone) {
            FLabTheme { Onboarding { prefs.edit().putBoolean("onboarding_done", true).apply(); onboardingDone = true } }
            return
        }

        var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
        val state by FLabCore.state.collectAsState()
        FLabTheme {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        listOf(
                            Screen.HOME to ("●" to "Inicio"),
                            Screen.MOTION to ("↔" to "Motion"),
                            Screen.APPS to ("▦" to "Apps"),
                            Screen.DIAGNOSTICS to ("i" to "Estado"),
                        ).forEach { (target, labels) ->
                            NavigationBarItem(
                                selected = screen == target,
                                onClick = { screen = target },
                                icon = { Text(labels.first, fontWeight = FontWeight.Black) },
                                label = { Text(labels.second) },
                            )
                        }
                    }
                },
            ) { padding ->
                when (screen) {
                    Screen.HOME -> Home(state, Modifier.padding(padding), onMotion = { screen = Screen.MOTION })
                    Screen.MOTION -> MotionStudio(state, Modifier.padding(padding))
                    Screen.APPS -> AppsScreen(state, Modifier.padding(padding))
                    Screen.DIAGNOSTICS -> Diagnostics(state, Modifier.padding(padding))
                }
            }
        }
    }

    @Composable
    private fun FLabTheme(content: @Composable () -> Unit) {
        val colors = if (isSystemInDarkTheme()) darkColorScheme(
            primary = Color(0xFF8FA8FF), background = Color(0xFF101114), surface = Color(0xFF1A1B20),
            surfaceVariant = Color(0xFF24262D), onBackground = Color(0xFFF5F3EC), onSurface = Color(0xFFF5F3EC),
        ) else lightColorScheme(
            primary = Color(0xFF315CFF), background = Color(0xFFF5F4EE), surface = Color.White,
            surfaceVariant = Color(0xFFE9E8E2), onBackground = Color(0xFF111111), onSurface = Color(0xFF111111),
        )
        MaterialTheme(colorScheme = colors, content = content)
    }

    @Composable
    private fun Onboarding(finish: () -> Unit) {
        var page by rememberSaveable { mutableIntStateOf(0) }
        val titles = listOf("Tu Fold, más coherente.", "Intervención mínima.", "Tú mantienes el control.")
        val bodies = listOf(
            "F/LAB coordina la bisagra, las pantallas y sus módulos desde un único Core. Motion puede seguir el ángulo real cuando Samsung lo publica.",
            "No sustituye One UI. Fold Motion funciona sin Accesibilidad; System Motion LAB es opcional y usa una captura efímera sólo durante el cambio de pantalla.",
            "Puedes probar cada efecto, desactivar F/LAB al instante y restablecer toda su configuración desde Diagnostics.",
        )
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("F/LAB · MVP", fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 2.2.sp)
                Column {
                    Text("0${page + 1}", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(18.dp))
                    Text(titles[page], fontSize = 48.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp)
                    Spacer(Modifier.height(18.dp))
                    Text(bodies[page], color = MaterialTheme.colorScheme.onSurface.copy(alpha = .67f), fontSize = 17.sp, lineHeight = 25.sp)
                }
                Button(
                    onClick = { if (page < 2) page++ else finish() },
                    modifier = Modifier.fillMaxWidth().height(62.dp),
                    shape = RoundedCornerShape(20.dp),
                ) { Text(if (page < 2) "CONTINUAR" else "ENTRAR EN F/LAB", fontWeight = FontWeight.Black) }
            }
        }
    }

    @Composable
    private fun Home(state: FLabState, modifier: Modifier, onMotion: () -> Unit) {
        var accessDialog by remember { mutableStateOf(false) }
        val prefs = remember { getSharedPreferences("flab", MODE_PRIVATE) }
        var experiment by remember { mutableStateOf(prefs.getBoolean("immersive_experiment", false)) }
        var systemMotion by remember { mutableStateOf(prefs.getBoolean("system_motion_experiment", false)) }
        Page(modifier) {
            BrandHeader("El Fold,\nmejor conectado.", if (state.enabled) "F/LAB ACTIVE" else "F/LAB DISABLED")
            HealthCard(state)
            Section("MÓDULOS")
            ModuleCard("Fold Motion", if (state.enabled) "ON" else "OFF",
                "${state.foldPosture.name.lowercase().replaceFirstChar { it.uppercase() }} · ${state.sensorMode.name.lowercase()}", onMotion)
            ModuleCard("Continuity", if (systemMotion) "LAB ON" else "CORE",
                if (systemMotion) "Frame efímero + shader gobernado por la bisagra" else "Ventana, orientación y display coordinados") { onMotion() }
            ModuleCard("App awareness", if (state.accessibilityConnected) "ON" else "OPTIONAL",
                if (state.accessibilityConnected) "Perfiles por aplicación disponibles" else "Requiere consentimiento de Accesibilidad") {
                accessDialog = true
            }
            CardBlock {
                ToggleLine("System Motion · LAB", systemMotion) {
                    systemMotion = it
                    prefs.edit().putBoolean("system_motion_experiment", it).apply()
                    if (it && !state.accessibilityConnected) accessDialog = true
                }
                Text("Extiende Fold Motion sobre One UI y apps compatibles. Captura un único frame en memoria, no recibe toques y se retira al terminar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                ToggleLine("Immersive experimental", experiment) {
                    experiment = it
                    prefs.edit().putBoolean("immersive_experiment", it).apply()
                }
                Text("Sólo actúa en apps elegidas y con bordes visuales que no reciben toques.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
            }
            Section("PERFIL")
            ProfileSelector(state.profile)
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { FLabCore.setEnabled(!state.enabled) },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                colors = if (state.enabled) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    else ButtonDefaults.buttonColors(),
                shape = RoundedCornerShape(20.dp),
            ) { Text(if (state.enabled) "DISABLE F/LAB" else "ENABLE F/LAB", fontWeight = FontWeight.Black) }
        }
        if (accessDialog) AccessDisclosure(
            onDismiss = { accessDialog = false },
            onContinue = { accessDialog = false; startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
        )
    }

    @Composable
    private fun HealthCard(state: FLabState) {
        val healthy = state.enabled && state.lastModuleError == null
        Surface(color = if (healthy) Color(0xFF315CFF) else MaterialTheme.colorScheme.error,
            contentColor = Color.White, shape = RoundedCornerShape(28.dp)) {
            Row(Modifier.fillMaxWidth().padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (healthy) "Sistema saludable" else if (!state.enabled) "F/LAB detenido" else "Revisar módulo",
                        fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Text(if (healthy) "Core activo · ${state.activeModules.size} módulos conectados" else state.lastModuleError ?: "Kill Switch activo",
                        color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                }
                Text(if (healthy) "✓" else "!", fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
        }
    }

    @Composable
    private fun ProfileSelector(selected: VisualProfile) {
        CardBlock {
            VisualProfile.entries.forEach { profile ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = selected == profile, onClick = { FLabCore.setProfile(profile) })
                    Column {
                        Text(profile.name.lowercase().replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                        Text(profileDescription(profile), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                    }
                }
            }
        }
    }

    private fun profileDescription(profile: VisualProfile) = when (profile) {
        VisualProfile.BALANCED -> "Equilibrio entre fluidez y consumo"
        VisualProfile.SMOOTH -> "Más profundidad y transición"
        VisualProfile.MINIMAL -> "Movimiento breve y discreto"
        VisualProfile.BATTERY -> "Intervención y duración mínimas"
        VisualProfile.CUSTOM -> "Utiliza tus ajustes manuales"
    }

    @Composable
    private fun MotionStudio(state: FLabState, modifier: Modifier) {
        val prefs = remember { getSharedPreferences("studio", MODE_PRIVATE) }
        var preview by remember { mutableStateOf<PageView?>(null) }
        var progress by remember { mutableFloatStateOf(state.foldProgress) }
        var blur by remember { mutableFloatStateOf(prefs.getFloat("blur", .58f)) }
        var radius by remember { mutableFloatStateOf(prefs.getFloat("radius", .045f)) }
        var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
        var enabled by remember { mutableStateOf(prefs.getBoolean("enabled", true)) }
        var imageRevision by remember { mutableIntStateOf(0) }
        val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                contentResolver.openInputStream(uri)?.use { input ->
                    File(filesDir, "landscape.jpg").outputStream().use { input.copyTo(it) }
                } ?: error("No se pudo abrir la imagen")
            }.onSuccess {
                imageRevision++
                prefs.edit().putLong("image", System.currentTimeMillis()).apply()
                preview?.renderer?.reload(); preview?.invalidate()
            }.onFailure { FLabCore.recordModuleError("Image", it) }
        }
        LaunchedEffect(state.foldProgress) { if (enabled) progress = state.foldProgress }
        Page(modifier) {
            BrandHeader("Movimiento\nfísico.", "FOLD MOTION")
            Text(if (state.sensorMode == SensorMode.CONTINUOUS) "Siguiendo bisagra · ${state.hingeAngle?.toInt()}°"
                else "Fallback perceptivo · ${state.foldPosture}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("AGSL físico · sin línea central · ${state.hingeVelocityDegPerSecond.toInt()}°/s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            AndroidView(
                factory = { context -> PageView(context).also { preview = it; it.onProgress = { p -> progress = p } } },
                update = { view ->
                    preview = view; view.renderer.blur = blur; view.renderer.radius = radius
                    view.renderer.dark = dark; view.respondToFold = enabled; imageRevision.hashCode(); view.invalidate()
                },
                modifier = Modifier.fillMaxWidth().height(350.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { preview?.play() }, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(18.dp)) {
                    Text("PREVIEW", fontWeight = FontWeight.Black)
                }
                OutlinedButton(onClick = { imagePicker.launch("image/*") }, Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(18.dp)) {
                    Text("IMAGEN", fontWeight = FontWeight.Black)
                }
            }
            CardBlock {
                SliderLine("Apertura", progress, 0f..1f) { progress = it; preview?.setProgress(it) }
                SliderLine("Blur suave", blur, 0f..1f) { blur = it; prefs.edit().putFloat("blur", it).apply() }
                SliderLine("Esquinas", radius, 0f..0.15f) { radius = it; prefs.edit().putFloat("radius", it).apply() }
                ToggleLine("Escenario oscuro", dark) { dark = it; prefs.edit().putBoolean("dark", it).apply() }
                ToggleLine("Responder al Fold", enabled) { enabled = it; prefs.edit().putBoolean("enabled", it).apply() }
            }
            Button(onClick = { openWallpaperPicker() }, Modifier.fillMaxWidth().height(62.dp), shape = RoundedCornerShape(20.dp)) {
                Text("USAR EN ONE UI HOME", fontWeight = FontWeight.Black)
            }
            Text("Motion actúa sobre el fondo de inicio; iconos y widgets siguen bajo control de One UI.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        }
    }

    @Composable
    private fun AppsScreen(state: FLabState, modifier: Modifier) {
        var revision by remember { mutableIntStateOf(0) }
        Page(modifier) {
            BrandHeader("Cada app,\nsu tratamiento.", "APP PROFILES")
            Text(if (state.accessibilityConnected) "App awareness conectado" else "App awareness no conectado · los perfiles no actúan",
                color = if (state.accessibilityConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold)
            ProfileCatalog.rules().forEach { rule ->
                val selected = revision >= 0 && ProfileCatalog.immersiveEnabled(this@MainActivity, rule)
                CardBlock {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.label, fontSize = 19.sp, fontWeight = FontWeight.Black)
                            Text(rule.strategy.name.lowercase().replace('_', ' '), color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        if (rule.strategy == AppStrategy.IMMERSIVE_OPT_IN) Switch(selected, onCheckedChange = {
                            ProfileCatalog.setImmersiveEnabled(this@MainActivity, rule, it); revision++
                        }) else Text(if (rule.safe) "SAFE" else "AUTO", fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(rule.reason, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
                    if (rule.continuity) Text("Continuity ✓", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                }
            }
        }
    }

    @Composable
    private fun Diagnostics(state: FLabState, modifier: Modifier) {
        var resetDialog by remember { mutableStateOf(false) }
        Page(modifier) {
            BrandHeader("Estado visible.\nSin Android Studio.", "DIAGNOSTICS")
            CardBlock {
                Diagnostic("Device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                Diagnostic("Android", "${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}")
                Diagnostic("Fold", "${state.foldPosture} · ${state.hingeAngle?.let { "${it.toInt()}°" } ?: "sin ángulo"}")
                Diagnostic("Velocity", "${state.hingeVelocityDegPerSecond.toInt()}°/s")
                Diagnostic("Sensor", state.sensorMode.name)
                Diagnostic("Window", "${state.windowWidth}×${state.windowHeight} · ${state.orientation}")
                Diagnostic("Display", state.activeDisplay.toString())
                Diagnostic("Modules", state.activeModules.joinToString().ifBlank { "None" })
                Diagnostic("App awareness", if (state.accessibilityConnected) "Connected" else "Not connected")
                Diagnostic("Power saver", state.powerSave.toString())
                Diagnostic("Last event", state.lastEvent)
                Diagnostic("Last error", state.lastModuleError ?: "None")
            }
            Button(onClick = { shareDiagnostics() }, Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) {
                Text("COMPARTIR DEBUG REPORT", fontWeight = FontWeight.Black)
            }
            OutlinedButton(onClick = { FLabCore.setEnabled(false) }, Modifier.fillMaxWidth().height(58.dp), shape = RoundedCornerShape(18.dp)) {
                Text("KILL SWITCH", fontWeight = FontWeight.Black)
            }
            TextButton(onClick = { resetDialog = true }, Modifier.fillMaxWidth()) { Text("Reset F/LAB") }
            Text("El reporte no incluye texto de otras apps, capturas, cuentas ni identificadores del dispositivo.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        }
        if (resetDialog) AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text("¿Restablecer F/LAB?") },
            text = { Text("Se borrarán perfiles, imagen y ajustes. One UI seguirá funcionando normalmente.") },
            confirmButton = { TextButton(onClick = { FLabCore.reset(); resetDialog = false }) { Text("RESTABLECER") } },
            dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("CANCELAR") } },
        )
    }

    @Composable
    private fun AccessDisclosure(onDismiss: () -> Unit, onContinue: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("App awareness · Accesibilidad") },
            text = { Text("F/LAB solicita Accesibilidad para reconocer la app en primer plano y aplicar perfiles. Si activas System Motion LAB, también toma un único frame de la pantalla al comenzar cada fase del pliegue y lo muestra brevemente con un shader controlado por la bisagra.\n\nLa captura vive sólo en memoria, se destruye al terminar y nunca se guarda ni transmite. Android bloquea las pantallas protegidas y F/LAB excluye banca, pagos, contraseñas, permisos, cámara y otras superficies sensibles. La capa no recibe toques. F/LAB no lee nodos, textos, mensajes o contraseñas, ni ejecuta gestos o acciones.\n\nEs opcional: Preview y live wallpaper funcionan sin este acceso. Puedes revocarlo en cualquier momento.") },
            confirmButton = { TextButton(onClick = onContinue) { Text("ACEPTO · ABRIR AJUSTES") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("AHORA NO") } },
        )
    }

    @Composable
    private fun Page(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), content = content,
        )
    }

    @Composable
    private fun BrandHeader(title: String, eyebrow: String) {
        Text("F/LAB · $eyebrow", fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.7.sp,
            color = MaterialTheme.colorScheme.primary)
        Text(title, fontSize = 43.sp, lineHeight = 43.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.3).sp)
    }

    @Composable private fun Section(label: String) = Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black,
        letterSpacing = 1.7.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))

    @Composable
    private fun CardBlock(content: @Composable ColumnScope.() -> Unit) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(26.dp), tonalElevation = 1.dp) {
            Column(Modifier.fillMaxWidth().padding(20.dp), content = content)
        }
    }

    @Composable
    private fun ModuleCard(title: String, status: String, detail: String, onClick: () -> Unit) {
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
            Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 19.sp, fontWeight = FontWeight.Black)
                    Text(detail, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
                }
                Text(status, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }

    @Composable
    private fun ToggleLine(label: String, checked: Boolean, change: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(checked, change)
        }
    }

    @Composable
    private fun SliderLine(label: String, value: Float, range: ClosedFloatingPointRange<Float>, change: (Float) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text("${(value / range.endInclusive * 100).toInt()}%", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
        }
        Slider(value, change, valueRange = range)
    }

    @Composable
    private fun Diagnostic(label: String, value: String) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
            Text(label, Modifier.width(112.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp)
            Text(value, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }

    private fun openWallpaperPicker() {
        try {
            startActivity(Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(this@MainActivity, FoldWallpaper::class.java))
            })
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    private fun shareDiagnostics() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "F/LAB Diagnostics")
            putExtra(Intent.EXTRA_TEXT, FLabCore.diagnostics())
        }, "Compartir reporte"))
    }
}
