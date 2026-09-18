package com.lquiroz.flab.compat

import com.lquiroz.flab.immersive.AppContext
import com.lquiroz.flab.profiles.DefaultAppProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** DoD 28 and DoD 29: rules are data, and they degrade by disappearing. */
class CompatibilityRegistryTest {

    private val registry = CompatibilityRegistry.default

    @Test
    fun `a known app and context resolves`() {
        val rule = registry.resolve(
            DefaultAppProfiles.INSTAGRAM,
            versionCode = 300_000_000L,
            context = AppContext.VerticalVideo,
        )

        assertNotNull(rule)
        assertEquals(ImmersiveStrategy.ImmersiveExtension, rule?.strategy)
    }

    @Test
    fun `an unknown app resolves to nothing`() {
        assertNull(registry.resolve("com.example.nope", 1L, AppContext.VerticalVideo))
    }

    @Test
    fun `an unknown context for a known app resolves to nothing`() {
        assertNull(
            registry.resolve(DefaultAppProfiles.INSTAGRAM, 300_000_000L, AppContext.Navigation),
        )
    }

    @Test
    fun `a version outside every range resolves to nothing`() {
        // This is the DoD 28 failure mode: Instagram ships an update we have no rule for, the
        // lookup misses, and F/LAB stops optimising instead of guessing.
        val narrow = CompatibilityRegistry(
            listOf(
                CompatibilityRule(
                    packageName = DefaultAppProfiles.INSTAGRAM,
                    versions = VersionRange(100L, 200L),
                    context = AppContext.VerticalVideo,
                    strategy = ImmersiveStrategy.ImmersiveExtension,
                ),
            ),
        )

        assertNotNull(narrow.resolve(DefaultAppProfiles.INSTAGRAM, 150L, AppContext.VerticalVideo))
        assertNull(narrow.resolve(DefaultAppProfiles.INSTAGRAM, 250L, AppContext.VerticalVideo))
        assertNull(narrow.resolve(DefaultAppProfiles.INSTAGRAM, 99L, AppContext.VerticalVideo))
    }

    @Test
    fun `version ranges are half open`() {
        val range = VersionRange(100L, 200L)

        assertTrue(100L in range)
        assertTrue(199L in range)
        assertTrue(200L !in range)
    }

    @Test
    fun `a specific rule wins over a catch-all`() {
        val mixed = CompatibilityRegistry(
            listOf(
                CompatibilityRule(
                    packageName = "com.example.app",
                    versions = VersionRange.Any,
                    context = AppContext.VerticalVideo,
                    strategy = ImmersiveStrategy.ChromaticContinuity,
                ),
                CompatibilityRule(
                    packageName = "com.example.app",
                    versions = VersionRange(300L, 400L),
                    context = AppContext.VerticalVideo,
                    strategy = ImmersiveStrategy.ImmersiveExtension,
                ),
            ),
        )

        assertEquals(
            ImmersiveStrategy.ImmersiveExtension,
            mixed.resolve("com.example.app", 350L, AppContext.VerticalVideo)?.strategy,
        )
        assertEquals(
            ImmersiveStrategy.ChromaticContinuity,
            mixed.resolve("com.example.app", 500L, AppContext.VerticalVideo)?.strategy,
        )
    }

    @Test
    fun `overrides layer on top without removing the floor`() {
        val override = CompatibilityRule(
            packageName = DefaultAppProfiles.INSTAGRAM,
            versions = VersionRange.Any,
            context = AppContext.VerticalVideo,
            strategy = ImmersiveStrategy.ChromaticContinuity,
        )

        val layered = registry.withOverrides(listOf(override))

        assertEquals(
            ImmersiveStrategy.ChromaticContinuity,
            layered.resolve(DefaultAppProfiles.INSTAGRAM, 1L, AppContext.VerticalVideo)?.strategy,
        )
        assertNotNull(
            "unrelated rules must survive",
            layered.resolve(DefaultAppProfiles.YOUTUBE, 1L, AppContext.FullscreenVideo),
        )
    }

    @Test
    fun `every shipped abstention explains itself`() {
        DefaultRules.all
            .filter { it.strategy == ImmersiveStrategy.None }
            .forEach {
                assertTrue(
                    "${it.packageName}/${it.context} abstains without a reason",
                    it.abstainReason.explanation.isNotBlank(),
                )
            }
    }

    @Test
    fun `no shipped rule depends on a coordinate or a view id`() {
        // A guard against the obvious regression: rules must stay version-scoped facts, not
        // pixel-scoped ones, or DoD 28's safe degradation stops being possible.
        DefaultRules.all.forEach { rule ->
            val note = rule.note.orEmpty().lowercase()
            listOf("pixel", "dp offset", "view id", "resource id", "coordinate").forEach { banned ->
                assertTrue(
                    "rule for ${rule.packageName} mentions $banned",
                    !note.contains(banned),
                )
            }
        }
    }
}

/** DoD 39: the architecture is ready for remote config even though nothing is served yet. */
class ConfigResolverTest {

    @Test
    fun `the bundled source alone produces the default rules`() {
        val resolved = ConfigResolver().resolve()

        assertEquals(DefaultRules.all.size, resolved.registry.size)
        assertEquals(1L, resolved.version)
    }

    @Test
    fun `a bundle for a newer build is ignored`() {
        val future = object : ConfigSource {
            override val name = "Future"
            override fun load() = ConfigBundle(
                version = 99L,
                minAppVersionCode = 500L,
                rules = emptyList(),
                flags = listOf(FeatureFlag("something_new", enabled = true)),
            )
        }

        val resolved = ConfigResolver(
            sources = listOf(BundledConfigSource, future),
            appVersionCode = 1L,
        ).resolve()

        assertTrue("a flag from a future bundle must not apply", !resolved.isEnabled("something_new"))
        assertEquals(1L, resolved.version)
    }

    @Test
    fun `a newer bundle overrides an older rule`() {
        val remote = object : ConfigSource {
            override val name = "Remote"
            override fun load() = ConfigBundle(
                version = 2L,
                minAppVersionCode = 0L,
                rules = listOf(
                    CompatibilityRule(
                        packageName = DefaultAppProfiles.INSTAGRAM,
                        versions = VersionRange.Any,
                        context = AppContext.VerticalVideo,
                        strategy = ImmersiveStrategy.None,
                        abstainReason = com.lquiroz.flab.immersive.AbstainReason.NoCompatibilityRule,
                    ),
                ),
                flags = emptyList(),
            )
        }

        val resolved = ConfigResolver(listOf(BundledConfigSource, remote)).resolve()

        assertEquals(
            "a remote bundle must be able to switch a shipped rule off",
            ImmersiveStrategy.None,
            resolved.registry
                .resolve(DefaultAppProfiles.INSTAGRAM, 1L, AppContext.VerticalVideo)
                ?.strategy,
        )
    }

    @Test
    fun `an unknown flag is off`() {
        assertTrue(!ConfigResolver().resolve().isEnabled("not_a_real_flag"))
    }
}
