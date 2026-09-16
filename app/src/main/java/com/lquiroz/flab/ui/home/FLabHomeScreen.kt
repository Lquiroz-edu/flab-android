package com.lquiroz.flab.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lquiroz.flab.fold.FoldSnapshot
import com.lquiroz.flab.ui.theme.Cyan
import com.lquiroz.flab.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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

fun virtualCanvasScale(mode: WindowMode): Float = when (mode) {
    WindowMode.Compact -> 2.12f
    WindowMode.Medium,
    WindowMode.Expanded,
    -> 1f
}

fun hingeOpenFraction(angle: Float?, mode: WindowMode): Float = when {
    angle != null -> (angle / 180f).coerceIn(0f, 1f)
    mode == WindowMode.Compact -> 0f
    else -> 1f
}

private data class PanoramaPalette(
    val name: String,
    val sky: List<Color>,
    val sun: Color,
    val ridgeFar: Color,
    val ridgeNear: Color,
    val foreground: Color,
    val accent: Color,
)

private val palettes = listOf(
    PanoramaPalette(
        name = "DUSK",
        sky = listOf(Color(0xFF8583B6), Color(0xFFD9A3A5), Color(0xFFF3C8B8)),
        sun = Color(0xFFFFDFB2),
        ridgeFar = Color(0xFF4E526E),
        ridgeNear = Color(0xFF272B3D),
        foreground = Color(0xFFF1C8BB),
        accent = Color(0xFFFFD6B7),
    ),
    PanoramaPalette(
        name = "AURORA",
        sky = listOf(Color(0xFF1E355A), Color(0xFF3E8C9D), Color(0xFF9DE4C7)),
        sun = Color(0xFFD8FFF3),
        ridgeFar = Color(0xFF224E62),
        ridgeNear = Color(0xFF122D41),
        foreground = Color(0xFF8BD6C5),
        accent = Cyan,
    ),
    PanoramaPalette(
        name = "EMBER",
        sky = listOf(Color(0xFF442742), Color(0xFFB55255), Color(0xFFFFA568)),
        sun = Color(0xFFFFE0A3),
        ridgeFar = Color(0xFF713844),
        ridgeNear = Color(0xFF3B2534),
        foreground = Color(0xFFE68160),
        accent = Color(0xFFFFC078),
    ),
)

@Composable
fun FLabHomeScreen(foldSnapshot: FoldSnapshot, hingeAngle: Float?) {
    var paletteIndex by rememberSaveable { mutableIntStateOf(0) }
    val palette = palettes[paletteIndex]
    val haptic = LocalHapticFeedback.current
    var time by remember { mutableStateOf(LocalTime.now()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            time = LocalTime.now()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090D)),
    ) {
        val mode = windowModeForWidth(maxWidth.value.toInt())
        val openFraction = hingeOpenFraction(hingeAngle, mode)
        val settle = remember { Animatable(1f) }
        var previousMode by rememberSaveable { mutableStateOf(mode.name) }

        LaunchedEffect(mode) {
            if (previousMode != mode.name) {
                settle.snapTo(0f)
                settle.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(170, easing = FastOutSlowInEasing),
                )
                previousMode = mode.name
            }
        }

        val sceneScale = if (mode == WindowMode.Compact) {
            lerp(1.018f, 1f, settle.value)
        } else {
            lerp(0.985f, 1f, settle.value)
        }
        val sceneAlpha = lerp(0.92f, 1f, settle.value)
        val accent by animateColorAsState(
            targetValue = palette.accent,
            animationSpec = tween(350),
            label = "panoramaAccent",
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(
                    horizontal = if (mode == WindowMode.Compact) 12.dp else 18.dp,
                    vertical = 10.dp,
                ),
        ) {
            LabHeader(mode = mode, accent = accent)
            Spacer(Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = sceneScale
                        scaleY = sceneScale
                        alpha = sceneAlpha
                    }
                    .clip(RoundedCornerShape(if (mode == WindowMode.Compact) 34.dp else 40.dp))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.14f),
                        RoundedCornerShape(if (mode == WindowMode.Compact) 34.dp else 40.dp),
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        paletteIndex = (paletteIndex + 1) % palettes.size
                    },
            ) {
                SharedPanorama(
                    palette = palette,
                    mode = mode,
                    openFraction = openFraction,
                    modifier = Modifier.fillMaxSize(),
                )
                LockSceneOverlay(
                    time = time,
                    palette = palette,
                    mode = mode,
                    openFraction = openFraction,
                    hingeAngle = hingeAngle,
                    foldSnapshot = foldSnapshot,
                )
            }

            Spacer(Modifier.height(10.dp))
            TestStrip(
                mode = mode,
                palette = palette,
                hingeAngle = hingeAngle,
                onNextScene = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    paletteIndex = (paletteIndex + 1) % palettes.size
                },
            )
        }
    }
}

