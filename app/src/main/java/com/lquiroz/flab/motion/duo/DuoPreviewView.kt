package com.lquiroz.flab.motion.duo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.roundToInt

/** Local content only: no screenshot, overlay, wallpaper change or privileged helper. */
@RequiresApi(33)
internal class DuoPreviewView(context: Context) : View(context) {
    var onStatus: (String) -> Unit = {}
    private val shader = runCatching { RuntimeShader(ClassicGlassShader.source) }.getOrNull()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var textures = emptyList<Bitmap>()
    private var target = 135f
    private var visual = 135f
    private var inner = true
    private var smooth = true
    private var blur = 0.3f
    private var lastNanos = 0L
    private var status = ""

    fun configure(angle: Float, innerPanel: Boolean, blurAmount: Float, smoothing: Boolean) {
        val next = angle.takeIf { it.isFinite() }?.coerceIn(0f, 180f) ?: return
        if (target == next && inner == innerPanel && blur == blurAmount && smooth == smoothing) return
        if (inner != innerPanel || !smoothing) visual = next
        target = next; inner = innerPanel; blur = blurAmount; smooth = smoothing
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        clearTextures()
        if (w < 1 || h < 1) return
        val scale = minOf(1f, 640f / maxOf(w, h))
        val base = Bitmap.createBitmap(maxOf(1, (w * scale).roundToInt()), maxOf(1, (h * scale).roundToInt()), Bitmap.Config.ARGB_8888)
        drawDemo(Canvas(base), base.width.toFloat(), base.height.toFloat())
        textures = listOf(base) + (1..5).map { level ->
            Bitmap.createScaledBitmap(base, maxOf(1, base.width shr level), maxOf(1, base.height shr level), true)
        }
        shader?.let { s ->
            textures.forEachIndexed { index, bitmap ->
                val input = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                input.setLocalMatrix(Matrix().apply { setScale(base.width.toFloat() / bitmap.width, base.height.toFloat() / bitmap.height) })
                s.setInputShader(if (index == 0) "content" else "mip$index", input)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = textures.firstOrNull() ?: return
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 16f else (now - lastNanos) / 1_000_000f
        lastNanos = now
        visual = if (smooth) FrameSmoothing.step(visual, target, dt) else target
        paint.shader = null
        canvas.drawBitmap(bitmap, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), paint)
        val s = shader
        if (s == null || !canvas.isHardwareAccelerated) {
            report("Vista plana: el efecto necesita Android 13+ y GPU")
        } else {
            runCatching {
                s.setFloatUniform("texSize", bitmap.width.toFloat(), bitmap.height.toFloat())
                s.setFloatUniform("origin", 0f, 0f)
                s.setFloatUniform("extent", width.toFloat(), height.toFloat())
                s.setFloatUniform("sampleScale", 1f, 1f)
                s.setFloatUniform("sampleOffset", 0f, 0f)
                val uniforms = mapOf(
                    "progress" to DuoPreviewInput.progress(visual, inner), "inner" to if (inner) 1f else 0f,
                    "aaStrength" to 0.35f, "fallback" to 0f, "horizontal" to 0f, "reverse" to 0f,
                    "intensity" to 1f, "blurStrength" to blur, "seamOffset" to 0.07f,
                    "reflectedCover" to 0f, "windowReveal" to 1f,
                    "earlyStretch" to if (inner) 0.9f else 2.7f,
                    "endStretch" to if (inner) 0.45f else 1.25f, "startupEasing" to 1f,
                )
                uniforms.forEach { (name, value) -> s.setFloatUniform(name, value) }
                effectPaint.shader = s
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), effectPaint)
                report("Duo Classic · proyección y blur GPU")
            }.onFailure { report("Vista plana: ${it.javaClass.simpleName}") }
        }
        if (smooth && abs(visual - target) >= 0.01f) postInvalidateOnAnimation() else lastNanos = 0L
    }

    private fun report(value: String) {
        if (status == value) return
        status = value
        post { onStatus(value) }
    }

    private fun drawDemo(c: Canvas, w: Float, h: Float) {
        c.drawColor(Color.rgb(14, 19, 31))
        paint.shader = LinearGradient(0f, 0f, w, h, intArrayOf(Color.rgb(84, 116, 207), Color.rgb(31, 46, 72)), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, paint)
        paint.shader = null; paint.color = Color.WHITE; paint.textSize = w * 0.065f
        c.drawText("F/LAB", w * 0.07f, h * 0.16f, paint)
        paint.textSize = w * 0.026f
        c.drawText("Contenido de prueba", w * 0.07f, h * 0.23f, paint)
        repeat(4) { index ->
            val top = h * (0.32f + index * 0.135f)
            paint.color = Color.argb(45, 255, 255, 255)
            c.drawRoundRect(w * 0.06f, top, w * 0.94f, top + h * 0.1f, w * 0.025f, w * 0.025f, paint)
            paint.color = Color.rgb(175, 224, 241)
            c.drawCircle(w * 0.14f, top + h * 0.05f, h * 0.023f, paint)
            paint.color = Color.WHITE
            c.drawRoundRect(w * 0.23f, top + h * 0.028f, w * 0.72f, top + h * 0.038f, 3f, 3f, paint)
            paint.color = Color.argb(120, 255, 255, 255)
            c.drawRoundRect(w * 0.23f, top + h * 0.057f, w * 0.57f, top + h * 0.065f, 3f, 3f, paint)
        }
    }

    fun release() {
        effectPaint.shader = null
        clearTextures()
    }

    private fun clearTextures() {
        textures.distinct().forEach { it.recycle() }
        textures = emptyList()
    }
}
