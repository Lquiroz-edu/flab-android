package com.lquiroz.flab.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DoD 14: the list of things F/LAB will not put a layer over. */
class SafeAppsTest {

    @Test
    fun `banking apps are protected`() {
        listOf(
            "com.bankinter.launcher",
            "es.bancosantander.apps",
            "com.bbva.bbvacontigo",
            "com.revolut.revolut",
            "com.paypal.android.p2pmobile",
        ).forEach { assertTrue("$it should be protected", SafeApps.isProtected(it)) }
    }

    @Test
    fun `authenticators and password managers are protected`() {
        listOf(
            "com.google.android.apps.authenticator2",
            "com.x8bit.bitwarden",
            "com.lastpass.lpandroid",
            "com.example.passwordvault",
        ).forEach { assertTrue("$it should be protected", SafeApps.isProtected(it)) }
    }

    @Test
    fun `system and permission surfaces are protected`() {
        listOf(
            "com.android.systemui",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.android.keyguard",
        ).forEach { assertTrue("$it should be protected", SafeApps.isProtected(it)) }
    }

    @Test
    fun `the camera is protected`() {
        assertTrue(SafeApps.isProtected(DefaultAppProfiles.SAMSUNG_CAMERA))
    }

    @Test
    fun `ordinary apps are not protected`() {
        listOf(
            "com.instagram.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "com.whatsapp",
        ).forEach { assertFalse("$it should not be protected", SafeApps.isProtected(it)) }
    }

    @Test
    fun `a protected app always resolves to a locked profile`() {
        val profile = DefaultAppProfiles.forPackage("com.mybank.android")

        assertTrue(profile.locked)
        assertEquals(TreatmentMode.Off, profile.immersive)
        assertEquals(TreatmentMode.Off, profile.continuity)
    }

    @Test
    fun `a user override cannot unlock a protected app`() {
        val override = AppProfile(
            packageName = "com.mybank.android",
            displayName = "My Bank",
            immersive = TreatmentMode.On,
            continuity = TreatmentMode.On,
        )

        // The override is honoured as a stored preference, but the policy gate in ImmersivePolicy
        // refuses it regardless — see ImmersivePolicyTest. Here we only assert that the safe-app
        // check itself does not soften.
        assertTrue(SafeApps.isProtected(override.packageName))
    }

    @Test
    fun `an unconfigured app defaults to immersive off`() {
        val profile = DefaultAppProfiles.forPackage("com.example.somethingnew")

        assertEquals(TreatmentMode.Off, profile.immersive)
        assertFalse(profile.locked)
    }

    @Test
    fun `seeded profiles match the DoD examples`() {
        val instagram = DefaultAppProfiles.forPackage(DefaultAppProfiles.INSTAGRAM)
        assertEquals(TreatmentMode.On, instagram.immersive)
        assertEquals(TreatmentMode.On, instagram.continuity)

        val youtube = DefaultAppProfiles.forPackage(DefaultAppProfiles.YOUTUBE)
        assertEquals("YouTube defers to Auto", TreatmentMode.Auto, youtube.immersive)

        val camera = DefaultAppProfiles.forPackage(DefaultAppProfiles.SAMSUNG_CAMERA)
        assertTrue(camera.locked)
    }
}
