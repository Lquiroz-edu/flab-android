package com.lquiroz.flab.motion

/**
 * The Fold Motion module (DoD 3, DoD 44).
 *
 * Owns one [PerceptualInterpolator] and turns its frames into [MotionChannels]. It does not own a
 * loop: the renderer asks for a frame when it is about to draw one, and [needsFrames] tells it
 * whether to ask again. That inversion is what keeps DoD 22 honest — when nothing is moving,
 * nobody is scheduling work.
 *
 * Not thread-safe. Everything that touches it — the evidence collector and the frame loop —
 * runs on the main thread.
 */
class FoldMotionEngine(tuning: MotionTuning = MotionTuning.Balanced) {

    private val interpolator = PerceptualInterpolator(tuning)
    private var tuning: MotionTuning = tuning

    /**
     * Set by [submit], cleared by the first [advance] that consumes it.
     *
     * Without it the engine would refuse to start: a settled frame reports `settled = true`, so
     * brand-new evidence would arrive, find [needsFrames] false, and never get a frame to act on.
     */
    private var hasPendingEvidence = false

    var frame: FoldMotionFrame = FoldMotionFrame.Closed
        private set

    var channels: MotionChannels = MotionChannels.Neutral
        private set

    /**
     * Whether the renderer should schedule another frame.
     *
     * False as soon as the spring has arrived and the evidence has gone stale, and always false
     * for an inert tuning — the Battery profile never schedules a frame at all.
     */
    val needsFrames: Boolean
        get() = !tuning.isInert && (hasPendingEvidence || !frame.settled)

    fun updateTuning(tuning: MotionTuning) {
        this.tuning = tuning
        interpolator.updateTuning(tuning)
        // Re-map straight away so a profile change is visible without waiting for movement.
        channels = MotionChannelMapper.map(frame, tuning)
    }

    /**
     * Feeds a new observation of the physical device.
     *
     * Safe to call while the frame loop is running: both run on the main thread, and the next
     * [advance] picks the new target up mid-flight. That is the point — during a real fold, samples
     * keep arriving while the engine is already animating, and an engine that could not accept them
     * until it settled would lurch between stale targets instead of tracking the hinge.
     */
    fun submit(evidence: FoldEvidence) {
        interpolator.submit(evidence)
        hasPendingEvidence = true
    }

    /** Snaps to [progress] with no travel. Used by Live Preview, where the finger is the evidence. */
    fun scrubTo(progress: Float, nowNanos: Long) {
        interpolator.jumpTo(progress, EvidenceSource.Manual, nowNanos)
        hasPendingEvidence = false
        frame = FoldMotionFrame(progress.coerceIn(0f, 1f), 0f, 0f, settled = true, source = EvidenceSource.Manual)
        channels = MotionChannelMapper.map(frame, tuning)
    }

    /** Advances to [nowNanos] and returns the channels to draw with. */
    fun advance(nowNanos: Long): MotionChannels {
        hasPendingEvidence = false
        frame = interpolator.advance(nowNanos)
        channels = MotionChannelMapper.map(frame, tuning)
        return channels
    }
}
