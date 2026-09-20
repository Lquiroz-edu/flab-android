package com.lquiroz.flab.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.withClip
import com.lquiroz.flab.FLabApplication
import com.lquiroz.flab.core.FLabCore
import com.lquiroz.flab.motion.FoldMotionEngine
import com.lquiroz.flab.motion.MotionChannels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Fold Wallpaper — the Duo-style hinge effect, on a surface F/LAB actually owns.
 *
 * `System effects` (`FoldOverlayWindow`) can blur and dim whatever the compositor has behind it,
 * but it cannot bend that content's geometry — a live wallpaper is the one surface a sandboxed app
 * can render pixel by pixel. Here, that ownership is spent on reproducing the effect analysed from
 * Apple's iPhone Duo footage: the background is one continuous image, pinched at the hinge in
 * proportion to how closed the device is, rather than cut into two static halves.
 *
 * ### Why a WallpaperService and not something living inside the app
 *
 * A live wallpaper is visible on the home and lock screens — precisely where F/LAB itself is not
 * running. It reads [FLabCore.evidence] the same way [FLabOverlayService] does, through
 * [FLabCore.requestContinuousTracking], so both background consumers can hold the hinge sensor open
 * at once without fighting over it (see the ref-counting note on that method).
 *
 * ### Cost
 *
 * The warp animation is driven by the same `while (engine.needsFrames)` discipline as every other
 * renderer in this project: frames run only while the device is actually moving, and stop the
 * instant it settles. The one addition a wallpaper needs that nothing else does is a low-rate clock
 * tick, because the time card has to stay correct even when nothing is folding — that tick is
 * capped at twice a minute and is the only periodic work anywhere in F/LAB.
 */
class FoldWallpaperService : WallpaperService() {

    /**
     * The live engine, so a system configuration change (dark/light switching) can be forwarded to
     * it. `Engine` has no `onConfigurationChanged` of its own — only the enclosing [Service] does —
     * so this is the standard way a `WallpaperService` learns about one.
     */
    private var activeEngine: FoldEngine? = null

