package com.lquiroz.flab.motion

/**
 * Where a piece of fold evidence came from, ordered by how continuous it is.
 *
 * The engine always prefers the most continuous source it can get. Anything below
 * [HingeAngle] only produces sparse samples, so the interpolator has to fill the gaps — but it
 * fills them *between* known samples, it never runs ahead of the last one.
 */
enum class EvidenceSource(val continuous: Boolean, val label: String) {
    /** `Sensor.TYPE_HINGE_ANGLE`. Genuinely continuous on Samsung foldables running Android 11+. */
    HingeAngle(true, "Hinge angle"),

    /** Window size changes reported while the device is being opened or closed. */
    WindowMetrics(false, "Window metrics"),

    /** `FoldingFeature.State` transitions: FLAT / HALF_OPENED. Three values at best. */
    PostureEvent(false, "Posture event"),

    /** The Live Preview scrubber, or a test. */
    Manual(true, "Manual"),
}

/**
 * One observation of the device's physical opening, normalised to `0f` (closed) .. `1f` (flat open).
 *
 * @param progress normalised opening, already clamped to `0f..1f`.
 * @param timestampNanos monotonic time the observation was made, from the same clock as
 *   [PerceptualInterpolator.advance].
 */
data class FoldEvidence(
    val progress: Float,
    val source: EvidenceSource,
    val timestampNanos: Long,
) {
    init {
        require(progress in 0f..1f) { "progress must be normalised, was $progress" }
    }
}

/**
 * The engine's current opinion about the fold, produced once per frame.
 *
 * @param progress the interpolated opening.
 * @param velocity signed rate of change in progress-units per second.
 * @param energy `0f..1f` smoothed measure of *how much the device is moving right now*.
 *   Note this is deliberately not derived from [progress]: a device held steady at half-open has
 *   high progress but zero energy, and must therefore show no transition treatment at all.
 * @param settled true once the engine has arrived and evidence has gone stale. While settled the
 *   frame loop can be stopped entirely.
 * @param source the source of the most recent evidence.
 */
data class FoldMotionFrame(
    val progress: Float,
    val velocity: Float,
    val energy: Float,
    val settled: Boolean,
    val source: EvidenceSource,
) {
    companion object {
        val Closed = FoldMotionFrame(0f, 0f, 0f, settled = true, source = EvidenceSource.PostureEvent)
        val Open = FoldMotionFrame(1f, 0f, 0f, settled = true, source = EvidenceSource.PostureEvent)
    }
}
