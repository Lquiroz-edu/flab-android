package com.lquiroz.flab.system

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.lquiroz.flab.motion.MotionChannels
import java.util.function.Consumer
import kotlin.math.roundToInt

/**
 * The system-wide fold effect: one click-through window that blurs and dims what is behind it.
 *
 * ### How it reaches outside F/LAB
 *
 * `TYPE_APPLICATION_OVERLAY` puts the window above other apps, and `FLAG_BLUR_BEHIND` with
 * [WindowManager.LayoutParams.setBlurBehindRadius] blurs whatever the compositor has underneath —
 * the actual app, not a screenshot of it. That is the one genuinely system-wide visual effect a
 * sandboxed app can produce, and it is available from Android 12.
 *
 * What it still cannot do, and no flag will change: move, scale or restyle the app underneath, or
 * read a single pixel of it.
 *
 * ### Input, once and for all
 *
 * `FLAG_NOT_TOUCHABLE` and `FLAG_NOT_FOCUSABLE` are set in [baseFlags] and are never removed or
 * made conditional. Every touch, swipe, back gesture and keystroke passes straight through to the
 * app underneath (DoD 31). This window has no content view with any listener, and it is not a
 * surface anyone can interact with — which is the difference between a visual effect and a
 * tapjacking vector.
 *
 * ### When blur is unavailable
 *
 * Cross-window blur can be off: GPU limitations, battery saver, multimedia tunneling. Android's own
 * guidance for that case is to raise the dim amount instead, which is what [channelsToParams] does.
 * The effect degrades to a softer dim rather than disappearing or, worse, leaving a flat translucent
 * sheet that reads as a fault.
 *
 * ### Lifetime
 *
 * The window is added when there is something to show and **removed** when there is not — not
 * hidden, removed, so the surface is released (DoD 22, DoD 30). [isShowing] is the single source of
 * truth about whether it currently exists.
 */
class FoldOverlayWindow(private val context: Context) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null

    /** Tracks the platform's own answer about whether blur will render at all. */
    private var crossWindowBlurEnabled: Boolean = initialBlurEnabled()
    private var blurListener: Consumer<Boolean>? = null

    val isShowing: Boolean get() = view != null

    val isBlurSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && crossWindowBlurEnabled

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) registerBlurListener()
    }

    /** True when the user has granted "display over other apps". */
    fun hasPermission(): Boolean = Settings.canDrawOverlays(context)

    /**
     * Shows or updates the overlay for [channels].
     *
     * Safe to call every frame: the window is added once and then only its `LayoutParams` are
     * updated, which is cheap and avoids the surface churn of add/remove cycles.
     */
    fun apply(channels: MotionChannels) {
        if (channels.isNeutral) {
            hide()
            return
        }
        val layoutParams = params ?: buildParams().also { params = it }
        channelsToParams(channels, layoutParams)

        val current = view
        if (current == null) {
            // A bare View with no content: this window exists to carry window flags, not to draw.
            // Nothing is ever rendered into it, so there is nothing that could obscure content.
            val fresh = View(context)
            runCatching { windowManager.addView(fresh, layoutParams) }
                .onSuccess { view = fresh }
        } else {
            runCatching { windowManager.updateViewLayout(current, layoutParams) }
        }
    }

    /** Removes the overlay and releases its surface. Idempotent. */
    fun hide() {
        val current = view ?: return
        view = null
        runCatching { windowManager.removeViewImmediate(current) }
    }

    /** Removes the overlay and stops listening for blur availability. */
    fun release() {
        hide()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurListener?.let { windowManager.removeCrossWindowBlurEnabledListener(it) }
            blurListener = null
        }
        params = null
    }

    private fun buildParams() = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        gravity = Gravity.TOP or Gravity.START
        width = WindowManager.LayoutParams.MATCH_PARENT
        height = WindowManager.LayoutParams.MATCH_PARENT
        flags = baseFlags
        // Transparent: the effect lives entirely in the blur and dim the window asks the
        // compositor for. The window itself paints nothing.
        alpha = 1f
        // Two separate API floors here, and both matter: the field itself arrived in 28, and
        // ALWAYS only in 30. On 28–29 SHORT_EDGES is the closest equivalent. Either way the blur
        // runs under the cutout rather than stopping in a band beside it, which would be exactly
        // the artificial edge DoD 30 bans.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun channelsToParams(
        channels: MotionChannels,
        target: WindowManager.LayoutParams,
    ) {
        val blurring = isBlurSupported && channels.blurRadiusDp > 0.25f
        if (blurring) {
            target.flags = baseFlags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            setBlurRadius(target, dpToPx(channels.blurRadiusDp))
        } else {
            target.flags = baseFlags
            setBlurRadius(target, 0)
        }

        // Android's own guidance when blur is unavailable: carry the effect with a stronger dim
        // rather than dropping it. The ceiling still holds, so this can never become a black flash.
        val dim = if (blurring) {
            channels.dimAlpha
        } else {
            (channels.dimAlpha * NO_BLUR_DIM_BOOST).coerceAtMost(MotionChannels.MAX_DIM_ALPHA)
        }
        if (dim > 0f) {
            target.flags = target.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            target.dimAmount = dim
        } else {
            target.flags = target.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()
            target.dimAmount = 0f
        }
    }

    private fun setBlurRadius(target: WindowManager.LayoutParams, radiusPx: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            target.blurBehindRadius = radiusPx
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerBlurListener() {
        val listener = Consumer<Boolean> { enabled ->
            crossWindowBlurEnabled = enabled
            // Recompute on the next frame rather than mutating the live window from a system
            // callback; the frame loop owns the window's parameters.
        }
        blurListener = listener
        windowManager.addCrossWindowBlurEnabledListener(listener)
    }

    private fun initialBlurEnabled(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager.isCrossWindowBlurEnabled

    private fun dpToPx(dp: Float): Int =
        (dp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(0)

    companion object {
        /**
         * Whether cross-window blur would render, without constructing a window.
         *
         * Diagnostics needs this answer and must not pay for a listener registration to get it: an
         * instance built just to read a flag leaks its blur listener every time the screen
         * recomposes.
         */
        fun isBlurAvailable(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
            val manager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            return manager?.isCrossWindowBlurEnabled == true
        }

        /**
         * Never conditional, never removed.
         *
         * NOT_TOUCHABLE and NOT_FOCUSABLE make the window invisible to input; LAYOUT_NO_LIMITS and
         * LAYOUT_IN_SCREEN let it cover the system bar areas so the blur is continuous rather than
         * stopping in a band at the top, which would be exactly the artificial edge DoD 30 bans.
         */
        val baseFlags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

        const val NO_BLUR_DIM_BOOST = 1.6f
    }
}
