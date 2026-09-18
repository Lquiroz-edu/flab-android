package com.lquiroz.flab.immersive

import com.lquiroz.flab.compat.CompatibilityRegistry
import com.lquiroz.flab.compat.ImmersiveStrategy
import com.lquiroz.flab.core.FLabState
import com.lquiroz.flab.core.ModuleId
import com.lquiroz.flab.core.PowerPosture
import com.lquiroz.flab.profiles.AppProfile
import com.lquiroz.flab.profiles.SafeApps
import com.lquiroz.flab.profiles.TreatmentMode

/**
 * The outcome of asking "should F/LAB do anything here?".
 *
 * An abstention is as much a result as an application, and carries the same amount of detail, so
 * that "F/LAB did nothing" can always be distinguished from "F/LAB is broken".
 */
data class ImmersiveDecision(
    val strategy: ImmersiveStrategy,
    val reason: AbstainReason,
    val estimate: ContextEstimate,
) {
    val isApplied: Boolean get() = strategy != ImmersiveStrategy.None

    companion object {
        fun abstain(reason: AbstainReason, estimate: ContextEstimate = ContextEstimate.Unknown) =
            ImmersiveDecision(ImmersiveStrategy.None, reason, estimate)
    }
}

/**
 * Decides whether the Immersive module should act, and how.
 *
 * Pure: the same inputs always give the same decision, which is what makes DoD 46 and 47 testable
 * rather than a matter of squinting at a phone. The gates run cheapest-and-most-absolute first, so
 * a protected app never reaches the compatibility lookup at all.
 */
object ImmersivePolicy {

    fun decide(
        state: FLabState,
        profile: AppProfile,
        estimate: ContextEstimate,
        appVersionCode: Long,
        registry: CompatibilityRegistry = CompatibilityRegistry.default,
    ): ImmersiveDecision {
        if (!state.isModuleRunning(ModuleId.Immersive)) {
            return ImmersiveDecision.abstain(AbstainReason.ModuleUnavailable, estimate)
        }
        if (state.power != PowerPosture.Normal) {
            return ImmersiveDecision.abstain(AbstainReason.PowerSaving, estimate)
        }
        if (profile.locked || SafeApps.isProtected(profile.packageName)) {
            return ImmersiveDecision.abstain(AbstainReason.ProtectedApp, estimate)
        }
        if (profile.immersive == TreatmentMode.Off) {
            return ImmersiveDecision.abstain(AbstainReason.UserDisabled, estimate)
        }
        // A shared window means the status bar is not ours to blend into. DoD 33.
        if (!state.window.ownsSystemBars) {
            return ImmersiveDecision.abstain(AbstainReason.MultiWindow, estimate)
        }
        if (!estimate.isReliable) {
            return ImmersiveDecision.abstain(AbstainReason.LowConfidence, estimate)
        }

        val rule = registry.resolve(profile.packageName, appVersionCode, estimate.context)
            ?: return ImmersiveDecision.abstain(AbstainReason.NoCompatibilityRule, estimate)

        if (rule.strategy == ImmersiveStrategy.None) {
            // The rule exists and its answer is "stand back" — usually because the app is already
            // doing the right thing. DoD 47's YouTube case lands here.
            return ImmersiveDecision.abstain(rule.abstainReason, estimate)
        }
        // Auto defers to the rule's own judgement about whether the treatment is worth it (DoD 48).
        if (profile.immersive == TreatmentMode.Auto && !rule.worthwhileOnAuto) {
            return ImmersiveDecision.abstain(AbstainReason.NoPerceptibleGain, estimate)
        }

        return ImmersiveDecision(rule.strategy, AbstainReason.None, estimate)
    }
}
