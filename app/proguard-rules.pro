# Shizuku constructs helpers and instrumentation by class name in another process.
-keep class org.duofold.live.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class org.lsposed.hiddenapibypass.** { *; }
# These are compile-only descriptions of Samsung/Android hidden framework APIs.
-dontwarn android.window.DisplayAreaOrganizer
-dontwarn android.window.DisplayAreaAppearedInfo
-dontwarn android.window.DisplayAreaInfo
-dontwarn android.window.WindowContainerToken
