package com.lquiroz.flab.access

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.lquiroz.flab.core.FLabCore
import com.lquiroz.flab.core.FLabState
import com.lquiroz.flab.motion.FoldShader
import com.lquiroz.flab.motion.FoldSurface
import com.lquiroz.flab.motion.FoldTrigger
import com.lquiroz.flab.profiles.ProfileCatalog

/**
 * Explicitly opt-in, no-root continuity experiment. A single in-memory frame
 * masks panel hand-off while the real hinge continues to control the shader.
 * It never receives touch input and destroys the frame when the transition ends.
 */
class SystemMotionOverlay(private val service: AccessibilityService) : FLabCore.Listener {
    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val trigger = FoldTrigger()
    private var overlay: SnapshotView? = null
    private var frame: SnapshotFrame? = null
    private var captureInFlight = false
    private var generation = 0
    private var foregroundPackage: String? = null
    private var windowSignature = ""
    private var lastState = FLabState()

    private val staleFrame = Runnable {
        hide()
        FLabCore.reportModuleEvent("System Motion released a paused frame")
    }

    fun start() {
        FLabCore.addListener(this)
    }

    fun stop() {
        FLabCore.removeListener(this)
        hide()
    }

    fun onWindowChanged(packageName: String?) {
        foregroundPackage = packageName
        if (!allowed()) {
            hide()
            return
        }
        val bounds = windowManager.currentWindowMetrics.bounds
        val signature = "${bounds.width()}x${bounds.height()}"
        val changed = windowSignature.isNotEmpty() && signature != windowSignature
        windowSignature = signature
        if (changed && lastState.foldProgress in .04f..0.96f) capture()
    }

    override fun onState(state: FLabState) {
        lastState = state
        if (!allowed()) {
            hide()
            return
        }
        val angle = state.hingeAngle ?: return
        overlay?.updateMotion(state.foldProgress, state.hingeVelocityDegPerSecond)
        if (trigger.accept(angle, SystemClock.uptimeMillis())) capture()

        handler.removeCallbacks(staleFrame)
        if (state.foldProgress <= .025f || state.foldProgress >= .985f) {
            handler.postDelayed({ hide() }, 90L)
        } else if (overlay != null) {
            // Tent mode must return to live content instead of freezing an app.
            handler.postDelayed(staleFrame, 720L)
        }
    }

    private fun allowed(): Boolean {
        val prefs = service.getSharedPreferences("flab", AccessibilityService.MODE_PRIVATE)
        return lastState.enabled && prefs.getBoolean("system_motion_experiment", false) &&
            !ProfileCatalog.isProtected(foregroundPackage)
    }

    private fun capture() {
        if (captureInFlight || !allowed()) return
        hide()
        captureInFlight = true
        val request = ++generation
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    captureInFlight = false
                    if (request != generation || !allowed()) {
                        result.hardwareBuffer.close()
                        return
                    }
                    val bitmap = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    if (bitmap == null) {
                        result.hardwareBuffer.close()
                        FLabCore.reportModuleEvent("System Motion capture unavailable")
                        return
                    }
                    show(SnapshotFrame(bitmap, result.hardwareBuffer))
                }

                override fun onFailure(errorCode: Int) {
                    captureInFlight = false
                    FLabCore.reportModuleEvent("System Motion skipped capture ($errorCode)")
                }
            },
        )
    }

    private fun show(snapshot: SnapshotFrame) {
        val view = SnapshotView(service, snapshot.bitmap).apply {
            updateMotion(lastState.foldProgress, lastState.hingeVelocityDegPerSecond)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            title = "F/LAB System Motion"
        }
        runCatching { windowManager.addView(view, params) }
            .onSuccess {
                overlay = view
                frame = snapshot
                FLabCore.reportModuleEvent("System Motion frame active")
            }
            .onFailure {
                snapshot.close()
                FLabCore.recordModuleError("System Motion", it)
            }
    }

    fun hide() {
        handler.removeCallbacks(staleFrame)
        generation++
        overlay?.let { runCatching { windowManager.removeViewImmediate(it) } }
        overlay = null
        frame?.close()
        frame = null
    }

    private class SnapshotFrame(val bitmap: Bitmap, private val buffer: HardwareBuffer) {
        fun close() {
            if (!bitmap.isRecycled) bitmap.recycle()
            buffer.close()
        }
    }

    private class SnapshotView(context: android.content.Context, private val snapshot: Bitmap) : View(context) {
        private val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val node = RenderNode("F/LAB continuity frame")
        private val shader = FoldShader(context)
        private var progress = 1f
        private var velocity = 0f

        fun updateMotion(progress: Float, velocity: Float) {
            this.progress = progress.coerceIn(0f, 1f)
            this.velocity = velocity
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            if (width < 2 || height < 2) return
            node.setPosition(0, 0, width, height)
            val recording = node.beginRecording(width, height)
            recording.drawColor(Color.BLACK)
            recording.drawBitmap(snapshot, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), sourcePaint)
            node.endRecording()
            val surface = if (width.toFloat() / height < .68f) FoldSurface.COVER else FoldSurface.INNER
            node.setRenderEffect(shader.effect(width.toFloat(), height.toFloat(), progress, surface, .68f, velocity))
            canvas.drawRenderNode(node)
        }
    }
}
