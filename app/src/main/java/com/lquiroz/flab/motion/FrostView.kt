package com.lquiroz.flab.motion

import android.content.Context
import android.graphics.*
import android.view.View

/** Independent optical displacement with Android's Gaussian blur. No borrowed shader source. */
class FrostView(context: Context, private val bitmap: Bitmap) : View(context) {
    private val source = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val shader = RuntimeShader("""
        uniform shader image;
        uniform float2 size;
        uniform float amount;
        half4 main(float2 p) {
            float2 q = p + float2((p.x - size.x * 0.5) * 0.015 * amount, 0.0);
            half4 c = image.eval(q);
            return half4(c.rgb * (1.0 - 0.035 * amount), c.a);
        }
    """.trimIndent())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@FrostView.shader }
    var amount = 0f
        set(value) { field = value.coerceIn(0f, 1f); updateBlur(); invalidate() }
    var strength = 0.7f
        set(value) { field = value.coerceIn(0.2f, 1.5f); updateBlur(); invalidate() }

    private fun updateBlur() {
        val radius = (amount * strength * 14f * resources.displayMetrics.density).coerceAtMost(64f)
        setRenderEffect(if (radius < 0.1f) null else
            RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
    }

    init { shader.setInputShader("image", source) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        source.setLocalMatrix(Matrix().apply {
            setScale(w.toFloat() / bitmap.width, h.toFloat() / bitmap.height)
        })
        shader.setFloatUniform("size", w.toFloat(), h.toFloat())
    }
    override fun onDraw(canvas: Canvas) {
        shader.setFloatUniform("amount", amount)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
