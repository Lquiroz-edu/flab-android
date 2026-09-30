package org.duofold.live
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
/** Public entry point for F/LAB; the imported engine owns a single global compositor. */
object GlobalDuo {
 private var listener:SharedPreferences.OnSharedPreferenceChangeListener?=null
 fun initialize(context:Context){
  val c=context.applicationContext
  val p=c.getSharedPreferences("standalone",0)
  if(!p.getBoolean("flab_global_initialized",false)){
   // Seed onboarding BEFORE defaults so settings never masquerade as a legacy install.
   c.getSharedPreferences("first_run",0).edit().putInt("state",1).putInt("step",1).putBoolean("wallpaper_verified",false).commit()
   p.edit().putBoolean("flab_global_initialized",true).putBoolean("enabled",false)
    .putInt("content_fps",60).putBoolean("full_resolution",false).putInt("antialias_method_v2",0)
    .putString("animation_mode",AnimationModePolicy.DEFAULT).putString("animation_style","duo")
    .putBoolean("cover_preview",true).putBoolean("dual",false).putFloat("end_stretch",1.25f).putFloat("intensity",1f).commit()
  }
  listener=SharedPreferences.OnSharedPreferenceChangeListener{prefs,key->
   if(key=="enabled"&&prefs.getBoolean("enabled",false)){
    c.getSharedPreferences("flab_settings",0).edit().putBoolean("system_effects_enabled",false).apply()
    runCatching{c.stopService(Intent().setClassName(c,"com.lquiroz.flab.system.FLabOverlayService"))}
   }
   if(key=="enabled"&&!prefs.getBoolean("enabled",false))FoldAwakeDefault.restore(c)
  }
  p.registerOnSharedPreferenceChangeListener(listener)
  if(p.getBoolean("enabled",false))c.getSharedPreferences("flab_settings",0).edit().putBoolean("system_effects_enabled",false).apply()
  if(!p.getBoolean("enabled",false))FoldAwakeDefault.restore(c)
  org.duofold.live.wallpaperlayer.WallpaperRestore.resume(c)
 }
 fun open(context:Context){context.startActivity(Intent(context,MainActivity::class.java))}
 fun stop(context:Context){
  context.getSharedPreferences("standalone",0).edit().putBoolean("enabled",false).apply()
  StandaloneService.instance?.restart()
  context.stopService(Intent(context,FoldBackgroundService::class.java))
  FoldAwakeDefault.restore(context)
 }
}
