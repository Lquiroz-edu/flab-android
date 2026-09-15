package com.lquiroz.flab.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lquiroz.flab.fold.FoldSnapshot
import com.lquiroz.flab.ui.theme.Cyan
import com.lquiroz.flab.ui.theme.Surface
import com.lquiroz.flab.ui.theme.SurfaceRaised
import com.lquiroz.flab.ui.theme.TextSecondary
import com.lquiroz.flab.ui.theme.Violet

enum class WindowMode(val label: String) {
    Compact("Cover"),
    Medium("Medium"),
    Expanded("Main display"),
}

fun windowModeForWidth(widthDp: Int): WindowMode = when {
    widthDp < 600 -> WindowMode.Compact
    widthDp < 840 -> WindowMode.Medium
    else -> WindowMode.Expanded
}

@Composable
fun FLabHomeScreen(foldSnapshot: FoldSnapshot) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF15202B), Color(0xFF090B0F)),
                    radius = 1200f,
                ),
            ),
    ) {
        val mode = windowModeForWidth(maxWidth.value.toInt())
        val horizontalPadding = if (mode == WindowMode.Compact) 20.dp else 40.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = 20.dp),
        ) {
            Header(mode)
            Spacer(Modifier.height(36.dp))

            if (mode == WindowMode.Expanded) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    MotionCard(Modifier.weight(1.35f))
                    StatusColumn(foldSnapshot, mode, Modifier.weight(1f))
                }
            } else {
                MotionCard(Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                StatusColumn(foldSnapshot, mode, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Header(mode: WindowMode) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "F/LAB",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
            )
            Text(
                text = "Foldable experience laboratory",
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }
        ModePill(mode.label)
    }
}

@Composable
private fun ModePill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(Cyan.copy(alpha = 0.12f))
            .border(1.dp, Cyan.copy(alpha = 0.45f), RoundedCornerShape(100.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label.uppercase(), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MotionCard(modifier: Modifier = Modifier) {
    var opened by rememberSaveable { mutableStateOf(false) }
    val deviceWidth by animateDpAsState(
        targetValue = if (opened) 270.dp else 142.dp,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "deviceWidth",
    )
    val accent by animateColorAsState(
        targetValue = if (opened) Cyan else Violet,
        animationSpec = tween(650),
        label = "accent",
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Surface.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
            .clickable { opened = !opened }
            .padding(24.dp),
    ) {
        Text("MOTION 001", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text("Continuity, without the reset.", fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(26.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(deviceWidth)
                    .height(190.dp)
                    .clip(RoundedCornerShape(if (opened) 20.dp else 30.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(accent.copy(alpha = 0.75f), Color(0xFF202835)),
                        ),
                    )
                    .border(2.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(24.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(1.dp)
                        .height(150.dp)
                        .background(Color.White.copy(alpha = if (opened) 0.2f else 0f)),
                )
            }
        }
        Text(
            text = if (opened) "Tap to preview the cover state" else "Tap to preview the open state",
            color = TextSecondary,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun StatusColumn(
    foldSnapshot: FoldSnapshot,
    mode: WindowMode,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("LIVE DEVICE", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        StatusCard("Window", mode.label, Cyan)
        StatusCard("Hinge", foldSnapshot.posture.label, Violet)
        StatusCard("Orientation", foldSnapshot.orientation, Color(0xFFFFD37A))
        StatusCard("Separating", if (foldSnapshot.isSeparating) "Yes" else "No", Color(0xFF8DFFA8))
    }
}

@Composable
private fun StatusCard(title: String, value: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceRaised.copy(alpha = 0.82f))
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = accent, fontWeight = FontWeight.SemiBold)
    }
}
