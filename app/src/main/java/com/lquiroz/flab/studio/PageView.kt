package com.lquiroz.flab.studio

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import com.lquiroz.flab.core.FLabCore
import com.lquiroz.flab.core.FLabState

class PageView(context: Context) : View(context), FLabCore.Listener {
    val renderer = PageRenderer(context)
    var progress = 1f
        private set
    var onProgress: ((Float) -> Unit)? = null
    private var animation: ValueAnimator? = null
    private var physicalMotion = false
    private val settle = Runnable { physicalMotion = false; invalidate() }
    var respondToFold = true
        set(value) { field=value }

    init { renderer.reload(); contentDescription="Vista previa de página. Desliza horizontalmente o usa el control de apertura." }
    fun setProgress(value: Float) {
        animation?.cancel(); animation=null
        progress=PageGeometry.progress(value); onProgress?.invoke(progress); invalidate()
    }
    fun play(target: Float = if(progress > .5f) 0f else 1f) {
        animation?.cancel()
        if(!ValueAnimator.areAnimatorsEnabled()) { setProgress(target); return }
        animation=ValueAnimator.ofFloat(progress,target).apply {
            duration=1100
            interpolator=android.view.animation.AccelerateDecelerateInterpolator()
            addUpdateListener { progress=it.animatedValue as Float; onProgress?.invoke(progress); invalidate() }
            start()
        }
    }
    override fun onDraw(canvas: Canvas) { renderer.draw(canvas,width,height,progress,animation?.isRunning==true || physicalMotion) }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent?.requestDisallowInterceptTouchEvent(true); setProgress(1-event.x/width) }
            MotionEvent.ACTION_MOVE -> setProgress(1-event.x/width)
            MotionEvent.ACTION_UP -> { parent?.requestDisallowInterceptTouchEvent(false); performClick() }
            MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        FLabCore.addListener(this)
    }
    override fun onDetachedFromWindow() {
        FLabCore.removeListener(this)
        removeCallbacks(settle)
        animation?.cancel(); animation=null
        super.onDetachedFromWindow()
    }
    override fun onState(state: FLabState) {
        if (!respondToFold || !state.enabled || animation?.isRunning == true) return
        renderer.velocityDegPerSecond = state.hingeVelocityDegPerSecond
        if (kotlin.math.abs(progress - state.foldProgress) < .001f) return
        progress = state.foldProgress
        physicalMotion = true
        onProgress?.invoke(progress)
        removeCallbacks(settle)
        postDelayed(settle, 90)
        invalidate()
    }
}
