package com.lquiroz.flab.access

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.view.accessibility.AccessibilityEvent
import com.lquiroz.flab.core.FLabCore
import com.lquiroz.flab.profiles.AppStrategy
import com.lquiroz.flab.profiles.ProfileCatalog

class FLabAccessibilityService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var overlay: ImmersiveOverlay
    private lateinit var systemMotion: SystemMotionOverlay
    private lateinit var prefs: SharedPreferences
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { overlay.hide() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        FLabCore.start(applicationContext)
        prefs = getSharedPreferences("flab", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        FLabCore.acquire("App awareness", needsHinge = prefs.getBoolean("system_motion_experiment", false))
        FLabCore.setAccessibilityConnected(true)
        overlay = ImmersiveOverlay(this)
        systemMotion = SystemMotionOverlay(this).also { it.start() }
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val packageName = event.packageName?.toString()
        FLabCore.reportForeground(packageName)
        if (::systemMotion.isInitialized) systemMotion.onWindowChanged(packageName)
        applyRule(packageName)
    }

    private fun applyRule(packageName: String?) {
        val rule = ProfileCatalog.find(packageName)
        val global = getSharedPreferences("flab", MODE_PRIVATE).getBoolean("global_enabled", true)
        val experiment = getSharedPreferences("flab", MODE_PRIVATE).getBoolean("immersive_experiment", false)
        val allowed = global && experiment && rule?.strategy == AppStrategy.IMMERSIVE_OPT_IN &&
            !rule.safe && ProfileCatalog.immersiveEnabled(this, rule)
        if (allowed) runCatching { overlay.show() }
            .onFailure { FLabCore.recordModuleError("Immersive", it); overlay.hide() }
        else overlay.hide()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "system_motion_experiment") {
            FLabCore.acquire("App awareness", needsHinge = prefs.getBoolean(key, false))
            if (!prefs.getBoolean(key, false) && ::systemMotion.isInitialized) systemMotion.hide()
        }
    }

    override fun onInterrupt() {
        if (::overlay.isInitialized) overlay.hide()
        if (::systemMotion.isInitialized) systemMotion.hide()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        if (::overlay.isInitialized) overlay.hide()
        if (::systemMotion.isInitialized) systemMotion.stop()
        if (::prefs.isInitialized) prefs.unregisterOnSharedPreferenceChangeListener(this)
        FLabCore.setAccessibilityConnected(false)
        FLabCore.release("App awareness")
        super.onDestroy()
    }
}
