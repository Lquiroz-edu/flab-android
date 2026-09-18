package com.lquiroz.flab.immersive

/**
 * What the user is looking at inside an app, as far as F/LAB can tell.
 *
 * DoD 7 is the rule this type exists to enforce: an app being open is not a reason to do anything.
 * Instagram's Feed and Instagram's Reels want opposite treatment, so the unit of decision has to be
 * the context, never the package.
 */
enum class AppContext(val displayName: String) {
    /** Scrolling content with its own chrome. Leave alone. */
    Feed("Feed"),

    /** Full-bleed vertical video: Reels, Shorts, TikTok. The case immersion is actually for. */
    VerticalVideo("Vertical video"),

    /** Stories and similar tap-through full-screen content. */
    Story("Story"),

    /** Conversations. Keyboard-heavy, so always conservative. */
    Messaging("Messaging"),

    /** The app's own full-screen video player, which already handles its bars. */
    FullscreenVideo("Full-screen video"),

    /** Viewfinder. Never touched. */
    Camera("Camera"),

    /** Turn-by-turn navigation. Never covered. */
    Navigation("Navigation"),

    /** A web page or document. Colour continuity at most. */
    Browsing("Browsing"),

    /** We could not tell. This is a first-class value, not an error. */
    Unknown("Unknown"),
}

/**
 * Why F/LAB did or did not act, recorded for every decision.
 *
 * Diagnostics shows the most recent one. Being able to answer "why is nothing happening in
 * Instagram right now" without a debugger is most of what makes this project maintainable.
 */
enum class AbstainReason(val explanation: String) {
    None("Applied"),
    LowConfidence("Context could not be identified reliably enough"),
    AppAlreadyImmersive("The app already handles this well"),
    ProtectedApp("Protected by the safe-app policy"),
    UserDisabled("Turned off for this app"),
    MultiWindow("The window is shared, so F/LAB does not own the system bars"),
    UnreadableContrast("System bar icons could not be kept legible"),
    NoCompatibilityRule("No compatibility rule covers this app version"),
    ModuleUnavailable("The Immersive module is not running"),
    PowerSaving("The device asked F/LAB to conserve power"),
    NoPerceptibleGain("The treatment would not be perceptible here"),
}

/**
 * A context guess plus how much F/LAB trusts it.
 *
 * [confidence] runs `0f..1f`. [MIN_CONFIDENCE] is the bar for acting, and it is set high on
 * purpose: DoD 7 says losing a visual improvement is better than breaking an app, so the
 * asymmetry between a false positive and a false negative is baked into the threshold.
 */
data class ContextEstimate(
    val context: AppContext,
    val confidence: Float,
    val signals: List<String> = emptyList(),
) {
    val isReliable: Boolean
        get() = context != AppContext.Unknown && confidence >= MIN_CONFIDENCE

    companion object {
        const val MIN_CONFIDENCE = 0.7f
        val Unknown = ContextEstimate(AppContext.Unknown, 0f)
    }
}
