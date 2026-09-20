package com.lquiroz.flab.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lquiroz.flab.core.ModuleId
import com.lquiroz.flab.core.PowerPosture
import com.lquiroz.flab.core.PowerSignal
import com.lquiroz.flab.core.SetupProgress
import com.lquiroz.flab.core.SetupStep
import com.lquiroz.flab.core.SetupStepId
import com.lquiroz.flab.profiles.TreatmentMode
import com.lquiroz.flab.ui.FLabScreen
import com.lquiroz.flab.ui.FLabUiState
import com.lquiroz.flab.ui.components.Dot
import com.lquiroz.flab.ui.components.FLabButton
import com.lquiroz.flab.ui.components.FLabCard
import com.lquiroz.flab.ui.components.FLabGlassCard
import com.lquiroz.flab.ui.components.FeatureTile
import com.lquiroz.flab.ui.components.Pill
import com.lquiroz.flab.ui.components.SectionLabel
import com.lquiroz.flab.ui.components.TileArrow
import com.lquiroz.flab.ui.theme.FLabColors
import com.lquiroz.flab.ui.theme.FLabTokens

/**
 * The control centre (DoD 10).
 *
 * A hub, not a settings panel (DoD 40): a hero card for the device and its overall health, three
 * icon tiles grouping Fold Motion/Continuity, Immersive/System effects and Apps by what they do
 * rather than one row per switch, and a Performance card below. Nothing here is a raw on/off row —
 * a tile's subtitle explains its own state, and the detail one tap deeper on the screen it opens.
 *
 * The health line at the top is DoD 43: it says F/LAB Active when everything is running, Action
 * required when a permission is missing or the breaker has tripped, and always explains itself
 * rather than leaving a silent amber dot.
 */
@Composable
fun HomeScreen(
    ui: FLabUiState,
    wide: Boolean,
    onNavigate: (FLabScreen) -> Unit,
    onToggleEngine: (Boolean) -> Unit,
    onBeginSetup: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val setupSteps = remember(
        ui.configuration.enabled,
        ui.systemEffects.hasOverlayPermission,
        ui.systemEffects.accessibilityEnabled,
    ) {
        SetupProgress.steps(
            engineEnabled = ui.configuration.enabled,
            hasOverlayPermission = ui.systemEffects.hasOverlayPermission,
            hasAccessibilityPermission = ui.systemEffects.accessibilityEnabled,
        )
    }
    val setupComplete = SetupProgress.isComplete(setupSteps)

    Column(modifier = modifier.fillMaxWidth()) {
        HomeHeader(ui, onToggleEngine)
        Spacer(Modifier.height(24.dp))

        // Shown until the three steps are done, then gone for good — the point of a checklist is
        // that it stops asking once there is nothing left to ask (DoD 18).
        AnimatedVisibility(
            visible = !setupComplete,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                SetupCard(
                    steps = setupSteps,
                    onBeginSetup = onBeginSetup,
                    onOpenOverlaySettings = onOpenOverlaySettings,
                    onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        AnimatedVisibility(
            visible = ui.needsAttention,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                AttentionCard(ui, onNavigate)
                Spacer(Modifier.height(16.dp))
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HeroCard(ui, onNavigate)
            FeatureTiles(ui, wide, onNavigate)
            PerformanceCard(ui, onNavigate)
            SecondaryLinks(ui, onNavigate)
        }
    }
}

@Composable
private fun HomeHeader(ui: FLabUiState, onToggleEngine: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text("F/LAB", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(healthColor(ui))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = ui.healthLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = healthColor(ui),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Pill(
            text = if (ui.configuration.enabled) "On" else "Off",
            accent = if (ui.configuration.enabled) MaterialTheme.colorScheme.primary else FLabColors.textSecondary,
            filled = ui.configuration.enabled,
            onClick = { onToggleEngine(!ui.configuration.enabled) },
        )
    }
}

/**
 * The device hero: what F/LAB is running on, and one sentence on how it is doing right now —
 * Home's front page rather than a "Device" settings row. The 3D fold illustration sits as a
 * sibling of the card, not a child of it, so it can spill past the card's rounded corner instead
 * of being clipped to it.
 */
@Composable
private fun HeroCard(ui: FLabUiState, onNavigate: (FLabScreen) -> Unit) {
    val device = ui.device
    Box(Modifier.fillMaxWidth()) {
        FLabCard(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 248.dp),
            contentPadding = 22,
        ) {
            Column(Modifier.widthIn(max = 190.dp)) {
                SectionLabel("Your Fold")
                Spacer(Modifier.height(10.dp))
                Text(
                    text = device?.displayName ?: "Detecting your device",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        device == null -> "Reading device information."
                        !ui.configuration.enabled -> "Turn F/LAB on to start following the hinge."
                        device.isFoldable && device.hasHingeSensor ->
                            "Continuity, motion and immersive behaviour are active."
                        device.isFoldable ->
                            "Foldable detected, posture events only — motion will be coarser."
                        else -> "No hinge reported — Fold Motion has nothing to follow."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = FLabColors.textSecondary,
                )
                Spacer(Modifier.height(16.dp))
                Pill(
                    text = if (ui.needsAttention) "Action required" else "All systems working",
                    accent = if (ui.needsAttention) FLabColors.warning else FLabColors.ok,
                    onClick = { onNavigate(FLabScreen.Diagnostics) },
                )
            }
        }
        FoldDeviceIllustration(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 14.dp, y = 18.dp)
                .size(width = 148.dp, height = 186.dp),
        )
    }
}

