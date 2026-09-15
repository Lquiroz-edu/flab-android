package com.lquiroz.flab.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lquiroz.flab.fold.FoldPosture
import com.lquiroz.flab.fold.FoldSnapshot
import com.lquiroz.flab.ui.theme.Cyan
import com.lquiroz.flab.ui.theme.Surface as FLabSurface
import com.lquiroz.flab.ui.theme.SurfaceRaised
import com.lquiroz.flab.ui.theme.TextSecondary
import com.lquiroz.flab.ui.theme.Violet
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class WindowMode(val label: String) {
    Compact("Cover"),
    Medium("Inner"),
    Expanded("Inner wide"),
}

fun windowModeForWidth(widthDp: Int): WindowMode = when {
    widthDp < 600 -> WindowMode.Compact
    widthDp < 840 -> WindowMode.Medium
    else -> WindowMode.Expanded
}

fun nextMomentIndex(current: Int, count: Int): Int {
    require(count > 0) { "Moment count must be positive" }
    return (current + 1) % count
}

private data class ContinuityMoment(
    val number: String,
    val eyebrow: String,
    val title: String,
    val detail: String,
    val accent: Color,
)

private val moments = listOf(
    ContinuityMoment(
        number = "01",
        eyebrow = "FOCUS",
        title = "One thought,\nmore room.",
        detail = "The active idea stays anchored while the canvas grows around it.",
        accent = Cyan,
    ),
    ContinuityMoment(
        number = "02",
        eyebrow = "FLOW",
        title = "Never begin\ntwice.",
        detail = "Selection, progress and intent survive every screen transition.",
        accent = Violet,
    ),
    ContinuityMoment(
        number = "03",
        eyebrow = "DEPTH",
        title = "Space reveals\ncontext.",
        detail = "The inner display adds structure instead of merely stretching pixels.",
        accent = Color(0xFFFFD37A),
    ),
)

@Composable
fun FLabHomeScreen(foldSnapshot: FoldSnapshot) {
    var selectedMoment by rememberSaveable { mutableIntStateOf(0) }
    var isPlaying by rememberSaveable { mutableStateOf(false) }
    var progress by rememberSaveable { mutableFloatStateOf(0.18f) }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            delay(50)
            progress = if (progress >= 1f) 0f else (progress + 0.0065f).coerceAtMost(1f)
        }
    }

    val moment = moments[selectedMoment]
    val accent by animateColorAsState(
        targetValue = moment.accent,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "sceneAccent",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.14f), Color(0xFF090B0F)),
                    radius = 1350f,
                ),
            ),
    ) {
        val mode = windowModeForWidth(maxWidth.value.toInt())
        val horizontalPadding = if (mode == WindowMode.Compact) 18.dp else 30.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = 18.dp),
        ) {
            ContinuityHeader(mode = mode, accent = accent)
            Spacer(Modifier.height(if (mode == WindowMode.Compact) 24.dp else 30.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = {
                    fadeIn(tween(420, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(180))
                },
                label = "coverToInner",
            ) { targetMode ->
                if (targetMode == WindowMode.Compact) {
                    CoverScene(
                        moment = moment,
                        accent = accent,
                        progress = progress,
                        isPlaying = isPlaying,
                        onTogglePlayback = { isPlaying = !isPlaying },
                        onNextMoment = { selectedMoment = nextMomentIndex(selectedMoment, moments.size) },
                        foldSnapshot = foldSnapshot,
                    )
                } else {
                    InnerScene(
                        moment = moment,
                        selectedMoment = selectedMoment,
                        accent = accent,
                        progress = progress,
                        isPlaying = isPlaying,
                        onTogglePlayback = { isPlaying = !isPlaying },
                        onSelectMoment = { selectedMoment = it },
                        foldSnapshot = foldSnapshot,
                        wide = targetMode == WindowMode.Expanded,
                    )
                }
            }
        }
    }
}

