package com.lquiroz.flab.compat

import com.lquiroz.flab.immersive.AbstainReason
import com.lquiroz.flab.immersive.AppContext
import com.lquiroz.flab.profiles.DefaultAppProfiles

/**
 * The rules F/LAB ships with.
 *
 * Every row is a claim about an app's behaviour that could stop being true with the next update,
 * so rows are written to be replaceable and are never load-bearing: if one goes stale the registry
 * stops matching and F/LAB stands back (DoD 28).
 *
 * Note what is absent: there is no rule keyed on a pixel offset, a view id, a screen coordinate or
 * a string in the UI. Those are the things that break silently and take an app down with them.
 */
object DefaultRules {

    val all: List<CompatibilityRule> = buildList {
        addAll(instagram())
        addAll(youTube())
        addAll(tikTok())
        addAll(chrome())
        addAll(maps())
        addAll(camera())
    }

    /**
     * DoD 46. Reels and Stories get the full treatment; Feed and DM are explicitly left alone
     * rather than merely unlisted, so Diagnostics can show that the abstention was deliberate.
     */
    private fun instagram() = listOf(
        CompatibilityRule(
            packageName = DefaultAppProfiles.INSTAGRAM,
            versions = VersionRange.Any,
            context = AppContext.VerticalVideo,
            strategy = ImmersiveStrategy.ImmersiveExtension,
            note = "Reels: extend content perceptually into the status bar area.",
        ),
        CompatibilityRule(
            packageName = DefaultAppProfiles.INSTAGRAM,
            versions = VersionRange.Any,
            context = AppContext.Story,
            strategy = ImmersiveStrategy.GradientExtension,
            note = "Stories: gradient only — the progress bars at the top must stay readable.",
        ),
        CompatibilityRule(
            packageName = DefaultAppProfiles.INSTAGRAM,
            versions = VersionRange.Any,
            context = AppContext.Feed,
            strategy = ImmersiveStrategy.None,
            abstainReason = AbstainReason.NoPerceptibleGain,
            note = "Feed keeps its normal chrome.",
        ),
        CompatibilityRule(
            packageName = DefaultAppProfiles.INSTAGRAM,
            versions = VersionRange.Any,
            context = AppContext.Messaging,
            strategy = ImmersiveStrategy.None,
            abstainReason = AbstainReason.NoPerceptibleGain,
            note = "DM returns to conservative behaviour. The keyboard must not be disturbed.",
        ),
    )

    /**
     * DoD 47. YouTube is the control case: it already gets most of this right, so nearly every
     * row here says "stand back", and the ones that do not are marked as not worth it on Auto.
     */
    private fun youTube() = listOf(
        CompatibilityRule(
            packageName = DefaultAppProfiles.YOUTUBE,
            versions = VersionRange.Any,
            context = AppContext.FullscreenVideo,
            strategy = ImmersiveStrategy.None,
            abstainReason = AbstainReason.AppAlreadyImmersive,
            note = "Full screen is already correct. Touching it can only make it worse.",
        ),
        CompatibilityRule(
            packageName = DefaultAppProfiles.YOUTUBE,
            versions = VersionRange.Any,
            context = AppContext.VerticalVideo,
            strategy = ImmersiveStrategy.ChromaticContinuity,
            worthwhileOnAuto = false,
            note = "Shorts: colour matching only, and only if the user asks for it explicitly.",
        ),
        CompatibilityRule(
            packageName = DefaultAppProfiles.YOUTUBE,
            versions = VersionRange.Any,
            context = AppContext.Feed,
            strategy = ImmersiveStrategy.None,
            abstainReason = AbstainReason.AppAlreadyImmersive,
        ),
    )

    private fun tikTok() = listOf(
        CompatibilityRule(
            packageName = DefaultAppProfiles.TIKTOK,
            versions = VersionRange.Any,
            context = AppContext.VerticalVideo,
            strategy = ImmersiveStrategy.ImmersiveExtension,
            note = "Treated as full-screen vertical content.",
        ),
        CompatibilityRule(
            packageName = DefaultAppProfiles.TIKTOK,
            versions = VersionRange.Any,
            context = AppContext.Messaging,
            strategy = ImmersiveStrategy.None,
            abstainReason = AbstainReason.NoPerceptibleGain,
        ),
    )

    private fun chrome() = listOf(
        CompatibilityRule(
            packageName = DefaultAppProfiles.CHROME,
            versions = VersionRange.Any,
            context = AppContext.Browsing,
            strategy = ImmersiveStrategy.ChromaticContinuity,
            note = "Colour continuity only. An overlay over a page is never acceptable.",
        ),
        CompatibilityRule(
            packageName = DefaultAppProfiles.CHROME,
            versions = VersionRange.Any,
            context = AppContext.FullscreenVideo,
            strategy = ImmersiveStrategy.None,
            abstainReason = AbstainReason.AppAlreadyImmersive,
        ),
    )

    private fun maps() = listOf(
        CompatibilityRule(
            packageName = DefaultAppProfiles.GOOGLE_MAPS,
            versions = VersionRange.Any,
            context = AppContext.Navigation,
            strategy = ImmersiveStrategy.None,
            abstainReason = AbstainReason.ProtectedApp,
            note = "Navigation information is never covered or tinted.",
        ),
    )

    private fun camera() = listOf(
        CompatibilityRule(
            packageName = DefaultAppProfiles.SAMSUNG_CAMERA,
            versions = VersionRange.Any,
            context = AppContext.Camera,
            strategy = ImmersiveStrategy.None,
            abstainReason = AbstainReason.ProtectedApp,
            note = "Shutter, preview and capture are untouchable.",
        ),
    )
}
