package com.lquiroz.flab.profiles

import com.lquiroz.flab.motion.MotionTuning

/** The profiles required by DoD 12. */
enum class ProfileId(val displayName: String, val summary: String) {
    Balanced("Balanced", "The default. Visible where it helps, invisible everywhere else."),
    Smooth("Smooth", "Richer motion and a softer spring. Costs a little more GPU time."),
    Minimal("Minimal", "Discreet motion only. No blur, no dimming."),
    Battery("Battery", "Minimum intervention. F/LAB keeps tracking state but stops painting."),
    Custom("Custom", "Your own tuning."),
}

/**
 * A complete F/LAB configuration.
 *
 * Profiles are values, not switches on a service: changing one produces a new [FLabProfile] that
 * every module reads on its next frame, which is what lets the Live Preview (DoD 11) show an
 * unsaved profile without touching the running configuration.
 */
data class FLabProfile(
    val id: ProfileId,
    val motion: MotionTuning,
    /** Whether Continuity may cover cover/inner switches with a transition. */
    val continuityEnabled: Boolean,
    /** Whether Immersive may treat system bars at all. */
    val immersiveEnabled: Boolean,
    /**
     * Ceiling on how long a Continuity transition may last.
     *
     * DoD 4 is explicit that the visual layer must hide latency rather than add it, so this is a
     * budget, not a duration: if the real switch finishes sooner, the transition ends with it.
     */
    val continuityBudgetMillis: Long,
) {
    companion object {
        val Balanced = FLabProfile(
            id = ProfileId.Balanced,
            motion = MotionTuning.Balanced,
            continuityEnabled = true,
            immersiveEnabled = true,
            continuityBudgetMillis = 220L,
        )

        val Smooth = FLabProfile(
            id = ProfileId.Smooth,
            motion = MotionTuning.Smooth,
            continuityEnabled = true,
            immersiveEnabled = true,
            continuityBudgetMillis = 300L,
        )

        val Minimal = FLabProfile(
            id = ProfileId.Minimal,
            motion = MotionTuning.Minimal,
            continuityEnabled = true,
            immersiveEnabled = true,
            continuityBudgetMillis = 140L,
        )

        val Battery = FLabProfile(
            id = ProfileId.Battery,
            motion = MotionTuning.Battery,
            continuityEnabled = false,
            immersiveEnabled = false,
            continuityBudgetMillis = 0L,
        )

        /** Resolves a stored [ProfileId]; [ProfileId.Custom] falls back to the user's own tuning. */
        fun of(id: ProfileId, custom: FLabProfile? = null): FLabProfile = when (id) {
            ProfileId.Balanced -> Balanced
            ProfileId.Smooth -> Smooth
            ProfileId.Minimal -> Minimal
            ProfileId.Battery -> Battery
            ProfileId.Custom -> custom?.copy(id = ProfileId.Custom) ?: Balanced.copy(id = ProfileId.Custom)
        }
    }
}
