package com.lquiroz.flab.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.Shader
import com.lquiroz.flab.motion.FoldShader
import com.lquiroz.flab.motion.FoldSurface
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Shared, crease-free AGSL material used by Preview and the One UI wallpaper. */
class PageRenderer(private val context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val node = RenderNode("F/LAB fold material")
    private val shader = FoldShader(context)
    private var image = generatedLandscape()

    var radius = 0.045f
    var blur = 0.58f
    var dark = false
    var velocityDegPerSecond = 0f

    val shaderAvailable: Boolean get() = shader.available

    fun reload() {
        val file = File(context.filesDir, "landscape.jpg")
        image = if (file.exists()) BitmapFactory.decodeFile(file.path) ?: generatedLandscape()
        else generatedLandscape()
    }

    fun draw(canvas: Canvas, width: Int, height: Int, raw: Float, moving: Boolean, full: Boolean = false) {
        if (width < 2 || height < 2) return
        val progress = PageGeometry.progress(raw)
        canvas.drawColor(if (dark) Color.rgb(10, 11, 14) else Color.rgb(247, 247, 242))

        val rect = if (full) RectF(0f, 0f, width.toFloat(), height.toFloat()) else {
            val targetWidth = width * .90f
            val targetHeight = min(height * .76f, targetWidth * .76f)
            RectF(
                (width - targetWidth) / 2f,
                (height - targetHeight) / 2f,
                (width + targetWidth) / 2f,
                (height + targetHeight) / 2f,
            )
        }
        val outWidth = rect.width().roundToInt().coerceAtLeast(2)
        val outHeight = rect.height().roundToInt().coerceAtLeast(2)
        val corner = if (full) 0f else min(rect.width(), rect.height()) * radius
        val surface = if (width.toFloat() / height < .68f) FoldSurface.COVER else FoldSurface.INNER

        node.setPosition(0, 0, outWidth, outHeight)
        val recording = node.beginRecording(outWidth, outHeight)
        drawCrop(recording, image, RectF(0f, 0f, outWidth.toFloat(), outHeight.toFloat()))
        node.endRecording()
        node.setRenderEffect(
            if (canvas.isHardwareAccelerated) shader.effect(
                outWidth.toFloat(),
                outHeight.toFloat(),
                progress,
                surface,
                blur,
                if (moving) velocityDegPerSecond else 0f,
            ) else null,
        )

        canvas.save()
        if (corner > 0f) {
            canvas.clipPath(Path().apply { addRoundRect(rect, corner, corner, Path.Direction.CW) })
        }
        canvas.translate(rect.left, rect.top)
        canvas.drawRenderNode(node)
        canvas.restore()
    }

    private fun drawCrop(canvas: Canvas, bitmap: Bitmap, destination: RectF) {
        val scale = max(destination.width() / bitmap.width, destination.height() / bitmap.height)
        val sourceWidth = destination.width() / scale
        val sourceHeight = destination.height() / scale
        val source = Rect(
            ((bitmap.width - sourceWidth) / 2f).toInt(),
            ((bitmap.height - sourceHeight) / 2f).toInt(),
            ((bitmap.width + sourceWidth) / 2f).toInt(),
            ((bitmap.height + sourceHeight) / 2f).toInt(),
        )
        paint.color = Color.WHITE
        paint.shader = null
        paint.colorFilter = null
        canvas.drawBitmap(bitmap, source, destination, paint)
    }

    private fun generatedLandscape(): Bitmap {
        val bitmap = Bitmap.createBitmap(1440, 1800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val layer = Paint(Paint.ANTI_ALIAS_FLAG)
        layer.shader = LinearGradient(
            0f, 0f, 1440f, 1800f,
            if (dark) intArrayOf(Color.rgb(6, 13, 24), Color.rgb(34, 45, 70), Color.rgb(9, 10, 15))
            else intArrayOf(Color.rgb(113, 155, 185), Color.rgb(225, 208, 165), Color.rgb(187, 149, 118)),
            null,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, 1440f, 1800f, layer)
        layer.shader = null
        repeat(4) { index ->
            layer.color = if (dark) Color.rgb(18 + index * 9, 25 + index * 9, 39 + index * 10)
            else Color.rgb(70 + index * 28, 80 + index * 23, 77 + index * 18)
            val y = 760f + index * 255f
            val path = Path().apply {
                moveTo(0f, y + 100f)
                cubicTo(380f, y - 280f, 830f, y + 260f, 1440f, y - 90f)
                lineTo(1440f, 1800f)
                lineTo(0f, 1800f)
                close()
            }
            canvas.drawPath(path, layer)
        }
        return bitmap
    }
}
