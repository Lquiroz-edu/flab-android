package com.lquiroz.flab.access

import android.accessibilityservice.AccessibilityService
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.*

/** Non-focusable, non-touchable chromatic edge. It never captures or inspects app pixels. */
class ImmersiveOverlay(private val service: AccessibilityService) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private var top: View? = null
    private var bottom: View? = null

    fun show() {
        if (top != null) return
        val density = service.resources.displayMetrics.density
        top = edge(
            (44 * density).toInt(),
            Gravity.TOP,
            intArrayOf(Color.argb(115, 5, 6, 9), Color.TRANSPARENT),
        )
        bottom = edge(
            (34 * density).toInt(),
            Gravity.BOTTOM,
            intArrayOf(Color.TRANSPARENT, Color.argb(100, 5, 6, 9)),
        )
    }

    fun hide() {
        listOfNotNull(top, bottom).forEach { runCatching { manager.removeViewImmediate(it) } }
        top = null
        bottom = null
    }

    private fun edge(height: Int, gravity: Int, colors: IntArray): View {
        val view = View(service).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colors)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            contentDescription = null
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT,
        ).apply { this.gravity = gravity }
        manager.addView(view, params)
        return view
    }
}
