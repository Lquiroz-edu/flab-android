package com.lquiroz.flab.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class SurfaceRole { Cover, Inner }

enum class WindowMode { Compact, Medium, Expanded }

fun windowModeForWidth(widthDp: Int): WindowMode = when {
    widthDp < 600 -> WindowMode.Compact
    widthDp < 840 -> WindowMode.Medium
    else -> WindowMode.Expanded
}

fun hingeOpenFraction(angle: Float?, mode: WindowMode): Float =
    angle?.div(180f)?.coerceIn(0f, 1f) ?: if (mode == WindowMode.Compact) 0f else 1f

fun frostAmount(progress: Float): Float =
    ((progress.coerceIn(0f, 1f) - 0.04f) / 0.68f).coerceIn(0f, 1f)

fun dashboardReveal(progress: Float): Float =
    ((progress.coerceIn(0f, 1f) - 0.18f) / 0.70f).coerceIn(0f, 1f)

@Composable
fun DuoSurface(
    role: SurfaceRole,
    unfoldProgress: Float,
    rawHingeAngle: Float?,
    coverWidthMm: Float,
    status: String,
) {
    val progress = unfoldProgress.coerceIn(0f, 1f)
    val displayMetrics = LocalView.current.resources.displayMetrics
    val density = LocalDensity.current
    val railWidth = with(density) {
        val pxPerMm = (displayMetrics.xdpi / 25.4f).takeIf { it > 0f } ?: density.density
        (coverWidthMm * pxPerMm).toDp()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (role == SurfaceRole.Cover) {
            CoverGlass(progress = progress)
        } else {
            InnerCanvas(
                progress = progress,
                railWidth = railWidth,
                rawHingeAngle = rawHingeAngle,
                status = status,
            )
        }
    }
}

@Composable
private fun CoverGlass(progress: Float) {
    val frost = frostAmount(progress)
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = -75f * progress
                cameraDistance = 18f * density
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
    ) {
        SharedRail(Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.White.copy(alpha = 0.50f * frost),
                    0.28f to Color.White.copy(alpha = 0.20f * frost),
                    1f to Color.White.copy(alpha = 0.03f * frost),
                ),
            )
            drawRect(Color.Black.copy(alpha = 0.16f * progress))
        }
    }
}

@Composable
private fun InnerCanvas(
    progress: Float,
    railWidth: Dp,
    rawHingeAngle: Float?,
    status: String,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val safeRailWidth = railWidth.coerceAtMost(maxWidth * 0.56f)
        val reveal = dashboardReveal(progress)
        val leftWidth = maxWidth - safeRailWidth

        Dashboard(
            modifier = Modifier
                .fillMaxHeight()
                .width(leftWidth)
                .align(Alignment.CenterStart)
                .graphicsLayer {
                    alpha = reveal
                    rotationY = -75f * (1f - progress)
                    cameraDistance = 22f * density
                    transformOrigin = TransformOrigin(1f, 0.5f)
                },
            progress = progress,
            rawHingeAngle = rawHingeAngle,
            status = status,
        )

        SharedRail(
            Modifier
                .fillMaxHeight()
                .width(safeRailWidth)
                .align(Alignment.CenterEnd),
        )

        Canvas(
            Modifier
                .fillMaxHeight()
                .width(34.dp)
                .align(Alignment.CenterEnd)
                .offset(x = -safeRailWidth),
        ) {
            drawRect(
                Brush.horizontalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.72f * (1f - reveal)),
                        Color.White.copy(alpha = 0.18f * (1f - progress)),
                        Color.Transparent,
                    ),
                ),
            )
        }
    }
}

@Composable
private fun SharedRail(modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF16192B), Color(0xFF0D1020), Color(0xFF070812)),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 22.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF7667FF).copy(alpha = 0.40f), Color.Transparent),
                    center = Offset(size.width * 0.82f, size.height * 0.12f),
                    radius = size.width * 0.9f,
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * 0.82f, size.height * 0.12f),
            )
        }

        Column(Modifier.fillMaxSize()) {
            Text(
                "F/LAB",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
            )
            Text(
                "DUO MOTION",
                color = Color(0xFFAAAFC4),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
            )

            Spacer(Modifier.weight(1f))
            Text(
                "11:00",
                color = Color.White,
                fontSize = 54.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                "El contenido permanece.\nEl dispositivo cambia alrededor.",
                color = Color(0xFFB7B9C8),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                listOf("01", "02", "03", "04").forEachIndexed { index, label ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(
                                    if (index == 1) Color(0xFF7667FF)
                                    else Color.White.copy(alpha = 0.09f),
                                )
                                .border(
                                    1.dp,
                                    Color.White.copy(alpha = 0.12f),
                                    RoundedCornerShape(15.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(7.dp))
                        Box(
                            Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (index == 1) Color(0xFF8D82FF) else Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashboard(
    modifier: Modifier,
    progress: Float,
    rawHingeAngle: Float?,
    status: String,
) {
    Column(
        modifier
            .background(Color(0xFF090B12))
            .statusBarsPadding()
            .padding(26.dp),
    ) {
        Text(
            "CONTINUIDAD",
            color = Color(0xFF8D82FF),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "La interfaz no salta.\nLa bisagra revela espacio.",
            color = Color.White,
            fontSize = 31.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(28.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetricCard("APERTURA", "${(progress * 100).roundToInt()}%", Modifier.weight(1f))
            MetricCard("BISAGRA", rawHingeAngle?.let { "${it.roundToInt()}°" } ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        MetricCard("SUPERFICIE", status.uppercase(), Modifier.fillMaxWidth())
        Spacer(Modifier.weight(1f))
        Text(
            "ALPHA 03 · DUAL-SURFACE PROBE",
            color = Color(0xFF7D8194),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF151824))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .padding(18.dp),
    ) {
        Text(label, color = Color(0xFF858A9D), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
