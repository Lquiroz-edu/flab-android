package com.lquiroz.flab.motion

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.util.Log
import com.lquiroz.flab.R

enum class FoldSurface { COVER, INNER }

/**
 * GPU fold material shared by the preview, wallpaper and optional continuity
 * overlay. The shader is compiled once per renderer; a failure degrades to a
 * normal bitmap rather than affecting One UI.
 */
class FoldShader(context: Context) {
    private val pxPerMm = context.resources.displayMetrics.xdpi
        .takeIf { it.isFinite() && it > 0f }
        ?.div(25.4f) ?: 6f
    private val runtime = runCatching {
        val source = context.resources.openRawResource(R.raw.flab_fold)
            .bufferedReader().use { it.readText() }
        RuntimeShader(source)
    }.onFailure { Log.e(TAG, "AGSL unavailable; using flat renderer", it) }.getOrNull()

    val available: Boolean get() = runtime != null

    fun effect(
        width: Float,
        height: Float,
        progress: Float,
        surface: FoldSurface,
        blur: Float,
        velocityDegPerSecond: Float,
    ): RenderEffect? {
        val shader = runtime ?: return null
        if (width <= 1f || height <= 1f) return null
        val strength = (0.72f + blur.coerceIn(0f, 1f) * 0.38f)
        val tilt = when (surface) {
            FoldSurface.COVER -> FoldMotionCurve.coverTilt(progress, strength)
            FoldSurface.INNER -> FoldMotionCurve.innerTilt(progress, strength)
        }
        if (tilt < 0.04f) return null
        val velocity = FoldMotionCurve.velocityEnergy(velocityDegPerSecond)
        val splitsX = width <= height || surface == FoldSurface.COVER
        val splitExtent = if (splitsX) width else height
        val hinge = if (surface == FoldSurface.COVER) 0f else splitExtent * 0.5f
        val eye = if (surface == FoldSurface.COVER) splitExtent * 0.5f else hinge
        shader.setFloatUniform("resolution", width, height)
        shader.setFloatUniform("tiltDegrees", tilt.coerceAtMost(48f))
        shader.setFloatUniform("eyeDistancePx", 320f * pxPerMm)
        shader.setFloatUniform("hingePos", hinge)
        shader.setFloatUniform("eyePos", eye)
        shader.setFloatUniform("axisSwap", if (splitsX) 0f else 1f)
        shader.setFloatUniform("paneMode", when (surface) {
            FoldSurface.COVER -> 1f
            FoldSurface.INNER -> 0f
        })
        shader.setFloatUniform("blurSpread", 0.025f + blur.coerceIn(0f, 1f) * 0.075f + velocity * 0.018f)
        shader.setFloatUniform("darkening", (0.0045f + blur * 0.0025f) * 6f / pxPerMm)
        shader.setFloatUniform("motionEnergy", velocity)
        return runCatching { RenderEffect.createRuntimeShaderEffect(shader, "content") }
            .onFailure { Log.e(TAG, "AGSL effect failed; using flat renderer", it) }
            .getOrNull()
    }

    companion object { private const val TAG = "FLabFoldShader" }
}
