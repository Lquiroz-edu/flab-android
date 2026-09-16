package com.lquiroz.flab.studio

import android.animation.ValueAnimator
import android.content.SharedPreferences
import android.hardware.*
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.lquiroz.flab.motion.FoldTrigger

class FoldWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = PaperEngine()
    inner class PaperEngine : Engine(), SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {
        private val prefs=getSharedPreferences("studio",MODE_PRIVATE)
        private val sensors=getSystemService(SensorManager::class.java)
        private val renderer=PageRenderer(this@FoldWallpaper)
        private var trigger=FoldTrigger()
        private var animation: ValueAnimator?=null
        private var progress=1f
        private var surfaceReady=false
        private var w=0
        private var h=0
        private var listening=false
        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(false)
            renderer.reload()
            prefs.registerOnSharedPreferenceChangeListener(this)
            settings()
        }
        private fun settings() {
            renderer.blur=prefs.getFloat("blur",.45f)
            renderer.radius=prefs.getFloat("radius",.045f)
            renderer.dark=prefs.getBoolean("dark",false)
        }
        override fun onVisibilityChanged(visible: Boolean) {
            if(visible) { listen(); draw() } else stop()
        }
        private fun listen() {
            if(listening || !prefs.getBoolean("enabled",true)) return
            trigger=FoldTrigger()
            val sensor=sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE,false)
                ?: sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE,true)
            listening=sensor!=null && sensors.registerListener(this,sensor,20_000)
        }
        private fun stop() {
            sensors.unregisterListener(this); listening=false
            animation?.cancel(); animation=null
        }
        override fun onSurfaceCreated(holder: SurfaceHolder) { super.onSurfaceCreated(holder); surfaceReady=true }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder,format,width,height)
            val changed=w>0 && (w!=width || h!=height)
            w=width; h=height
            if(changed && isVisible && prefs.getBoolean("enabled",true)) {
                progress=0f; play(1f)
            } else draw()
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady=false; stop(); super.onSurfaceDestroyed(holder)
        }
        private fun play(target: Float) {
            animation?.cancel()
            if(!ValueAnimator.areAnimatorsEnabled()) { progress=target; draw(); return }
            animation=ValueAnimator.ofFloat(progress,target).apply {
                duration=900
                addUpdateListener { progress=it.animatedValue as Float; draw() }
                start()
            }
        }
        private fun draw() {
            if(!isVisible || !surfaceReady || w<2 || h<2) return
            val canvas=try { surfaceHolder.lockHardwareCanvas() } catch (_: RuntimeException) { null } ?: return
            try { renderer.draw(canvas,w,h,progress,animation?.isRunning==true,true) }
            finally { surfaceHolder.unlockCanvasAndPost(canvas) }
        }
        override fun onSensorChanged(event: SensorEvent) {
            val angle=event.values.firstOrNull() ?: return
            if(trigger.accept(angle,SystemClock.elapsedRealtime())) play(if(angle>=175) 1f else 0f)
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            settings()
            if(key=="image") renderer.reload()
            if(key=="enabled") {
                stop()
                if(isVisible && prefs.getBoolean("enabled",true)) listen()
                else progress=1f
            }
            draw()
        }
        override fun onDestroy() { stop(); prefs.unregisterOnSharedPreferenceChangeListener(this); super.onDestroy() }
    }
}
