package com.lquiroz.flab.compat

import com.lquiroz.flab.immersive.AbstainReason
import com.lquiroz.flab.immersive.AppContext

/**
 * What F/LAB does to a surface once it has decided to act.
 *
 * Every one of these is built out of ordinary, documented Android behaviour applied to F/LAB's own
 * window, plus system-bar appearance requests. None of them reaches inside another app, and none
 * of them needs a privileged permission — see `docs/CAPABILITIES.md` for where that line is.
 */
enum class ImmersiveStrategy(val displayName: String) {
    /** Do nothing. */
    None("None"),

    /** Match the system bar colours to the content behind them. */
    ChromaticContinuity("Chromatic continuity"),

    /** Draw behind the bars and let the content run under them. */
    EdgeToEdge("Edge to edge"),

    /** Edge to edge plus a gradient that carries the content up into the bar area. */
    GradientExtension("Gradient extension"),

    /** The full treatment for vertical video: edge to edge, gradient, and icon contrast tracking. */
    ImmersiveExtension("Immersive extension"),
}

/** An inclusive-exclusive version-code window. */
data class VersionRange(val minInclusive: Long, val maxExclusive: Long) {
    operator fun contains(versionCode: Long): Boolean =
        versionCode >= minInclusive && versionCode < maxExclusive

    companion object {
        val Any = VersionRange(0L, Long.MAX_VALUE)
    }
}

/**
 * One row of the compatibility table required by DoD 29:
 *
 * ```
 * App -> version range -> context -> capability -> F/LAB strategy
 * ```
 *
 * The structure exists so that when Instagram ships an update that moves something, the fix is a
 * new row — eventually a remotely-delivered one, see [RemoteConfigSource] — and not a redesign.
 *
 * @param worthwhileOnAuto whether this row should fire when the app is on `Auto`. This is where
 *   DoD 48 lives: a row can be perfectly valid and still answer "no perceptible gain".
 * @param abstainReason the reason reported when [strategy] is [ImmersiveStrategy.None], so an
 *   intentional stand-back is distinguishable from a missing rule.
 */
data class CompatibilityRule(
    val packageName: String,
    val versions: VersionRange,
    val context: AppContext,
    val strategy: ImmersiveStrategy,
    val worthwhileOnAuto: Boolean = true,
    val abstainReason: AbstainReason = AbstainReason.None,
    val note: String? = null,
)

/**
 * The rule table.
 *
 * Lookup is most-specific-first: a row naming an exact version range wins over a row that accepts
 * any version, so a targeted fix can be added without touching the general case.
 *
 * ### Safe degradation (DoD 28)
 *
 * [resolve] returns null when nothing matches, and [ImmersivePolicy] turns a null into an
 * abstention. That is the entire failure mode: an Instagram update that lands outside every known
 * version range makes F/LAB stop treating Instagram, which is exactly the behaviour DoD 28 asks
 * for. There is no fallback that guesses.
 */
class CompatibilityRegistry(rules: List<CompatibilityRule>) {

    private val byPackage: Map<String, List<CompatibilityRule>> =
        rules.groupBy { it.packageName }
            .mapValues { (_, list) -> list.sortedBy { if (it.versions == VersionRange.Any) 1 else 0 } }

    val size: Int = rules.size

    fun resolve(
        packageName: String,
        versionCode: Long,
        context: AppContext,
    ): CompatibilityRule? = byPackage[packageName]?.firstOrNull {
        it.context == context && versionCode in it.versions
    }

    fun rulesFor(packageName: String): List<CompatibilityRule> = byPackage[packageName].orEmpty()

    /** Returns a registry with [overrides] layered on top, for remote config (DoD 39). */
    fun withOverrides(overrides: List<CompatibilityRule>): CompatibilityRegistry =
        CompatibilityRegistry(overrides + byPackage.values.flatten())

    companion object {
        val default: CompatibilityRegistry by lazy { CompatibilityRegistry(DefaultRules.all) }
    }
}
