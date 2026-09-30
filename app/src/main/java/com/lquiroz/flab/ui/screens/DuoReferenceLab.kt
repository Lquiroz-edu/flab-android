package com.lquiroz.flab.ui.screens

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lquiroz.flab.motion.EvidenceSource
import com.lquiroz.flab.motion.FoldEvidence
import com.lquiroz.flab.motion.duo.DuoPreviewInput
import com.lquiroz.flab.motion.duo.DuoPreviewView
import com.lquiroz.flab.ui.components.FLabCard
import com.lquiroz.flab.ui.components.KeyValueRow
import com.lquiroz.flab.ui.components.SectionLabel
import com.lquiroz.flab.ui.theme.FLabColors
import kotlinx.coroutines.flow.Flow
import kotlin.math.roundToInt

/** The reference shader runs against local sample content, with independent manual controls. */
@Composable
internal fun DuoReferenceLab(evidence: Flow<FoldEvidence>, onTrackSensor: (Boolean) -> Unit) {
    var angle by rememberSaveable { mutableFloatStateOf(135f) }
    var inner by rememberSaveable { mutableStateOf(true) }
    var sensor by rememberSaveable { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var blur by rememberSaveable { mutableFloatStateOf(0.3f) }
    var renderer by remember { mutableStateOf("Preparando efecto…") }
    val input = remember { DuoPreviewInput() }
    var samples by remember { mutableStateOf(0) }
    var raw by remember { mutableStateOf<Float?>(null) }
    val owner = LocalLifecycleOwner.current
    val track by rememberUpdatedState(onTrackSensor)

    DisposableEffect(owner, sensor) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) playing = false
            track(sensor && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        }
        owner.lifecycle.addObserver(observer)
        track(sensor && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { owner.lifecycle.removeObserver(observer); track(false) }
    }
    LaunchedEffect(evidence, owner, sensor) {
        if (!sensor) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            evidence.collect { observation ->
                // Posture interpolation is never presented as measured angle.
                if (observation.source == EvidenceSource.HingeAngle &&
                    System.nanoTime() - observation.timestampNanos in 0..750_000_000L &&
                    input.sample(observation.progress * 180f)
                ) {
                    raw = input.rawAngle
                    samples = input.samples
                }
            }
        }
    }
    LaunchedEffect(playing, inner, sensor, owner) {
        if (!playing || sensor) return@LaunchedEffect
        try {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                val sweep = Animatable(if (inner) 180f else 0f)
                sweep.animateTo(90f, tween(1400, easing = LinearEasing)) { angle = value }
                sweep.animateTo(if (inner) 180f else 0f, tween(1400, easing = LinearEasing)) { angle = value }
                playing = false
            }
        } finally {
            playing = false
        }
    }

    FLabCard(Modifier.fillMaxWidth()) {
        SectionLabel("Duo · prueba de cristal")
        Spacer(Modifier.height(8.dp))
        Text("Proyección, desenfoque y oscurecimiento del motor de referencia, sobre contenido de prueba dentro de F/LAB.", color = FLabColors.textSecondary, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { inner = true; playing = false }) { Text(if (inner) "Interior ✓" else "Interior") }
            TextButton(onClick = { inner = false; playing = false }) { Text(if (!inner) "Exterior ✓" else "Exterior") }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            AndroidView(
                factory = { context -> DuoPreviewView(context).apply { onStatus = { renderer = it } } },
                modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(20.dp)),
                onReset = null,
                onRelease = { it.release() },
                update = { view -> view.configure(if (sensor) raw ?: 180f else angle, inner, blur, !sensor || input.continuousObserved) },
            )
            Text(renderer, color = FLabColors.textSecondary, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("Este efecto requiere Android 13 o posterior.")
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { sensor = !sensor; playing = false; raw = null }) { Text(if (sensor) "Volver a manual" else "Probar con bisagra") }
            TextButton(enabled = !sensor, onClick = { playing = !playing }) { Text(if (playing) "Detener" else "Abrir y cerrar") }
        }
        if (!sensor) {
            KeyValueRow("Ángulo simulado", "${angle.roundToInt()}°")
            Slider(value = angle, onValueChange = { playing = false; angle = it }, valueRange = 0f..180f)
        } else {
            KeyValueRow("Ángulo recibido", raw?.let { "${it.roundToInt()}°" } ?: "Esperando muestras")
            KeyValueRow("Muestras", samples.toString())
            Text(if (input.continuousObserved) "Se han observado varios ángulos intermedios." else "Continuidad sin demostrar: el sensor puede entregar solo 0°, 90° y 180°. Activa F/LAB y Fold Motion para recibir muestras.", color = FLabColors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(8.dp))
        KeyValueRow("Desenfoque", "${(blur * 100).roundToInt()}%")
        Slider(value = blur, onValueChange = { blur = it })
        Text("Esta prueba no incluye el espejo Windowed Glass, Shizuku ni el cambio de panel. El contenido conserva la última muestra cuando la bisagra se detiene.", color = FLabColors.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}