@Composable
private fun LabHeader(mode: WindowMode, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "F/LAB",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = "DUO TRANSITION · ALPHA 02",
                color = TextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(accent.copy(alpha = 0.12f))
                .border(1.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(100.dp))
                .padding(horizontal = 11.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
            Text(
                text = mode.label.uppercase(),
                color = accent,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun SharedPanorama(
    palette: PanoramaPalette,
    mode: WindowMode,
    openFraction: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val worldWidth = size.width * virtualCanvasScale(mode)
        val viewportOffset = (worldWidth - size.width) / 2f
        fun worldX(fraction: Float): Float = worldWidth * fraction - viewportOffset

        drawRect(
            brush = Brush.verticalGradient(palette.sky),
            size = size,
        )

        drawCircle(
            color = palette.sun.copy(alpha = 0.84f),
            radius = size.minDimension * 0.14f,
            center = Offset(worldX(0.72f), size.height * 0.28f),
        )
        drawCircle(
            color = palette.sun.copy(alpha = 0.12f),
            radius = size.minDimension * 0.24f,
            center = Offset(worldX(0.72f), size.height * 0.28f),
        )

        val farRidge = Path().apply {
            moveTo(worldX(0f), size.height)
            lineTo(worldX(0f), size.height * 0.61f)
            lineTo(worldX(0.12f), size.height * 0.48f)
            lineTo(worldX(0.25f), size.height * 0.58f)
            lineTo(worldX(0.38f), size.height * 0.42f)
            lineTo(worldX(0.52f), size.height * 0.56f)
            lineTo(worldX(0.68f), size.height * 0.40f)
            lineTo(worldX(0.82f), size.height * 0.55f)
            lineTo(worldX(1f), size.height * 0.46f)
            lineTo(worldX(1f), size.height)
            close()
        }
        drawPath(farRidge, palette.ridgeFar.copy(alpha = 0.78f))

        val nearRidge = Path().apply {
            moveTo(worldX(0f), size.height)
            lineTo(worldX(0f), size.height * 0.68f)
            lineTo(worldX(0.18f), size.height * 0.57f)
            lineTo(worldX(0.32f), size.height * 0.64f)
            lineTo(worldX(0.49f), size.height * 0.52f)
            lineTo(worldX(0.66f), size.height * 0.65f)
            lineTo(worldX(0.84f), size.height * 0.54f)
            lineTo(worldX(1f), size.height * 0.62f)
            lineTo(worldX(1f), size.height)
            close()
        }
        drawPath(nearRidge, palette.ridgeNear.copy(alpha = 0.94f))

        val foreground = Path().apply {
            moveTo(worldX(0f), size.height)
            lineTo(worldX(0f), size.height * 0.78f)
            cubicTo(
                worldX(0.23f), size.height * 0.69f,
                worldX(0.38f), size.height * 0.86f,
                worldX(0.57f), size.height * 0.76f,
            )
            cubicTo(
                worldX(0.75f), size.height * 0.67f,
                worldX(0.88f), size.height * 0.82f,
                worldX(1f), size.height * 0.72f,
            )
            lineTo(worldX(1f), size.height)
            close()
        }
        drawPath(foreground, palette.foreground)

        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, Color.Black.copy(alpha = 0.18f)),
                startY = size.height * 0.55f,
                endY = size.height,
            ),
            size = size,
        )

        if (mode != WindowMode.Compact) {
            val creaseHalfWidth = size.width * 0.055f
            val creaseAlpha = 0.06f + (1f - openFraction) * 0.26f
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = creaseAlpha),
                        Color.White.copy(alpha = creaseAlpha * 0.48f),
                        Color.Transparent,
                    ),
                    startX = size.width / 2f - creaseHalfWidth,
                    endX = size.width / 2f + creaseHalfWidth,
                ),
                topLeft = Offset(size.width / 2f - creaseHalfWidth, 0f),
                size = Size(creaseHalfWidth * 2f, size.height),
            )
        }
    }
}

@Composable
private fun LockSceneOverlay(
    time: LocalTime,
    palette: PanoramaPalette,
    mode: WindowMode,
    openFraction: Float,
    hingeAngle: Float?,
    foldSnapshot: FoldSnapshot,
) {
    val timeText = time.format(DateTimeFormatter.ofPattern("HH:mm"))
    val dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM"))

    Box(modifier = Modifier.fillMaxSize()) {
        if (mode == WindowMode.Compact) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 62.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = dateText.uppercase(),
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                )
                Text(
                    text = timeText,
                    color = Color.White,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-2).sp,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 42.dp, end = 44.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = dateText.uppercase(),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp,
                )
                Text(
                    text = timeText,
                    color = Color.White,
                    fontSize = 58.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-2).sp,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp),
        ) {
            Text(
                text = "SCENE ${palette.name}",
                color = palette.accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = if (mode == WindowMode.Compact) {
                    "Center crop · open to reveal"
                } else {
                    "Shared canvas · sides revealed"
                },
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = hingeAngle?.let { "${it.toInt()}°" } ?: foldSnapshot.posture.label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "OPEN ${(openFraction * 100).toInt()}%",
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
    }
}

@Composable
private fun TestStrip(
    mode: WindowMode,
    palette: PanoramaPalette,
    hingeAngle: Float?,
    onNextScene: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF141821))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(20.dp))
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(13.dp))
                .background(palette.accent)
                .clickable(onClick = onNextScene)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "CHANGE SCENE",
                color = Color(0xFF090B0F),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.7.sp,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (mode == WindowMode.Compact) "OPEN THE FOLD" else "CLOSE THE FOLD",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (hingeAngle == null) "Window fallback active" else "Live hinge sensor active",
                color = TextSecondary,
                fontSize = 9.sp,
            )
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)