/**
 * A small, static illustration of the folded device — the camera panel, the hinge and the screen,
 * tilted with a real perspective transform (`graphicsLayer`'s rotationY/rotationX/cameraDistance)
 * rather than a traced photo or an imported image. One composition, no per-frame cost, and nothing
 * to fetch: exactly the kind of asset DoD 22/23's "no per-frame cost, no network" discipline favours
 * over a bundled render.
 */
@Composable
private fun FoldDeviceIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.graphicsLayer {
            rotationY = -20f
            rotationX = 5f
            cameraDistance = 14f * density
        },
    ) {
        Row(Modifier.fillMaxSize()) {
            // Back panel: the camera module side.
            Box(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            bottomStart = 18.dp,
                            topEnd = 3.dp,
                            bottomEnd = 3.dp,
                        ),
                    )
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFE7EAEE), Color(0xFFB7BCC4), Color(0xFF90969F)),
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 14.dp, start = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    repeat(3) {
                        Box(
                            Modifier
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(listOf(Color(0xFF4A4D52), Color(0xFF0C0D0F))),
                                ),
                        )
                    }
                    Box(
                        Modifier
                            .padding(start = 3.dp, top = 1.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDFE4EA)),
                    )
                }
            }
            // Hinge.
            Box(
                modifier = Modifier
                    .weight(0.05f)
                    .fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Color(0xFF4A4D52), Color(0xFF1A1C1F)))),
            )
            // Front panel: the screen, lit from inside like it is mid-boot.
            Box(
                modifier = Modifier
                    .weight(0.53f)
                    .fillMaxHeight()
                    .clip(
                        RoundedCornerShape(
                            topStart = 3.dp,
                            bottomStart = 3.dp,
                            topEnd = 18.dp,
                            bottomEnd = 18.dp,
                        ),
                    )
                    .background(Brush.linearGradient(listOf(Color(0xFF191C22), Color(0xFF030406)))),
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth(0.75f)
                        .fillMaxHeight(0.6f)
                        .background(
                            Brush.radialGradient(listOf(FLabTokens.Violet.copy(alpha = 0.40f), Color.Transparent)),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth(0.7f)
                        .fillMaxHeight(0.55f)
                        .background(
                            Brush.radialGradient(listOf(FLabTokens.Cyan.copy(alpha = 0.32f), Color.Transparent)),
                        ),
                )
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 9.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0C0D0F)),
                )
                Text(
                    text = "Unfold\nMore",
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = 11.dp),
                )
            }
        }
    }
}

/**
 * The three hub tiles: Fold (motion + continuity), Experience (immersive + the system-wide
 * effect) and Apps (per-app treatment) — the groupings DoD 10 lists, read as a product rather than
 * four separate module switches.
 */