    override fun onCreateEngine(): Engine = FoldEngine()

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        activeEngine?.onSystemConfigurationChanged()
    }

    private inner class FoldEngine : Engine() {

        private val scope = CoroutineScope(
            SupervisorJob() + Handler(Looper.getMainLooper()).asCoroutineDispatcher(),
        )
        private val motionEngine = FoldMotionEngine()
        private var loopJob: Job? = null
        private var visible = false

        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var background: Bitmap? = null
        private var frostedSource: Bitmap? = null
        private val warpVertices = FloatArray(FoldWarpMesh.VERTEX_COUNT)

        // Cached off the frame path on purpose. drawFrame runs up to the panel's refresh rate
        // during a transition, and reading these fresh each time would mean a Binder round trip to
        // the system (battery) and a SimpleDateFormat allocation (clock) up to ~120 times a second
        // — exactly the per-frame cost this project's discipline elsewhere is written to avoid.
        private var cachedBatteryPercent = 0
        private var cachedCharging = false
        private var cachedTimeText = ""
        private var cachedDateText = ""
        private var batteryReceiver: BroadcastReceiver? = null

        private val core: FLabCore get() = (application as FLabApplication).core

        override fun onSurfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int,
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceWidth = width
            surfaceHeight = height
            rebuildBackground()
            drawFrame(motionEngine.channels)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                activeEngine = this
                core.requestContinuousTracking(this)
                registerBatteryReceiver()
                refreshClockCache()
                startLoop()
                drawFrame(motionEngine.channels)
            } else {
                if (activeEngine === this) activeEngine = null
                loopJob?.cancel()
                loopJob = null
                core.releaseContinuousTracking(this)
                unregisterBatteryReceiver()
            }
        }

        override fun onDestroy() {
            if (activeEngine === this) activeEngine = null
            loopJob?.cancel()
            core.releaseContinuousTracking(this)
            unregisterBatteryReceiver()
            scope.cancel()
            background?.recycle()
            frostedSource?.recycle()
            super.onDestroy()
        }

        /**
         * Redraws so the background palette follows the system dark/light setting the way
         * [com.lquiroz.flab.ui.theme.FLabTheme] does for the app itself (DoD 40) — a wallpaper that
         * only ever renders in one theme would look wrong on whichever side of the system setting
         * it was built for. Called by the enclosing [Service]; `Engine` has no config-change hook.
         */
        fun onSystemConfigurationChanged() {
            rebuildBackground()
            drawFrame(motionEngine.channels)
        }

        private fun startLoop() {
            if (loopJob?.isActive == true) return
            loopJob = scope.launch {
                val wake = Channel<Unit>(Channel.CONFLATED)

                launch {
                    core.evidence.collect { evidence ->
                        motionEngine.submit(evidence)
                        wake.trySend(Unit)
                    }
                }
                // The only periodic work in F/LAB. Everything else in this project is strictly
                // event-driven; a clock card is the one honest exception, and it is throttled hard.
                launch {
                    while (true) {
                        delay(CLOCK_TICK_MILLIS)
                        refreshClockCache()
                        wake.trySend(Unit)
                    }
                }

                for (unused in wake) {
                    motionEngine.updateTuning(core.effectiveMotionTuning)
                    while (motionEngine.needsFrames && visible) {
                        awaitFrame()
                        drawFrame(motionEngine.advance(System.nanoTime()))
                    }
                    // Settled, or the clock ticked with nothing folding: one still frame, so the
                    // card content (time, battery) stays current without a running animation.
                    drawFrame(motionEngine.channels)
                }
            }
        }

        private fun rebuildBackground() {
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return
            background?.recycle()
            frostedSource?.recycle()

            val bitmap = createBitmap(surfaceWidth, surfaceHeight)
            Canvas(bitmap).drawBrandGradient(surfaceWidth, surfaceHeight, isDarkTheme())
            background = bitmap

            // A small, heavily downscaled copy of the same art. Sampling it back up over a card's
            // bounds is a cheap, well-known approximation of a real backdrop blur — no RenderEffect,
            // no per-frame cost, and it works all the way back to this project's minSdk.
            val frostedWidth = (surfaceWidth / FROSTED_DOWNSCALE).coerceAtLeast(2)
            val frostedHeight = (surfaceHeight / FROSTED_DOWNSCALE).coerceAtLeast(2)
            frostedSource = bitmap.scale(frostedWidth, frostedHeight)
        }

        private fun drawFrame(channels: MotionChannels) {
            if (!visible) return
            val holder = surfaceHolder
            val bg = background ?: return
            val frosted = frostedSource ?: return
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return

            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                FoldWarpMesh.buildVertices(
                    surfaceWidth.toFloat(),
                    surfaceHeight.toFloat(),
                    channels.warpAmount,
                    warpVertices,
                )
                canvas.drawBitmapMesh(
                    bg,
                    FoldWarpMesh.COLUMNS,
                    FoldWarpMesh.ROWS - 1,
                    warpVertices,
                    0,
                    null,
                    0,
                    MESH_PAINT,
                )
                drawGlassCards(canvas, frosted)
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }
        }

        private fun drawGlassCards(canvas: Canvas, frosted: Bitmap) {
            val dark = isDarkTheme()
            // Every value here is either a cached field (battery, clock — see the fields' own
            // comment) or a plain in-memory read (fold posture, Build.MODEL), so drawing a card
            // costs nothing more than the earlier per-frame channels already cost.
            val cards = FoldWallpaperContent.cards(
                timeText = cachedTimeText,
                dateText = cachedDateText,
                batteryPercent = cachedBatteryPercent,
                isCharging = cachedCharging,
                foldPostureLabel = core.state.value.fold.posture.label,
                deviceLabel = Build.MODEL,
            )
            for (card in cards) {
                val pixels = card.rect.toPixels(surfaceWidth.toFloat(), surfaceHeight.toFloat())
                GlassCardPainter.draw(
                    canvas = canvas,
                    frostedSource = frosted,
                    rect = RectF(pixels.left, pixels.top, pixels.right, pixels.bottom),
                    title = card.title,
                    value = card.value,
                    caption = card.caption,
                    darkTheme = dark,
                )
            }
        }

        private fun isDarkTheme(): Boolean {
            val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return mode == Configuration.UI_MODE_NIGHT_YES
        }

        /**
         * Refreshes the clock cache. Called once when the wallpaper becomes visible and again on
         * every throttled clock tick — never from the frame path (see the fields' comment).
         *
         * Built fresh each time rather than holding a `SimpleDateFormat` in a companion object: a
         * formatter that captured `Locale.getDefault()` at class-init time would keep using
         * whatever locale was active when the wallpaper process started, even after the user
         * changes it, and this process can run for days.
         */
        private fun refreshClockCache() {
            val now = java.util.Date()
            cachedTimeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
            cachedDateText = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(now)
        }

        /**
         * Registers a live battery receiver for as long as the wallpaper is visible.
         *
         * A receiver rather than a per-draw sticky-intent query: this is what turns battery status
         * into something the frame path reads as a cached field instead of asking the system for on
         * every single frame of a transition. It also means a charge-state change is reflected the
         * moment it happens, not only on the next clock tick.
         */
        private fun registerBatteryReceiver() {
            if (batteryReceiver != null) return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = applyBatteryIntent(intent)
            }
            batteryReceiver = receiver
            val sticky = registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            sticky?.let(::applyBatteryIntent)
        }

        private fun unregisterBatteryReceiver() {
            batteryReceiver?.let { runCatching { unregisterReceiver(it) } }
            batteryReceiver = null
        }

        private fun applyBatteryIntent(status: Intent) {
            val level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            cachedBatteryPercent = if (level >= 0 && scale > 0) (level * 100f / scale).toInt() else 0
            cachedCharging = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        }
    }

    private companion object {
        const val CLOCK_TICK_MILLIS = 30_000L
        const val FROSTED_DOWNSCALE = 14

        val MESH_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    }
}