@Composable
private fun ContinuityHeader(mode: WindowMode, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("F/LAB", fontSize = 27.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp)
            Text(
                "MOTION CONTINUITY · V1",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(accent.copy(alpha = 0.11f))
                .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(100.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Text(mode.label.uppercase(), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CoverScene(
    moment: ContinuityMoment,
    accent: Color,
    progress: Float,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onNextMoment: () -> Unit,
    foldSnapshot: FoldSnapshot,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HeroCanvas(moment, accent, progress, true, Modifier.fillMaxWidth())
        PlaybackControls(isPlaying, progress, accent, onTogglePlayback, onNextMoment)
        ContinuityInstruction(
            title = "Open the Fold",
            body = "Keep this moment selected. The same number and progress must arrive on the inner canvas.",
            accent = accent,
        )
        DeviceTelemetry(foldSnapshot, WindowMode.Compact, accent)
    }
}

@Composable
private fun InnerScene(
    moment: ContinuityMoment,
    selectedMoment: Int,
    accent: Color,
    progress: Float,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onSelectMoment: (Int) -> Unit,
    foldSnapshot: FoldSnapshot,
    wide: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            HeroCanvas(
                moment,
                accent,
                progress,
                false,
                Modifier.weight(if (wide) 1.45f else 1.2f),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "THE CANVAS GREW.\nTHE THOUGHT DIDN'T MOVE.",
                    color = TextSecondary,
                    fontSize = if (wide) 12.sp else 10.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 17.sp,
                    letterSpacing = 1.sp,
                )
                moments.forEachIndexed { index, item ->
                    MomentCard(
                        moment = item,
                        selected = index == selectedMoment,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelectMoment(index)
                        },
                    )
                }
                PlaybackControls(
                    isPlaying,
                    progress,
                    accent,
                    onTogglePlayback,
                    { onSelectMoment(nextMomentIndex(selectedMoment, moments.size)) },
                )
            }
        }
        if (foldSnapshot.posture == FoldPosture.HalfOpened) {
            ContinuityInstruction(
                "Flex posture detected",
                "The scene stays active while controls remain separated from the primary canvas.",
                accent,
            )
        }
        DeviceTelemetry(
            foldSnapshot,
            if (wide) WindowMode.Expanded else WindowMode.Medium,
            accent,
        )
    }
}

@Composable
private fun HeroCanvas(
    moment: ContinuityMoment,
    accent: Color,
    progress: Float,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "continuityProgress",
    )
    val sceneScale by animateFloatAsState(
        targetValue = if (compact) 0.94f else 1f,
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "continuityScale",
    )

    Surface(
        modifier = modifier.graphicsLayer { scaleX = sceneScale; scaleY = sceneScale },
        color = FLabSurface.copy(alpha = 0.94f),
        shape = RoundedCornerShape(if (compact) 30.dp else 36.dp),
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(if (compact) 20.dp else 26.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "MOMENT ${moment.number}",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                )
                Text(
                    "${(animatedProgress * 100).roundToInt()}%",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            OrbitVisual(
                moment,
                accent,
                animatedProgress,
                compact,
                Modifier.fillMaxWidth().height(if (compact) 260.dp else 340.dp),
            )
            Text(
                moment.eyebrow,
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                moment.title,
                fontSize = if (compact) 29.sp else 34.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = if (compact) 32.sp else 38.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(moment.detail, color = TextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun OrbitVisual(
    moment: ContinuityMoment,
    accent: Color,
    progress: Float,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radius = size.minDimension * if (compact) 0.31f else 0.34f
            val center = Offset(size.width / 2f, size.height / 2f)
            val angle = (progress * 360f - 90f) * (Math.PI / 180f)

            drawCircle(accent.copy(alpha = 0.06f), radius * 1.28f, center)
            drawCircle(
                Color.White.copy(alpha = 0.08f),
                radius,
                center,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                color = accent,
                radius = 7.dp.toPx(),
                center = Offset(
                    center.x + cos(angle).toFloat() * radius,
                    center.y + sin(angle).toFloat() * radius,
                ),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                moment.number,
                color = accent,
                fontSize = if (compact) 58.sp else 72.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                "CONTINUOUS STATE",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    progress: Float,
    accent: Color,
    onTogglePlayback: () -> Unit,
    onNextMoment: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(SurfaceRaised.copy(alpha = 0.88f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(22.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(
            if (isPlaying) "PAUSE" else "PLAY",
            accent,
            true,
            Modifier.weight(1f),
        ) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onTogglePlayback()
        }
        ControlButton("NEXT", accent, false, Modifier.weight(1f)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onNextMoment()
        }
        Text(
            "${(progress * 100).roundToInt()}%",
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun ControlButton(
    label: String,
    accent: Color,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (emphasized) accent else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (emphasized) Color(0xFF090B0F) else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun MomentCard(moment: ContinuityMoment, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) moment.accent.copy(alpha = 0.12f) else SurfaceRaised.copy(alpha = 0.76f))
            .border(
                1.dp,
                if (selected) moment.accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.05f),
                RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(moment.number, color = moment.accent, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Column {
            Text(moment.eyebrow, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                if (selected) "Active across screens" else "Tap to select",
                color = TextSecondary,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun ContinuityInstruction(title: String, body: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.24f), RoundedCornerShape(22.dp))
            .padding(17.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(accent))
        Column {
            Text(title, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(body, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun DeviceTelemetry(foldSnapshot: FoldSnapshot, mode: WindowMode, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(
            "CONTINUITY TELEMETRY",
            color = TextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TelemetryValue("SURFACE", mode.label, accent)
            TelemetryValue("HINGE", foldSnapshot.posture.label, Violet)
            TelemetryValue("AXIS", foldSnapshot.orientation, Color(0xFFFFD37A))
        }
    }
}

@Composable
private fun TelemetryValue(label: String, value: String, accent: Color) {
    Column {
        Text(label, color = TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        Text(value, color = accent, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