@Composable
private fun FeatureTiles(ui: FLabUiState, wide: Boolean, onNavigate: (FLabScreen) -> Unit) {
    val foldRunning = ui.state.isModuleRunning(ModuleId.FoldMotion)
    val foldAccent = when {
        !ui.configuration.enabled -> FLabColors.textSecondary
        foldRunning -> MaterialTheme.colorScheme.primary
        else -> FLabColors.warning
    }
    val foldSubtitle = when {
        !ui.configuration.enabled -> "Turn F/LAB on to enable this."
        foldRunning -> "Following the hinge."
        else -> ui.state.moduleStates[ModuleId.FoldMotion]?.unavailableReason
            ?: "Hinge motion and continuity."
    }

    val effects = ui.systemEffects
    val experienceAccent = when {
        !effects.enabled -> FLabColors.textSecondary
        !effects.canRun -> FLabColors.warning
        else -> MaterialTheme.colorScheme.secondary
    }
    val experienceSubtitle = when {
        !effects.enabled -> "Immersive mode and system effects."
        !effects.canRun -> "Needs: ${effects.missing.joinToString(" and ")}"
        else -> "Active across the system."
    }

    val strongest = ui.appProfiles.firstOrNull { it.immersive == TreatmentMode.On }
    val appsSubtitle = strongest?.let { "${it.displayName} has the strongest treatment." }
        ?: "${ui.configuredAppCount} apps configured."

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 12.dp),
    ) {
        FeatureTile(
            title = "Fold",
            subtitle = foldSubtitle,
            accent = foldAccent,
            onClick = { onNavigate(FLabScreen.FoldMotion) },
            modifier = Modifier.weight(1f),
        ) { tint -> FoldGlyphIcon(tint) }
        FeatureTile(
            title = "Experience",
            subtitle = experienceSubtitle,
            accent = experienceAccent,
            onClick = { onNavigate(FLabScreen.Access) },
            modifier = Modifier.weight(1f),
        ) { tint -> OverlapGlyphIcon(tint) }
        FeatureTile(
            title = "Apps",
            subtitle = appsSubtitle,
            accent = MaterialTheme.colorScheme.secondary,
            onClick = { onNavigate(FLabScreen.Apps) },
            modifier = Modifier.weight(1f),
        ) { tint -> GridGlyphIcon(tint) }
    }
}

/** The performance mode as one row-card, matching the tiles' "state explains itself" reading. */
@Composable
private fun PerformanceCard(ui: FLabUiState, onNavigate: (FLabScreen) -> Unit) {
    val accent = when (ui.state.power) {
        PowerPosture.Normal -> FLabColors.ok
        PowerPosture.Conserving -> FLabColors.warning
        PowerPosture.Restricted -> FLabColors.danger
    }
    // Names the signal, not just the posture: "conserving" on its own is nothing the user can act
    // on, while "battery saver is on" is (DoD 43).
    val detail = when (ui.state.power) {
        PowerPosture.Normal -> ui.profile.id.summary
        PowerPosture.Conserving -> when (ui.state.powerSignal) {
            PowerSignal.BatterySaver -> "Battery saver is on — motion runs without blur or dim"
            else -> "The device is warm — motion runs without blur or dim"
        }
        PowerPosture.Restricted -> "Paused while the device cools down"
    }
    FLabCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onNavigate(FLabScreen.Profiles) },
        contentPadding = 16,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                BarsGlyphIcon(accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Performance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = " · ${ui.profile.id.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall, color = FLabColors.textSecondary)
            }
            Spacer(Modifier.width(12.dp))
            TileArrow()
        }
    }
}

/** The less central screens — opt-in and support features — as compact chips, not full rows. */
@Composable
private fun SecondaryLinks(ui: FLabUiState, onNavigate: (FLabScreen) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pill(
            text = if (ui.configuration.experimentsEnabled) "Experiments · On" else "Experiments",
            accent = if (ui.configuration.experimentsEnabled) FLabColors.warning else FLabColors.textSecondary,
            onClick = { onNavigate(FLabScreen.Experiments) },
        )
        Pill(
            text = "Diagnostics",
            accent = FLabColors.textSecondary,
            onClick = { onNavigate(FLabScreen.Diagnostics) },
        )
    }
}

/**
 * The guided activation checklist (DoD 18, 43): the single visible path from "just installed" to
 * "genuinely running", instead of the three separate discoveries — the Home pill, F/LAB Access,
 * the System effects toggle — that used to be scattered across screens with nothing tying them
 * together into one obvious next action.
 *
 * The one place [FLabGlassCard] is used outside the wallpaper: this is the single most important
 * thing on the screen while it is showing, which is exactly the case DoD 40's "few visible
 * settings at once" reserves it for.
 */
