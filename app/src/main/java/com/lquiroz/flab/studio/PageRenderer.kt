package com.lquiroz.flab.studio

import android.content.Context
import android.graphics.*
import java.io.File
import kotlin.math.*

/** Two physical leaves. Only the moving leaf receives motion blur. */
class PageRenderer(private val context: Context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val node = RenderNode("F/LAB moving page")
    private var day = landscape(false)
    private var night = landscape(true)
    var radius = 0.045f
    var blur = 0.45f
    var dark = false

    fun reload() {
        val file = File(context.filesDir, "landscape.jpg")
        val custom = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
        day = custom ?: landscape(false)
        night = if (custom == null) landscape(true) else Bitmap.createBitmap(
            custom.width, custom.height, Bitmap.Config.ARGB_8888).also {
            val tint = Paint(Paint.FILTER_BITMAP_FLAG)
            tint.colorFilter = ColorMatrixColorFilter(floatArrayOf(
                .30f,0f,0f,0f,0f, 0f,.36f,0f,0f,2f, 0f,0f,.49f,0f,8f, 0f,0f,0f,1f,0f))
            Canvas(it).drawBitmap(custom, 0f, 0f, tint)
        }
    }

    fun draw(canvas: Canvas, width: Int, height: Int, raw: Float, moving: Boolean, full: Boolean = false) {
        if (width < 2 || height < 2) return
        val p = PageGeometry.progress(raw)
        canvas.drawColor(if (dark) Color.rgb(18,20,24) else Color.rgb(247,247,242))
        val w = width * if (full) .94f else .87f
        val h = if (full) height * .90f else min(height * .72f, w * .68f)
        val rect = RectF((width-w)/2f,(height-h)/2f,(width+w)/2f,(height+h)/2f)
        val corner = min(w,h)*radius
        val cx = rect.centerX()
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect,corner,corner,Path.Direction.CW) })
        canvas.save(); canvas.clipRect(rect.left,rect.top,cx,rect.bottom)
        drawCrop(canvas, night, rect); canvas.restore()
        canvas.save(); canvas.clipRect(cx,rect.top,rect.right,rect.bottom)
        drawCrop(canvas, day, rect); canvas.restore()
        canvas.restore()

        val edge = PageGeometry.edge(p)
        if (abs(edge) < .015f) return
        val outer = cx + w*.5f*edge
        val lift = PageGeometry.lift(p)*h*.095f
        val half = (w*.5f).roundToInt().coerceAtLeast(1)
        val pageH = h.roundToInt().coerceAtLeast(1)
        val left = edge < 0
        val quad = if (left) floatArrayOf(outer,rect.top-lift,cx,rect.top,cx,rect.bottom,outer,rect.bottom+lift)
            else floatArrayOf(cx,rect.top,outer,rect.top-lift,outer,rect.bottom+lift,cx,rect.bottom)
        val matrix = Matrix().apply { setPolyToPoly(floatArrayOf(0f,0f,half.toFloat(),0f,
            half.toFloat(),pageH.toFloat(),0f,pageH.toFloat()),0,quad,0,4) }
        val pageRect = RectF(0f,0f,half.toFloat(),pageH.toFloat())
        fun contents(c: Canvas) {
            c.save()
            c.clipPath(Path().apply { addRoundRect(pageRect,corner,corner,Path.Direction.CW) })
            val whole = if (left) RectF(0f,0f,w,h) else RectF(-w*.5f,0f,w*.5f,h)
            drawCrop(c,if(left) day else night,whole)
            paint.color = Color.argb((PageGeometry.lift(p)*25).toInt(),0,0,0)
            c.drawRect(pageRect,paint)
            c.restore()
        }
        canvas.save()
        canvas.concat(matrix)
        val sigma = if(moving) blur * PageGeometry.lift(p) * 13f * context.resources.displayMetrics.density else 0f
        if (canvas.isHardwareAccelerated) {
            node.setPosition(0,0,half,pageH)
            val recording = node.beginRecording(half,pageH)
            contents(recording)
            node.endRecording()
            node.setRenderEffect(if(sigma < .1f) null else RenderEffect.createBlurEffect(sigma,sigma,Shader.TileMode.CLAMP))
            canvas.drawRenderNode(node)
        } else contents(canvas)
        canvas.restore()
    }

    private fun drawCrop(c: Canvas, image: Bitmap, dest: RectF) {
        val scale = max(dest.width()/image.width,dest.height()/image.height)
        val sw = dest.width()/scale; val sh = dest.height()/scale
        val src = Rect(((image.width-sw)/2).toInt(),((image.height-sh)/2).toInt(),
            ((image.width+sw)/2).toInt(),((image.height+sh)/2).toInt())
        paint.color = Color.WHITE; paint.colorFilter = null
        c.drawBitmap(image,src,dest,paint)
    }

    private fun landscape(dark: Boolean): Bitmap {
        val b = Bitmap.createBitmap(1200,900,Bitmap.Config.ARGB_8888)
        val c = Canvas(b); val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f,0f,0f,900f,
            if(dark) intArrayOf(Color.rgb(12,29,46),Color.rgb(78,76,91),Color.rgb(41,42,49))
            else intArrayOf(Color.rgb(130,166,180),Color.rgb(230,218,182),Color.rgb(198,179,144)),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,1200f,900f,p); p.shader=null
        val paths = arrayOf(
            "", "", "")
        for(i in paths.indices) {
            p.color = if(dark) Color.rgb(35+i*8,40+i*7,49+i*7)
                else Color.rgb(107+i*37,112+i*28,103+i*23)
            val y=430f+i*145
            val path=Path().apply { moveTo(0f,y+70); cubicTo(330f,y-145,610f,y+160,1200f,y-40)
                lineTo(1200f,900f); lineTo(0f,900f); close() }
            c.drawPath(path,p)
        }
        return b
    }
}
