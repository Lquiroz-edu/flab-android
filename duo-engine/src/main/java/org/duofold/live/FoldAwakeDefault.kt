package org.duofold.live
import android.content.Context
import android.os.SystemClock
import rikka.shizuku.Shizuku
/** Enabled-session policy. Save the previous Samsung value and restore on stop. */
object FoldAwakeDefault {
 private var pending=false
 private var nextAttempt=0L
 @JvmField var status="Keep-awake only while global effect is enabled"
 @JvmStatic fun persist(context:Context){
  val p=context.getSharedPreferences("standalone",0)
  if(!p.contains("auto_keep_cover_awake"))p.edit().putBoolean("auto_keep_cover_awake",true).apply()
 }
 @JvmStatic fun reconnect(){nextAttempt=0L}
 @JvmStatic fun restore(context:Context){
  val p=context.getSharedPreferences("standalone",0)
  if(pending || !p.contains("flab_original_fold_policy"))return
  pending=true
  FoldSettingsClient.request(context,"restore",p.getString("flab_original_fold_policy","null")){result->
   pending=false
   if(result.getBoolean("ok")){p.edit().remove("flab_original_fold_policy").apply();status="Previous fold policy restored"}
   else status="Restoration pending: ${result.getString("error")}; reconnect Shizuku"
  }
 }
 @JvmStatic fun tick(context:Context){
  val p=context.getSharedPreferences("standalone",0)
  if(!p.getBoolean("enabled",false)||!p.getBoolean("auto_keep_cover_awake",true)){restore(context);return}
  if(!context.getSystemService(android.os.PowerManager::class.java).isInteractive||pending||SystemClock.elapsedRealtime()<nextAttempt)return
  if(!runCatching{Shizuku.pingBinder()&&Shizuku.checkSelfPermission()==0}.getOrDefault(false))return
  pending=true
  FoldSettingsClient.request(context,"always",null){result->
   pending=false
   nextAttempt=SystemClock.elapsedRealtime()+if(result.getBoolean("ok"))300000 else 60000
   if(result.getBoolean("ok")){
    if(!p.contains("flab_original_fold_policy"))p.edit().putString("flab_original_fold_policy",result.getString("previous","null")).commit()
    status="Keep-awake verified for enabled session"
    if(!p.getBoolean("enabled",false)||!p.getBoolean("auto_keep_cover_awake",true))restore(context)
   }else status="Keep-awake retry: ${result.getString("error")}" 
  }
 }
}
