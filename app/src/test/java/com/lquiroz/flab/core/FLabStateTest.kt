package com.lquiroz.flab.core

import com.lquiroz.flab.motion.MotionTuning
import com.lquiroz.flab.profiles.ProfileId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DoD 2 and DoD 33: one state, and it knows when to stand down. */
class FLabStateTest {

    private val active = FLabState(
        engineStatus = EngineStatus.Active,
        activeProfile = ProfileId.Balanced,
    )

    @Test
    fun `a disabled engine runs no modules`() {
        val disabled = active.copy(engineStatus = EngineStatus.Disabled)

        assertTrue(disabled.activeModules.isEmpty())
        ModuleId.entries.forEach { assertFalse(disabled.isModuleRunning(it)) }
    }

    @Test
    fun `an active engine runs every enabled module`() {
        assertEquals(ModuleId.entries.size, active.activeModules.size)
    }

    @Test
    fun `power saving keeps every module running`() {
        // Regression cover for the bug that made the app look dead on a real Fold: battery saver
        // or a warm device used to take Fold Motion out entirely, and every visible effect — the
        // in-app motion, the system overlay, the wallpaper warp — hangs off that one module.
        val conserving = active.copy(power = PowerPosture.Conserving)

        ModuleId.entries.forEach {
            assertTrue("$it must survive power saving", conserving.isModuleRunning(it))
        }
    }

    @Test
    fun `power saving drops the veil and keeps the motion`() {
        val full = MotionTuning.Balanced
        val conserving = full.forPower(PowerPosture.Conserving)

        assertEquals(0f, conserving.blurIntensity)
        assertEquals(0f, conserving.dimIntensity)
        assertEquals(0f, conserving.depthIntensity)
        assertEquals("position channels are kept", full.scaleIntensity, conserving.scaleIntensity)
        assertEquals(full.offsetIntensity, conserving.offsetIntensity)
        assertEquals(full.warpIntensity, conserving.warpIntensity)
        assertFalse("the engine must still schedule frames", conserving.isInert)

        assertEquals("nothing changes at normal power", full, full.forPower(PowerPosture.Normal))
        assertTrue("restricted is inert", full.forPower(PowerPosture.Restricted).isInert)
    }

    @Test
    fun `thermal restriction stops everything`() {
        val restricted = active.copy(power = PowerPosture.Restricted)

        assertTrue(restricted.activeModules.isEmpty())
    }

    @Test
    fun `a module the user turned off does not run`() {
        val state = active.copy(
            moduleStates = active.moduleStates +
                (ModuleId.Immersive to ModuleRuntimeState(enabled = false)),
        )

        assertFalse(state.isModuleRunning(ModuleId.Immersive))
        assertEquals("Turned off", state.moduleStates[ModuleId.Immersive]?.unavailableReason)
        assertTrue("other modules are unaffected", state.isModuleRunning(ModuleId.FoldMotion))
    }

    @Test
    fun `multi window means F-LAB does not own the system bars`() {
        assertTrue(WindowState(presentation = WindowPresentation.FullScreen).ownsSystemBars)
        assertFalse(WindowState(presentation = WindowPresentation.SplitScreen).ownsSystemBars)
        assertFalse(WindowState(presentation = WindowPresentation.FreeForm).ownsSystemBars)
        assertFalse(WindowState(presentation = WindowPresentation.PictureInPicture).ownsSystemBars)
        assertFalse(
            "unknown is not an invitation",
            WindowState(presentation = WindowPresentation.Unknown).ownsSystemBars,
        )
    }

    @Test
    fun `window size derives its own orientation`() {
        assertEquals(ScreenOrientation.Portrait, WindowSize(400, 900).orientation)
        assertEquals(ScreenOrientation.Landscape, WindowSize(900, 400).orientation)
    }

    @Test
    fun `a device without a hinge is not treated as foldable`() {
        assertFalse(FoldState().isFoldable)
        assertTrue(FoldState(hingeOrientation = HingeOrientation.Vertical).isFoldable)
        assertTrue(FoldState(hingeAngleDegrees = 92f).isFoldable)
    }
}
