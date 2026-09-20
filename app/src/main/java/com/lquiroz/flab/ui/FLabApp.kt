package com.lquiroz.flab.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lquiroz.flab.ui.components.FLabButton
import com.lquiroz.flab.ui.motion.FoldMotionHost
import com.lquiroz.flab.ui.screens.AccessScreen
import com.lquiroz.flab.ui.screens.AppsScreen
import com.lquiroz.flab.ui.screens.DiagnosticsScreen
import com.lquiroz.flab.ui.screens.ExperimentsScreen
import com.lquiroz.flab.ui.screens.FoldMotionScreen
import com.lquiroz.flab.ui.screens.HomeScreen
import com.lquiroz.flab.ui.screens.OnboardingScreen
import com.lquiroz.flab.ui.screens.ProfilesScreen
import com.lquiroz.flab.ui.theme.FLabColors
import com.lquiroz.flab.ui.theme.FLabTokens

/**
 * The F/LAB shell.
 *
 * Two things worth knowing about this file:
 *
 *  1. The whole app is wrapped in [FoldMotionHost], so F/LAB's own screens get the treatment
 *     F/LAB is arguing for (DoD 41). If the motion is not good enough to use on itself, it is not
 *     good enough to ship.
 *  2. Layout adapts at 720 dp rather than the usual 600 dp. A Fold's inner display lands well
 *     above that, and the cover display well below, so the breakpoint falls in the gap instead of
 *     near either panel — a window that sits exactly on a breakpoint re-lays-out on every small
 *     resize during an unfold, which is the "contenido desplazado accidentalmente" of DoD 30.
 */
