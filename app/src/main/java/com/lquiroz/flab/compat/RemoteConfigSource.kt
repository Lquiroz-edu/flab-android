package com.lquiroz.flab.compat

/**
 * A feature flag F/LAB can be told about without being rebuilt.
 *
 * Flags are advisory and always fail closed: an unknown flag is off, and a flag that would enable
 * something the local build does not implement does nothing.
 */
data class FeatureFlag(val key: String, val enabled: Boolean)

/**
 * A bundle of remotely-changeable configuration (DoD 39).
 *
 * @param version monotonically increasing; a bundle older than the one in use is ignored.
 * @param minAppVersionCode a bundle written for a newer F/LAB is ignored by an older one, so a
 *   rule that assumes a strategy this build does not have can never be applied.
 */
data class ConfigBundle(
    val version: Long,
    val minAppVersionCode: Long,
    val rules: List<CompatibilityRule>,
    val flags: List<FeatureFlag>,
) {
    companion object {
        val Empty = ConfigBundle(0L, 0L, emptyList(), emptyList())
    }
}

/**
 * Where configuration comes from.
 *
 * v1 ships only [Bundled] — DoD 39 asks for the architecture to be ready for remote config, not
 * for a backend to exist. What that readiness actually means here:
 *
 *  - Rules are data, resolved through [CompatibilityRegistry], never `if (packageName == ...)`
 *    scattered through the modules.
 *  - The Core reads its registry from a [ConfigSource], so adding an HTTP-backed implementation is
 *    a new class rather than a change to the Core.
 *  - [ConfigResolver] already does the version gating and layering that a remote source needs, so
 *    that logic is written and tested before anything ships bundles over a network.
 */
interface ConfigSource {
    val name: String
    fun load(): ConfigBundle
}

/** The rules compiled into the APK. Always present, always the base layer. */
object BundledConfigSource : ConfigSource {
    override val name = "Bundled"
    override fun load() = ConfigBundle(
        version = 1L,
        minAppVersionCode = 0L,
        rules = DefaultRules.all,
        flags = emptyList(),
    )
}

/**
 * Combines config sources into the registry the Core uses.
 *
 * Layering is last-source-wins for a given app/version/context, with the bundled rules underneath,
 * so a remote bundle can correct a shipped rule but can never delete the floor beneath it.
 */
class ConfigResolver(
    private val sources: List<ConfigSource> = listOf(BundledConfigSource),
    private val appVersionCode: Long = Long.MAX_VALUE,
) {
    fun resolve(): ResolvedConfig {
        val applicable = sources
            .map { it.load() }
            .filter { it.minAppVersionCode <= appVersionCode }
            .sortedBy { it.version }

        val registry = applicable.fold(CompatibilityRegistry(emptyList())) { acc, bundle ->
            acc.withOverrides(bundle.rules)
        }
        val flags = applicable
            .flatMap { it.flags }
            .associateBy { it.key }
            .mapValues { (_, flag) -> flag.enabled }

        return ResolvedConfig(
            registry = registry,
            flags = flags,
            version = applicable.maxOfOrNull { it.version } ?: 0L,
        )
    }
}

data class ResolvedConfig(
    val registry: CompatibilityRegistry,
    val flags: Map<String, Boolean>,
    val version: Long,
) {
    /** Unknown flags are off. Never "on unless told otherwise". */
    fun isEnabled(key: String): Boolean = flags[key] == true
}
