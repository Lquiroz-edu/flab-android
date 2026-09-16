package com.lquiroz.flab.studio

import android.content.SharedPreferences
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.lquiroz.flab.core.FLabCore
import com.lquiroz.flab.core.FLabState

class FoldWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = PaperEngine()
    inner class PaperEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener, FLabCore.Listener {
        private val prefs=getSharedPreferences("studio",MODE_PRIVATE)
        private val renderer=PageRenderer(this@FoldWallpaper)
        private var progress=1f
        private var moving=false
        private var surfaceReady=false
        private var w=0
        private var h=0
        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            setTouchEventsEnabled(false)
            renderer.reload()
            prefs.registerOnSharedPreferenceChangeListener(this)
            settings()
        }
        private fun settings() {
            renderer.blur=prefs.getFloat("blur",.58f)
            renderer.radius=prefs.getFloat("radius",.045f)
            renderer.dark=prefs.getBoolean("dark",false)
        }
        override fun onVisibilityChanged(visible: Boolean) {
            if(visible) {
                FLabCore.acquire("Fold Motion")
                FLabCore.addListener(this)
                draw()
            } else {
                FLabCore.removeListener(this)
                FLabCore.release("Fold Motion")
            }
        }
        override fun onSurfaceCreated(holder: SurfaceHolder) { super.onSurfaceCreated(holder); surfaceReady=true }
        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder,format,width,height)
            w=width; h=height
            FLabCore.reportWindow(width,height)
            draw()
        }
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            surfaceReady=false
            FLabCore.removeListener(this)
            FLabCore.release("Fold Motion")
            super.onSurfaceDestroyed(holder)
        }
        private fun draw() {
            if(!isVisible || !surfaceReady || w<2 || h<2) return
            val canvas=try { surfaceHolder.lockHardwareCanvas() } catch (_: RuntimeException) { null } ?: return
            try { renderer.draw(canvas,w,h,progress,moving,true) }
            finally { surfaceHolder.unlockCanvasAndPost(canvas) }
        }
        override fun onState(state: FLabState) {
            if (!state.enabled || !prefs.getBoolean("enabled",true)) return
            renderer.velocityDegPerSecond=state.hingeVelocityDegPerSecond
            moving=kotlin.math.abs(progress-state.foldProgress)>.001f
            progress=state.foldProgress
            draw()
            moving=false
        }
        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
            settings()
            if(key=="image") renderer.reload()
            if(key=="enabled") {
                if(!prefs.getBoolean("enabled",true)) progress=1f
            }
            draw()
        }
        override fun onDestroy() {
            FLabCore.removeListener(this)
            FLabCore.release("Fold Motion")
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            super.onDestroy()
        }
    }
}
