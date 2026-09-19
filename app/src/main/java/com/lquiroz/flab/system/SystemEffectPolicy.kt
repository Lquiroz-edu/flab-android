package com.lquiroz.flab.system

import com.lquiroz.flab.core.EngineStatus
import com.lquiroz.flab.core.FLabState
import com.lquiroz.flab.core.ModuleId
import com.lquiroz.flab.core.PowerPosture
import com.lquiroz.flab.profiles.AppProfile
import com.lquiroz.flab.profiles.SafeApps
import com.lquiroz.flab.profiles.TreatmentMode

/**
 * Why the system-wide overlay is or is not on screen right now.
 *
 * Every refusal is named. An overlay that silently does not appear is indistinguishable from a
 * broken one, and an overlay that silently *does* appear where it should not is worse.
 */
enum class OverlayVerdict(val explanation: String) {
    Show("Applying"),
    ModuleOff("System effects are turned off"),
    EngineOff("F/LAB is off"),
    NoPermission("Display over other apps is not granted"),
    ProtectedApp("Protected app — F/LAB never layers over this"),
    UserDisabledApp("Turned off for this app"),
    Locked("The device is locked"),
    MultiWindow("The window is shared, so the effect would be misleading"),
    PowerSaving("The device asked F/LAB to conserve power"),
    NotMoving("Nothing is moving, so there is nothing to soften"),
    UnknownApp("The foreground app could not be identified"),
}

/**
 * Decides whether F/LAB may draw its system-wide fold effect.
 *
 * ### What this overlay is, and what it deliberately is not
 *
 * It is a **purely visual, click-through** window that blurs and dims what is behind it while the
 * device is physically moving. It never takes input: `FLAG_NOT_TOUCHABLE` is not a setting here,
 * it is a constant, because an interactive layer over another app is a tapjacking surface. It
 * cannot read the screen, and it cannot move or restyle the app underneath.
 *
 * ### The gates, and why each one exists
 *
 * - **Protected apps** (DoD 14). Banking, authenticators, password managers, payments, the camera,
 *   package installation, permission prompts, system UI. Even a click-through blur over a payment
 *   sheet is unacceptable: it changes what the user sees while they are confirming money.
 * - **Keyguard.** The lock screen is a security surface and never ours.
 * - **Multi-window.** In split screen the blur would cover both halves while only one is folding,
 *   which reads as a glitch rather than as motion.
 * - **Unknown foreground app.** If we cannot tell what is in front, we cannot tell whether it is
 *   protected. Not knowing resolves to not acting, exactly as in `ImmersivePolicy`.
 * - **Not moving.** The effect exists to soften a transition in progress. With no movement there is
 *   nothing to soften, and a resting blur is the "overlay congelado" of DoD 30.
 *
 * Pure, so all of the above is testable without a device — which matters more here than anywhere
 * else in the project, because this is the one module that can put pixels over someone's bank.
 */
object SystemEffectPolicy {

    /**
     * @param foregroundPackage the package in front, or null when unknown. Null is treated as
     *   unknown rather than as "nothing", because an overlay decision made without knowing what is
     *   underneath is not a decision worth making.
     * @param energy how much the device is moving, `0f..1f`, from the motion engine.
     */
    fun decide(
        state: FLabState,
        foregroundPackage: String?,
        profile: AppProfile?,
        energy: Float,
        hasOverlayPermission: Boolean,
        isDeviceLocked: Boolean,
    ): OverlayVerdict {
        if (!hasOverlayPermission) return OverlayVerdict.NoPermission
        if (state.engineStatus != EngineStatus.Active) return OverlayVerdict.EngineOff
        // Power is checked before the module, because conserving power already stops Fold Motion
        // from running and would otherwise be reported as the user having turned it off. The more
        // specific reason has to win, or Diagnostics sends you looking in the wrong place.
        if (state.power != PowerPosture.Normal) return OverlayVerdict.PowerSaving
        if (!state.isModuleRunning(ModuleId.FoldMotion)) return OverlayVerdict.ModuleOff
        if (isDeviceLocked) return OverlayVerdict.Locked
        if (state.window.presentation.isMultiWindow) return OverlayVerdict.MultiWindow

        if (foregroundPackage == null) return OverlayVerdict.UnknownApp
        if (SafeApps.isProtected(foregroundPackage)) return OverlayVerdict.ProtectedApp
        if (profile != null) {
            if (profile.locked) return OverlayVerdict.ProtectedApp
            if (profile.continuity == TreatmentMode.Off) return OverlayVerdict.UserDisabledApp
        }

        if (energy <= MIN_ENERGY) return OverlayVerdict.NotMoving
        return OverlayVerdict.Show
    }

    /**
     * Below this, movement is indistinguishable from a resting device and the overlay comes down.
     *
     * Set above zero on purpose: sensor noise around a held position would otherwise flicker the
     * window in and out, which costs a surface allocation each time and looks like a fault.
     */
    const val MIN_ENERGY = 0.02f
}
