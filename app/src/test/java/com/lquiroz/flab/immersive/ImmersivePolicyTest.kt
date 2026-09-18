package com.lquiroz.flab.immersive

import com.lquiroz.flab.compat.ImmersiveStrategy
import com.lquiroz.flab.core.EngineStatus
import com.lquiroz.flab.core.FLabState
import com.lquiroz.flab.core.ModuleId
import com.lquiroz.flab.core.ModuleRuntimeState
import com.lquiroz.flab.core.PowerPosture
import com.lquiroz.flab.core.WindowPresentation
import com.lquiroz.flab.core.WindowState
import com.lquiroz.flab.profiles.AppProfile
import com.lquiroz.flab.profiles.DefaultAppProfiles
import com.lquiroz.flab.profiles.TreatmentMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DoD 7, 14, 33, 46, 47 and 48, as a decision table. */
class ImmersivePolicyTest {

    private val runningState = FLabState(
        engineStatus = EngineStatus.Active,
        window = WindowState(presentation = WindowPresentation.FullScreen),
        moduleStates = ModuleId.entries.associateWith { ModuleRuntimeState(enabled = true) },
    )

    private fun reliable(context: AppContext) = ContextEstimate(context, confidence = 0.9f)

    private fun profile(packageName: String) =
        DefaultAppProfiles.forPackage(packageName)

    // -------------------------------------------------------------- Instagram (DoD 46)

    @Test
    fun `instagram reels receive the full treatment`() {
        val decision = ImmersivePolicy.decide(
            state = runningState,
            profile = profile(DefaultAppProfiles.INSTAGRAM),
            estimate = reliable(AppContext.VerticalVideo),
            appVersionCode = 300_000_000L,
        )

        assertEquals(ImmersiveStrategy.ImmersiveExtension, decision.strategy)
    }

    @Test
    fun `instagram stories get a gradient but not the full extension`() {
        val decision = ImmersivePolicy.decide(
            state = runningState,
            profile = profile(DefaultAppProfiles.INSTAGRAM),
            estimate = reliable(AppContext.Story),
            appVersionCode = 300_000_000L,
        )

        assertEquals(ImmersiveStrategy.GradientExtension, decision.strategy)
    }

    @Test
    fun `instagram feed is left alone`() {
        val decision = ImmersivePolicy.decide(
            state = runningState,
            profile = profile(DefaultAppProfiles.INSTAGRAM),
            estimate = reliable(AppContext.Feed),
            appVersionCode = 300_000_000L,
        )

        assertFalse(decision.isApplied)
    }

    @Test
    fun `instagram DM returns to conservative behaviour`() {
        val decision = ImmersivePolicy.decide(
            state = runningState,
            profile = profile(DefaultAppProfiles.INSTAGRAM),
            estimate = reliable(AppContext.Messaging),
            appVersionCode = 300_000_000L,
        )

        assertFalse(decision.isApplied)
    }

    // -------------------------------------------------------------- YouTube (DoD 47)

    @Test
    fun `youtube fullscreen is recognised as already correct`() {
        val decision = ImmersivePolicy.decide(
            state = runningState,
            profile = profile(DefaultAppProfiles.YOUTUBE),
            estimate = reliable(AppContext.FullscreenVideo),
            appVersionCode = 19_000_000L,
        )

        assertFalse(decision.isApplied)
        assertEquals(AbstainReason.AppAlreadyImmersive, decision.reason)
    }

    @Test
    fun `youtube shorts is skipped on auto and applied when asked for explicitly`() {
        val auto = ImmersivePolicy.decide(
            state = runningState,
            profile = profile(DefaultAppProfiles.YOUTUBE),
            estimate = reliable(AppContext.VerticalVideo),
            appVersionCode = 19_000_000L,
        )
        assertFalse("Auto must respect the rule's own judgement", auto.isApplied)
        assertEquals(AbstainReason.NoPerceptibleGain, auto.reason)

        val explicit = ImmersivePolicy.decide(
            state = runningState,
            profile = profile(DefaultAppProfiles.YOUTUBE).copy(immersive = TreatmentMode.On),
            estimate = reliable(AppContext.VerticalVideo),
            appVersionCode = 19_000_000L,
        )
        assertEquals(ImmersiveStrategy.ChromaticContinuity, explicit.strategy)
    }

    // -------------------------------------------------------------- safety gates