@Composable
private fun SetupCard(
    steps: List<SetupStep>,
    onBeginSetup: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
) {
    val remaining = steps.count { !it.done }
    FLabGlassCard(Modifier.fillMaxWidth()) {
        SectionLabel("Get F/LAB fully working")
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (remaining == 1) "One step left" else "$remaining steps left",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Fold Motion, Continuity and the system-wide effect all come from these same " +
                "switches. Once they are done you will not need to come back here for them.",
            style = MaterialTheme.typography.bodySmall,
            color = FLabColors.textSecondary,
        )
        Spacer(Modifier.height(18.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            steps.forEach { SetupStepRow(it) }
        }
        Spacer(Modifier.height(18.dp))
        when (SetupProgress.nextStep(steps)?.id) {
            SetupStepId.EngineOn -> FLabButton(
                text = "Turn F/LAB on",
                onClick = onBeginSetup,
                modifier = Modifier.fillMaxWidth(),
            )
            SetupStepId.OverlayPermission -> FLabButton(
                text = "Allow display over other apps",
                onClick = onOpenOverlaySettings,
                modifier = Modifier.fillMaxWidth(),
            )
            SetupStepId.AccessibilityPermission -> FLabButton(
                text = "Allow app awareness",
                onClick = onOpenAccessibilitySettings,
                modifier = Modifier.fillMaxWidth(),
            )
            null -> Unit
        }
    }
}

@Composable
private fun SetupStepRow(step: SetupStep) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(if (step.done) FLabColors.ok else FLabColors.textSecondary, size = if (step.done) 10 else 8)
        Spacer(Modifier.width(10.dp))
        Text(
            text = step.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (step.done) MaterialTheme.colorScheme.onSurface else FLabColors.textSecondary,
            fontWeight = if (step.done) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun healthColor(ui: FLabUiState): Color = when {
    !ui.configuration.enabled -> FLabColors.textSecondary
    ui.needsAttention -> FLabColors.warning
    else -> FLabColors.ok
}

/** DoD 43: a module that is off must explain itself, not just go grey. */
@Composable
private fun AttentionCard(ui: FLabUiState, onNavigate: (FLabScreen) -> Unit) {
    val tripped = ui.state.moduleStates.entries.filter { it.value.disabledByBreaker }
    FLabCard(Modifier.fillMaxWidth()) {
        SectionLabel("Action required")
        Spacer(Modifier.height(10.dp))
        tripped.forEach { (module, runtime) ->
            Text(
                text = "${module.displayName} switched itself off after repeated failures.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            runtime.lastErrorMessage?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = FLabColors.textSecondary)
            }
            Spacer(Modifier.height(12.dp))
        }
        FLabButton(
            text = "Open Diagnostics",
            onClick = { onNavigate(FLabScreen.Diagnostics) },
            prominent = false,
            accent = FLabColors.warning,
        )
    }
}

/** Two rounded panels side by side — a fold, open. */
@Composable
private fun FoldGlyphIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val gap = size.width * 0.16f
        val panelWidth = (size.width - gap) / 2f
        val corner = CornerRadius(size.width * 0.16f)
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = Size(panelWidth, size.height),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(panelWidth + gap, 0f),
            size = Size(panelWidth, size.height),
            cornerRadius = corner,
        )
    }
}

/** Two overlapping circles — the immersive layer meeting the system underneath it. */
@Composable
private fun OverlapGlyphIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val radius = size.width * 0.34f
        drawCircle(
            color = tint.copy(alpha = 0.55f),
            radius = radius,
            center = Offset(size.width * 0.40f, size.height * 0.5f),
        )
        drawCircle(
            color = tint,
            radius = radius,
            center = Offset(size.width * 0.62f, size.height * 0.5f),
        )
    }
}

/** A 2×2 grid of rounded squares — apps, individually treated. */
@Composable
private fun GridGlyphIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val cell = size.width * 0.40f
        val gap = size.width * 0.20f
        val corner = CornerRadius(size.width * 0.10f)
        for (row in 0..1) {
            for (col in 0..1) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = corner,
                )
            }
        }
    }
}

/** Three ascending bars — performance, at a glance. */
@Composable
private fun BarsGlyphIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(22.dp)) {
        val barWidth = size.width * 0.22f
        val gap = size.width * 0.12f
        val corner = CornerRadius(barWidth * 0.3f)
        listOf(0.45f, 0.7f, 1f).forEachIndexed { index, heightFraction ->
            val barHeight = size.height * heightFraction
            drawRoundRect(
                color = tint,
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
}