/**
 * Draws one frosted-glass card: a real blurred sample of the background beneath it, a translucent
 * tint, a hairline border, a soft drop shadow, and real device data as text.
 */
private object GlassCardPainter {

    private const val CORNER_RADIUS_PX = 42f
    private const val PADDING_PX = 36f

    fun draw(
        canvas: Canvas,
        frostedSource: Bitmap,
        rect: RectF,
        title: String,
        value: String,
        caption: String?,
        darkTheme: Boolean,
    ) {
        val path = Path().apply {
            addRoundRect(rect, CORNER_RADIUS_PX, CORNER_RADIUS_PX, Path.Direction.CW)
        }

        // Drop shadow first, beneath everything, on an unclipped canvas.
        canvas.drawPath(path, shadowPaint())

        canvas.withClip(path) {
            // The frosted backdrop: the same small, pre-blurred bitmap the whole wallpaper shares,
            // cropped to this card's position and scaled back up — a real sample of what is behind
            // the card, not a flat fill pretending to be one.
            val srcRect = android.graphics.Rect(
                (rect.left / width * frostedSource.width).toInt().coerceIn(0, frostedSource.width - 1),
                (rect.top / height * frostedSource.height).toInt().coerceIn(0, frostedSource.height - 1),
                (rect.right / width * frostedSource.width).toInt().coerceIn(1, frostedSource.width),
                (rect.bottom / height * frostedSource.height).toInt().coerceIn(1, frostedSource.height),
            )
            drawBitmap(frostedSource, srcRect, rect, BITMAP_PAINT)

            // The glass tint. Alpha kept moderate deliberately: too high and the blur underneath
            // stops reading at all, which is the difference between "frosted glass" and "opaque".
            drawRect(rect, tintPaint(darkTheme))
        }

        // Border, drawn after the clip is released so the stroke sits cleanly on the edge.
        canvas.drawPath(path, borderPaint(darkTheme))

        drawText(canvas, rect, title, value, caption, darkTheme)
    }

    private fun drawText(
        canvas: Canvas,
        rect: RectF,
        title: String,
        value: String,
        caption: String?,
        darkTheme: Boolean,
    ) {
        val primary = if (darkTheme) Color.WHITE else Color.BLACK
        val secondary = if (darkTheme) 0xB3FFFFFF.toInt() else 0xB3000000.toInt()

        val titlePaint = textPaint(secondary, 26f, bold = true, letterSpacing = 0.12f)
        val valuePaint = textPaint(primary, 64f, bold = true, letterSpacing = 0f)
        val captionPaint = textPaint(secondary, 26f, bold = false, letterSpacing = 0f)

        val left = rect.left + PADDING_PX
        var y = rect.top + PADDING_PX + titlePaint.textSize
        canvas.drawText(title.uppercase(Locale.getDefault()), left, y, titlePaint)

        y += valuePaint.textSize + 8f
        canvas.drawText(value, left, y, valuePaint)

        caption?.let {
            y += captionPaint.textSize + 4f
            if (y <= rect.bottom - PADDING_PX / 2f) {
                canvas.drawText(it, left, y, captionPaint)
            }
        }
    }

    private fun textPaint(color: Int, size: Float, bold: Boolean, letterSpacing: Float) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            this.letterSpacing = letterSpacing
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
            )
        }

    private fun tintPaint(darkTheme: Boolean) = Paint().apply {
        color = if (darkTheme) 0x33FFFFFF else 0x59FFFFFF
    }

    private fun borderPaint(darkTheme: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = if (darkTheme) 0x40FFFFFF else 0x66FFFFFF
    }

    private fun shadowPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        maskFilter = android.graphics.BlurMaskFilter(28f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }

    private val BITMAP_PAINT = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
}

/** F/LAB's own brand gradient, matching the app's dark/light schemes (DoD 40). */
private fun Canvas.drawBrandGradient(width: Int, height: Int, darkTheme: Boolean) {
    val colors = if (darkTheme) {
        intArrayOf(0xFF0E1420.toInt(), 0xFF1B2333.toInt(), 0xFF07090D.toInt())
    } else {
        intArrayOf(0xFFEAF6F8.toInt(), 0xFFF3EFFC.toInt(), 0xFFF7F8FA.toInt())
    }
    val shader = LinearGradient(
        0f,
        0f,
        width.toFloat(),
        height.toFloat(),
        colors,
        null,
        Shader.TileMode.CLAMP,
    )
    drawRect(0f, 0f, width.toFloat(), height.toFloat(), Paint().apply { this.shader = shader })
}
