package com.lquiroz.flab.studio

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.hardware.*
import android.view.MotionEvent
import android.view.View
import com.lquiroz.flab.motion.FoldTrigger

class PageView(context: Context) : View(context), SensorEventListener {
    val renderer = PageRenderer(context)
    var progress = 1f
        private set
    var onProgress: ((Float) -> Unit)? = null
    private var animation: ValueAnimator? = null
    private val sensors = context.getSystemService(SensorManager::class.java)
    private var trigger = FoldTrigger()
    private var listening = false
    var enabled = true
        set(value) { field=value; if(value && windowVisibility==VISIBLE) startSensors() else stopSensors() }

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
    override fun onDraw(canvas: Canvas) { renderer.draw(canvas,width,height,progress,animation?.isRunning==true) }
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
    private fun startSensors() {
        if(listening || !enabled) return
        trigger=FoldTrigger()
        val sensor=sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE,false)
            ?: sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE,true)
        listening=sensor!=null && sensors.registerListener(this,sensor,20_000)
    }
    private fun stopSensors() { sensors.unregisterListener(this); listening=false; animation?.cancel(); animation=null }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if(visibility==VISIBLE) startSensors() else stopSensors()
    }
    override fun onDetachedFromWindow() { stopSensors(); super.onDetachedFromWindow() }
    override fun onSensorChanged(event: SensorEvent) {
        val angle=event.values.firstOrNull() ?: return
        if(trigger.accept(angle,android.os.SystemClock.elapsedRealtime())) play(if(angle>=175) 1f else 0f)
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