@Composable
fun FLabApp(
    viewModel: FLabViewModel,
    onShare: (String) -> Unit,
    onOpenSettings: (Intent) -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val previewProgress by viewModel.previewProgress.collectAsStateWithLifecycle()

    LaunchedEffect(ui.configuration.onboardingComplete) {
        if (!ui.configuration.onboardingComplete) viewModel.navigate(FLabScreen.Onboarding)
    }

    BackHandler(enabled = screen != FLabScreen.Home && screen != FLabScreen.Onboarding) {
        viewModel.back()
    }

    FoldMotionHost(
        evidence = viewModel.evidence,
        tuning = ui.effectiveMotion,
        enabled = ui.configuration.enabled,
        onSettled = viewModel::onMotionSettled,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            GlassBackdrop(modifier = Modifier.fillMaxSize())

            val wide = maxWidth >= WIDE_BREAKPOINT_DP.dp
            val horizontalPadding = if (wide) 40.dp else 20.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = horizontalPadding, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = MAX_CONTENT_WIDTH_DP.dp),
                ) {
                    ScreenContent(viewModel, ui, screen, previewProgress, wide, onShare, onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(
    viewModel: FLabViewModel,
    ui: FLabUiState,
    screen: FLabScreen,
    previewProgress: Float,
    wide: Boolean,
    onShare: (String) -> Unit,
    onOpenSettings: (Intent) -> Unit,
) {
    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            (
                slideInVertically(spring(stiffness = 380f)) { it / 14 } +
                    fadeIn(spring(stiffness = 380f))
                ) togetherWith fadeOut(spring(stiffness = 900f))
        },
        label = "screen",
    ) { current ->
        Column(Modifier.fillMaxWidth()) {
            when (current) {
                FLabScreen.Onboarding -> OnboardingScreen(onFinish = viewModel::completeOnboarding)

                FLabScreen.Home -> HomeScreen(
                    ui = ui,
                    wide = wide,
                    onNavigate = viewModel::navigate,
                    onToggleEngine = viewModel::setEnabled,
                    onBeginSetup = viewModel::beginGuidedSetup,
                    onOpenOverlaySettings = { onOpenSettings(viewModel.overlayPermissionIntent()) },
                    onOpenAccessibilitySettings = {
                        onOpenSettings(viewModel.accessibilitySettingsIntent())
                    },
                )

                FLabScreen.FoldMotion -> FoldMotionScreen(
                    profile = ui.profile,
                    progress = previewProgress,
                    onScrub = viewModel::scrubPreview,
                    hasHingeSensor = ui.device?.hasHingeSensor == true,
                    onSetLiveWallpaper = { onOpenSettings(viewModel.liveWallpaperIntent()) },
                )

                FLabScreen.Apps -> AppsScreen(
                    profiles = ui.appProfiles,
                    onCycleImmersive = viewModel::cycleImmersive,
                    onCycleContinuity = viewModel::cycleContinuity,
                )

                FLabScreen.Profiles -> ProfilesScreen(
                    active = ui.configuration.profileId,
                    onSelect = viewModel::setProfile,
                )

                FLabScreen.Experiments -> ExperimentsScreen(
                    enabled = ui.configuration.experimentsEnabled,
                    onToggle = viewModel::setExperimentsEnabled,
                )

                FLabScreen.Access -> AccessScreen(
                    requirements = remember(ui) { viewModel.accessRequirements() },
                    systemEffects = ui.systemEffects,
                    canPreview = ui.canPreviewSystemEffects,
                    onPreview = viewModel::previewSystemEffects,
                    onToggleSystemEffects = viewModel::setSystemEffectsEnabled,
                    onOpenOverlaySettings = { onOpenSettings(viewModel.overlayPermissionIntent()) },
                    onOpenAccessibilitySettings = {
                        onOpenSettings(viewModel.accessibilitySettingsIntent())
                    },
                    onOpenAppDetails = { onOpenSettings(viewModel.appDetailsIntent()) },
                )

                FLabScreen.Diagnostics -> DiagnosticsScreen(
                    // Keyed on ui so the snapshot follows the engine rather than freezing at the
                    // first composition. A Diagnostics screen showing a stale copy of the truth is
                    // worse than none.
                    snapshot = remember(ui) { viewModel.diagnostics() },
                    report = viewModel::debugReport,
                    onShare = onShare,
                    onClearModuleFailure = viewModel::clearModuleFailure,
                    onReset = viewModel::reset,
                )
            }

            if (current != FLabScreen.Home && current != FLabScreen.Onboarding) {
                Spacer(Modifier.height(24.dp))
                FLabButton(
                    text = "Back to F/LAB",
                    onClick = viewModel::back,
                    prominent = false,
                    accent = FLabColors.textSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * The colourful backdrop every screen sits over. Three soft, static colour blobs on the base
 * scheme gradient — this is what actually makes [com.lquiroz.flab.ui.components.FLabCard]'s
 * translucency read as glass rather than as a plain dimmed panel. It is one composition, redrawn
 * only on resize or theme change, never per frame, so it costs nothing while scrolling or during a
 * fold transition.
 */
@Composable
private fun GlassBackdrop(modifier: Modifier = Modifier) {
    val dark = FLabColors.isDark
    val blobAlpha = if (dark) 1f else 0.55f
    Box(modifier = modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surface,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(0.95f)
                .aspectRatio(1f)
                .offset((-90).dp, (-130).dp)
                .blur(120.dp)
                .background(
                    Brush.radialGradient(
                        listOf(FLabTokens.Violet.copy(alpha = 0.30f * blobAlpha), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .offset(110.dp, (-70).dp)
                .blur(120.dp)
                .background(
                    Brush.radialGradient(
                        listOf(FLabTokens.Cyan.copy(alpha = 0.26f * blobAlpha), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(1f)
                .aspectRatio(1.1f)
                .offset(y = 160.dp)
                .blur(140.dp)
                .background(
                    Brush.radialGradient(
                        listOf(FLabTokens.Amber.copy(alpha = 0.16f * blobAlpha), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )
    }
}

/** Chosen to sit in the gap between a Fold's cover and inner widths, not on top of either. */
private const val WIDE_BREAKPOINT_DP = 720

/** Long lines are hard to read; on the inner display the content column stops growing here. */
private const val MAX_CONTENT_WIDTH_DP = 1040
