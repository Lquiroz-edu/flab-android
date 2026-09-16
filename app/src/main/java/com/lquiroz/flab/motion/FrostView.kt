package com.lquiroz.flab.motion

import android.content.Context
import android.graphics.*
import android.view.View

/** Independent, bounded nine-sample frost filter. No borrowed shader source. */
class FrostView(context: Context, private val bitmap: Bitmap) : View(context) {
    private val source = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val shader = RuntimeShader("""
        uniform shader image;
        uniform float2 size;
        uniform float amount;
        uniform float strength;
        half4 main(float2 p) {
            float side = abs(p.x / max(size.x, 1.0) - 0.5) * 2.0;
            float r = amount * strength * (4.0 + 22.0 * side * side);
            float2 q = p + float2((p.x - size.x * 0.5) * 0.015 * amount, 0.0);
            half4 c = image.eval(q) * 0.20;
            c += image.eval(q + float2(r, 0.0)) * 0.12;
            c += image.eval(q - float2(r, 0.0)) * 0.12;
            c += image.eval(q + float2(0.0, r)) * 0.12;
            c += image.eval(q - float2(0.0, r)) * 0.12;
            c += image.eval(q + float2(r, r) * 0.7071) * 0.08;
            c += image.eval(q - float2(r, r) * 0.7071) * 0.08;
            c += image.eval(q + float2(r, -r) * 0.7071) * 0.08;
            c += image.eval(q - float2(r, -r) * 0.7071) * 0.08;
            return half4(c.rgb * (1.0 - 0.035 * amount), c.a);
        }
    """.trimIndent())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = this@FrostView.shader }
    var amount = 0f
        set(value) { field = value.coerceIn(0f, 1f); invalidate() }
    var strength = 0.7f
        set(value) { field = value.coerceIn(0.2f, 1.5f); invalidate() }

    init { shader.setInputShader("image", source) }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        source.setLocalMatrix(Matrix().apply {
            setScale(w.toFloat() / bitmap.width, h.toFloat() / bitmap.height)
        })
        shader.setFloatUniform("size", w.toFloat(), h.toFloat())
    }
    override fun onDraw(canvas: Canvas) {
        shader.setFloatUniform("amount", amount)
        shader.setFloatUniform("strength", strength)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }
}
