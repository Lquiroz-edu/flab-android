package com.lquiroz.flab.motion

/**
 * Tuning for the Fold Motion Engine.
 *
 * Every value is perceptual rather than physical: the engine does not try to model the real hinge,
 * it tries to make the on-screen response feel attached to the hand that is moving the device.
 *
 * Intensities are multipliers in `0f..1f` applied to the channel ranges in [MotionChannels].
 * A profile that sets every intensity to `0f` produces a mathematically valid but visually inert
 * engine, which is exactly what the Battery profile wants.
 */
data class MotionTuning(
    /** Spring stiffness, in progress-units per second squared per unit of error. */
    val stiffness: Float = 220f,
    /** Spring damping. Critical damping is `2 * sqrt(stiffness)`. */
    val damping: Float = 26f,
    /**
     * Hard ceiling on how fast progress may travel, in progress-units per second.
     *
     * This is what keeps the engine honest: a late burst of evidence can never be "caught up" with
     * a teleport, it still has to be travelled through, so the motion stays readable.
     */
    val maxVelocity: Float = 4.5f,
    /**
     * How long the engine keeps animating after the last piece of physical evidence.
     *
     * Once this elapses and the spring has arrived, the frame loop stops. This is the mechanism
     * behind "if the user stops opening the device, the animation stops too": stale evidence is
     * never extrapolated into a completed animation.
     */
    val evidenceHoldMillis: Long = 140L,
    /** Whether the spring may overshoot its target before settling. */
    val allowOvershoot: Boolean = true,
    /** Reference velocity at which transition energy reaches `1f`, in progress-units per second. */
    val referenceVelocity: Float = 1.8f,
    /** Seconds for transition energy to decay once movement stops. */
    val energyDecaySeconds: Float = 0.18f,

    val scaleIntensity: Float = 1f,
    val blurIntensity: Float = 1f,
    val dimIntensity: Float = 1f,
    val depthIntensity: Float = 1f,
    val offsetIntensity: Float = 1f,
    val expansionIntensity: Float = 1f,
    /**
     * How strongly the hinge pinches a continuous background as the device opens (Fold Wallpaper).
     *
     * Position-driven like [scaleIntensity], not movement-driven like the veil channels: a
     * background held at half-open should show a stable pinch, not one that fades in and out with
     * the hand's speed. Unlike every other channel here, this one is meaningless anywhere but a
     * wallpaper — F/LAB's Compose surfaces do not have a single continuous background image behind
     * the hinge, so `MotionChannelMapper` computes it regardless and callers that cannot use it
     * simply ignore it.
     */
    val warpIntensity: Float = 1f,
) {
    /** True when this tuning cannot produce any visible change and the engine can stay parked. */
    val isInert: Boolean
        get() = scaleIntensity == 0f &&
            blurIntensity == 0f &&
            dimIntensity == 0f &&
            depthIntensity == 0f &&
            offsetIntensity == 0f &&
            expansionIntensity == 0f &&
            warpIntensity == 0f

    companion object {
        /** Default. Visible, but never showy. */
        val Balanced = MotionTuning()

        /** Richer visual treatment, softer spring, more travel. */
        val Smooth = MotionTuning(
            stiffness = 170f,
            damping = 21f,
            maxVelocity = 5.5f,
            evidenceHoldMillis = 180L,
            referenceVelocity = 1.5f,
            energyDecaySeconds = 0.24f,
            blurIntensity = 1f,
            dimIntensity = 1f,
            depthIntensity = 1f,
            offsetIntensity = 1f,
            expansionIntensity = 1f,
            warpIntensity = 1f,
        )

        /** Discreet. Position-driven channels only, no veil, no overshoot. */
        val Minimal = MotionTuning(
            stiffness = 320f,
            damping = 34f,
            maxVelocity = 6f,
            evidenceHoldMillis = 90L,
            allowOvershoot = false,
            scaleIntensity = 0.45f,
            blurIntensity = 0f,
            dimIntensity = 0.25f,
            depthIntensity = 0.3f,
            offsetIntensity = 0.4f,
            expansionIntensity = 0.6f,
            warpIntensity = 0.5f,
        )

        /** Minimum intervention. The engine still tracks state, it just does not paint. */
        val Battery = MotionTuning(
            stiffness = 400f,
            damping = 40f,
            maxVelocity = 8f,
            evidenceHoldMillis = 60L,
            allowOvershoot = false,
            scaleIntensity = 0f,
            blurIntensity = 0f,
            dimIntensity = 0f,
            depthIntensity = 0f,
            offsetIntensity = 0f,
            expansionIntensity = 0f,
            warpIntensity = 0f,
        )
    }
}