    @Test
    fun `an unreliable context is never acted on`() {
        val decision = ImmersivePolicy.decide(
            state = runningState,
            profile = profile(DefaultAppProfiles.INSTAGRAM),
            estimate = ContextEstimate(AppContext.VerticalVideo, confidence = 0.5f),
            appVersionCode = 300_000_000L,
        )

        assertFalse(decision.isApplied)
        assertEquals(AbstainReason.LowConfidence, decision.reason)
    }

    @Test
    fun `an unknown context is never acted on however confident`() {
        val decision = ImmersivePolicy.decide(
            state = runningState,
            profile = profile(DefaultAppProfiles.INSTAGRAM),
            estimate = ContextEstimate(AppContext.Unknown, confidence = 1f),
            appVersionCode = 300_000_000L,
        )

        assertFalse(decision.isApplied)
    }

    @Test
    fun `a banking app is refused even if the user turned immersion on`() {
        val bank = AppProfile(
            packageName = "com.bankinter.launcher",
            displayName = "Bankinter",
            immersive = TreatmentMode.On,
            continuity = TreatmentMode.On,
        )

        val decision = ImmersivePolicy.decide(
            state = runningState,
            profile = bank,
            estimate = reliable(AppContext.Feed),
            appVersionCode = 1L,
        )

        assertFalse(decision.isApplied)
        assertEquals(AbstainReason.ProtectedApp, decision.reason)
    }

    @Test
    fun `split screen disables immersion`() {
        val decision = ImmersivePolicy.decide(
            state = runningState.copy(
                window = WindowState(presentation = WindowPresentation.SplitScreen),
            ),
            profile = profile(DefaultAppProfiles.INSTAGRAM),
            estimate = reliable(AppContext.VerticalVideo),
            appVersionCode = 300_000_000L,
        )

        assertFalse(decision.isApplied)
        assertEquals(AbstainReason.MultiWindow, decision.reason)
    }

    @Test
    fun `picture in picture disables immersion`() {
        val decision = ImmersivePolicy.decide(
            state = runningState.copy(
                window = WindowState(presentation = WindowPresentation.PictureInPicture),
            ),
            profile = profile(DefaultAppProfiles.TIKTOK),
            estimate = reliable(AppContext.VerticalVideo),
            appVersionCode = 1L,
        )

        assertFalse(decision.isApplied)
    }

    @Test
    fun `power saving stands everything down`() {
        val decision = ImmersivePolicy.decide(
            state = runningState.copy(power = PowerPosture.Conserving),
            profile = profile(DefaultAppProfiles.INSTAGRAM),
            estimate = reliable(AppContext.VerticalVideo),
            appVersionCode = 300_000_000L,
        )

        assertEquals(AbstainReason.PowerSaving, decision.reason)
    }

    @Test
    fun `the kill switch stops every decision`() {
        val decision = ImmersivePolicy.decide(
            state = runningState.copy(engineStatus = EngineStatus.Disabled),
            profile = profile(DefaultAppProfiles.INSTAGRAM),
            estimate = reliable(AppContext.VerticalVideo),
            appVersionCode = 300_000_000L,
        )

        assertFalse(decision.isApplied)
        assertEquals(AbstainReason.ModuleUnavailable, decision.reason)
    }

    @Test
    fun `an app with no rule is left alone rather than guessed at`() {
        // DoD 28: when a rule stops matching, F/LAB stops optimising. It does not improvise.
        val unknownApp = AppProfile(
            packageName = "com.example.unknown",
            displayName = "Unknown",
            immersive = TreatmentMode.On,
            continuity = TreatmentMode.On,
        )

        val decision = ImmersivePolicy.decide(
            state = runningState,
            profile = unknownApp,
            estimate = reliable(AppContext.VerticalVideo),
            appVersionCode = 1L,
        )

        assertFalse(decision.isApplied)
        assertEquals(AbstainReason.NoCompatibilityRule, decision.reason)
    }

    @Test
    fun `every abstention carries a reason`() {
        val decision = ImmersivePolicy.decide(
            state = runningState.copy(engineStatus = EngineStatus.Disabled),
            profile = profile(DefaultAppProfiles.INSTAGRAM),
            estimate = reliable(AppContext.VerticalVideo),
            appVersionCode = 300_000_000L,
        )

        assertTrue(decision.reason.explanation.isNotBlank())
    }
}
