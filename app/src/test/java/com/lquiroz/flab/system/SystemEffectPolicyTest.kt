package com.lquiroz.flab.system

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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gates on the one F/LAB module that can put pixels over another app.
 *
 * These are the highest-stakes tests in the project. Everything else, failing, costs a visual
 * improvement; this, failing, puts a layer over somebody's bank.
 */
class SystemEffectPolicyTest {

    private val running = FLabState(
        engineStatus = EngineStatus.Active,
        window = WindowState(presentation = WindowPresentation.FullScreen),
        moduleStates = ModuleId.entries.associateWith { ModuleRuntimeState(enabled = true) },
    )

    private fun decide(
        state: FLabState = running,
        foreground: String? = DefaultAppProfiles.INSTAGRAM,
        profile: AppProfile? = null,
        energy: Float = 0.8f,
        permission: Boolean = true,
        locked: Boolean = false,
    ) = SystemEffectPolicy.decide(
        state = state,
        foregroundPackage = foreground,
        profile = profile ?: foreground?.let { DefaultAppProfiles.forPackage(it) },
        energy = energy,
        hasOverlayPermission = permission,
        isDeviceLocked = locked,
    )

    @Test
    fun `applies over an ordinary app while the device is moving`() {
        assertEquals(OverlayVerdict.Show, decide())
    }

    // ------------------------------------------------------------------ the refusals that matter

    @Test
    fun `never draws over a banking app`() {
        listOf(
            "com.bankinter.launcher",
            "es.bancosantander.apps",
            "com.bbva.bbvacontigo",
            "com.revolut.revolut",
            "com.paypal.android.p2pmobile",
        ).forEach {
            assertEquals("$it must be refused", OverlayVerdict.ProtectedApp, decide(foreground = it))
        }
    }

    @Test
    fun `never draws over an authenticator or password manager`() {
        listOf(
            "com.google.android.apps.authenticator2",
            "com.x8bit.bitwarden",
            "com.lastpass.lpandroid",
        ).forEach {
            assertEquals("$it must be refused", OverlayVerdict.ProtectedApp, decide(foreground = it))
        }
    }

    @Test
    fun `never draws over the camera`() {
        assertEquals(
            OverlayVerdict.ProtectedApp,
            decide(foreground = DefaultAppProfiles.SAMSUNG_CAMERA),
        )
    }

    @Test
    fun `never draws over a permission prompt or the installer`() {
        listOf(
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.android.systemui",
        ).forEach {
            assertEquals("$it must be refused", OverlayVerdict.ProtectedApp, decide(foreground = it))
        }
    }

    @Test
    fun `a user override cannot force it over a protected app`() {
        // The user asking for it is not a reason. This is the one place in F/LAB where the user's
        // stated preference is deliberately not the last word.
        val forced = AppProfile(
            packageName = "com.mybank.android",
            displayName = "My Bank",
            immersive = TreatmentMode.On,
            continuity = TreatmentMode.On,
        )

        assertEquals(
            OverlayVerdict.ProtectedApp,
            decide(foreground = "com.mybank.android", profile = forced),
        )
    }

    @Test
    fun `never draws on the lock screen`() {
        assertEquals(OverlayVerdict.Locked, decide(locked = true))
    }

    @Test
    fun `refuses when the foreground app is unknown`() {
        // Not knowing what is underneath resolves to not acting. If this ever returned Show, every
        // protected-app test above would be bypassable by the package name simply not arriving.
        assertEquals(OverlayVerdict.UnknownApp, decide(foreground = null))
    }

    @Test
    fun `an unknown app is refused even at full energy and with everything granted`() {
        assertNotEquals(
            OverlayVerdict.Show,
            decide(foreground = null, energy = 1f, permission = true, locked = false),
        )
    }

    // ------------------------------------------------------------------ the rest

    @Test
    fun `refuses without the overlay permission`() {
        assertEquals(OverlayVerdict.NoPermission, decide(permission = false))
    }

    @Test
    fun `refuses in split screen and picture in picture`() {
        listOf(WindowPresentation.SplitScreen, WindowPresentation.PictureInPicture, WindowPresentation.FreeForm)
            .forEach {
                assertEquals(
                    "$it must be refused",
                    OverlayVerdict.MultiWindow,
                    decide(state = running.copy(window = WindowState(presentation = it))),
                )
            }
    }

    @Test
    fun `refuses while conserving power`() {
        assertEquals(
            OverlayVerdict.PowerSaving,
            decide(state = running.copy(power = PowerPosture.Conserving)),
        )
    }

    @Test
    fun `the kill switch stops it`() {
        assertEquals(
            OverlayVerdict.EngineOff,
            decide(state = running.copy(engineStatus = EngineStatus.Disabled)),
        )
    }

    @Test
    fun `a disabled module stops it and says so distinctly`() {
        val moduleOff = running.copy(
            moduleStates = running.moduleStates +
                (ModuleId.FoldMotion to ModuleRuntimeState(enabled = false)),
        )

        assertEquals(OverlayVerdict.ModuleOff, decide(state = moduleOff))
    }

    @Test
    fun `comes down when the device stops moving`() {
        assertEquals(OverlayVerdict.NotMoving, decide(energy = 0f))
        assertEquals(
            "sensor noise around a held position must not flicker the window",
            OverlayVerdict.NotMoving,
            decide(energy = SystemEffectPolicy.MIN_ENERGY),
        )
    }

    @Test
    fun `an app the user turned off is skipped`() {
        val off = AppProfile(
            packageName = "com.example.app",
            displayName = "Example",
            immersive = TreatmentMode.Off,
            continuity = TreatmentMode.Off,
        )

        assertEquals(
            OverlayVerdict.UserDisabledApp,
            decide(foreground = "com.example.app", profile = off),
        )
    }

    @Test
    fun `every verdict explains itself`() {
        OverlayVerdict.entries.forEach {
            assertTrue("${it.name} has no explanation", it.explanation.isNotBlank())
        }
    }

    @Test
    fun `only one verdict ever shows the overlay`() {
        // A guard against a future edit adding a second "go" path that skips the gates above.
        assertEquals(1, OverlayVerdict.entries.count { it == OverlayVerdict.Show })
    }
}
