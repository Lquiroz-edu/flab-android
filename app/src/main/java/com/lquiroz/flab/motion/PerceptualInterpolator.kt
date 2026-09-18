package com.lquiroz.flab.motion

import kotlin.math.abs
import kotlin.math.exp

/**
 * Turns fold evidence into a continuous, physically-attached progress value.
 *
 * The contract that makes this different from "play an animation when an event arrives":
 *
 *  1. Progress only ever travels **towards the most recent evidence**. A `HALF_OPENED` event is a
 *     target of `0.5f`, not a trigger for a 0 -> 1 animation. If the user stops moving the device
 *     there, the engine stops at `0.5f` and settles.
 *  2. Travel speed is bounded by [MotionTuning.maxVelocity], so a late or coarse sample is walked
 *     to, never teleported to.
 *  3. Once evidence is older than [MotionTuning.evidenceHoldMillis] and the spring has arrived, the
 *     interpolator reports `settled`, and the owner is expected to stop asking for frames. No idle
 *     rendering, no polling.
 *
 * All time is in nanoseconds from a monotonic clock. The class is not thread-safe; the engine
 * confines it to the frame loop.
 */
class PerceptualInterpolator(
    private var tuning: MotionTuning = MotionTuning.Balanced,
    initialProgress: Float = 0f,
) {
    var progress: Float = initialProgress.coerceIn(0f, 1f)
        private set
    var velocity: Float = 0f
        private set
    var energy: Float = 0f
        private set

    private var target: Float = progress
    private var source: EvidenceSource = EvidenceSource.PostureEvent
    private var lastEvidenceNanos: Long = Long.MIN_VALUE
    private var lastFrameNanos: Long = Long.MIN_VALUE

    fun updateTuning(tuning: MotionTuning) {
        this.tuning = tuning
    }

    /** Records a new observation of the physical device. Cheap; safe to call per sensor event. */
    fun submit(evidence: FoldEvidence) {
        target = evidence.progress
        source = evidence.source
        lastEvidenceNanos = evidence.timestampNanos
    }

    /**
     * Snaps straight to [value] without any travel. Used by the Live Preview scrubber, where the
     * finger *is* the physical evidence, and on a cold start where there is nothing to animate from.
     */
    fun jumpTo(value: Float, source: EvidenceSource = EvidenceSource.Manual, nowNanos: Long) {
        progress = value.coerceIn(0f, 1f)
        target = progress
        velocity = 0f
        energy = 0f
        this.source = source
        lastEvidenceNanos = nowNanos
        lastFrameNanos = nowNanos
    }

    /** Advances the simulation to [nowNanos] and returns the frame to render. */
    fun advance(nowNanos: Long): FoldMotionFrame {
        val previousFrameNanos = lastFrameNanos
        lastFrameNanos = nowNanos
        if (previousFrameNanos == Long.MIN_VALUE) {
            return frame(settled = isArrived() && isEvidenceStale(nowNanos))
        }

        // Clamp dt so a dropped frame, a stopped emulator or a device coming back from sleep cannot
        // inject a huge impulse into the spring and produce a visible jump (DoD 30).
        val dt = ((nowNanos - previousFrameNanos).coerceAtLeast(0L) / 1_000_000_000.0)
            .toFloat()
            .coerceAtMost(MAX_STEP_SECONDS)
        if (dt <= 0f) return frame(settled = false)

        val startProgress = progress
        integrate(dt)

        if (!tuning.allowOvershoot) {
            // Never travel past the target we were given; clamping the interval also kills the sign
            // flip that would otherwise make the spring ring around a discrete posture value.
            val low = minOf(startProgress, target)
            val high = maxOf(startProgress, target)
            if (progress !in low..high) {
                progress = progress.coerceIn(low, high)
                velocity = 0f
            }
        }
        progress = progress.coerceIn(0f, 1f)

        val measured = (progress - startProgress) / dt
        val instantEnergy = (abs(measured) / tuning.referenceVelocity).coerceIn(0f, 1f)
        energy = if (instantEnergy >= energy) {
            // Energy rises immediately: the veil must not lag behind the hand.
            instantEnergy
        } else {
            val decay = exp(-dt / tuning.energyDecaySeconds.coerceAtLeast(MIN_DECAY_SECONDS))
            val decayed = energy * decay
            if (decayed <= ENERGY_EPSILON) 0f else maxOf(decayed, instantEnergy)
        }

        return frame(settled = isArrived() && isEvidenceStale(nowNanos))
    }

    private fun integrate(dt: Float) {
        // Semi-implicit Euler, sub-stepped so a stiff spring stays stable at low frame rates.
        val steps = ((dt / MAX_SUBSTEP_SECONDS).toInt() + 1).coerceAtMost(MAX_SUBSTEPS)
        val step = dt / steps
        repeat(steps) {
            val acceleration = tuning.stiffness * (target - progress) - tuning.damping * velocity
            velocity = (velocity + acceleration * step)
                .coerceIn(-tuning.maxVelocity, tuning.maxVelocity)
            progress += velocity * step
        }
    }

    private fun isArrived(): Boolean =
        abs(target - progress) < PROGRESS_EPSILON && abs(velocity) < VELOCITY_EPSILON

    private fun isEvidenceStale(nowNanos: Long): Boolean {
        if (lastEvidenceNanos == Long.MIN_VALUE) return true
        val ageMillis = (nowNanos - lastEvidenceNanos) / 1_000_000L
        return ageMillis >= tuning.evidenceHoldMillis
    }

    private fun frame(settled: Boolean): FoldMotionFrame {
        if (settled) {
            progress = target
            velocity = 0f
            energy = 0f
        }
        return FoldMotionFrame(
            progress = progress,
            velocity = if (settled) 0f else velocity,
            energy = energy,
            settled = settled,
            source = source,
        )
    }

    private companion object {
        const val MAX_STEP_SECONDS = 1f / 24f
        const val MAX_SUBSTEP_SECONDS = 1f / 120f
        const val MAX_SUBSTEPS = 8
        const val PROGRESS_EPSILON = 0.001f
        const val VELOCITY_EPSILON = 0.01f
        const val ENERGY_EPSILON = 0.004f
        const val MIN_DECAY_SECONDS = 0.01f
    }
}
