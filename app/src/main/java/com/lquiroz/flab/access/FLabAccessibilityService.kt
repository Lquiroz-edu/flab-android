package com.lquiroz.flab.access

import android.accessibilityservice.AccessibilityService
import android.content.*
import android.view.accessibility.AccessibilityEvent
import com.lquiroz.flab.core.FLabCore
import com.lquiroz.flab.profiles.AppStrategy
import com.lquiroz.flab.profiles.ProfileCatalog

class FLabAccessibilityService : AccessibilityService() {
    private lateinit var overlay: ImmersiveOverlay
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { overlay.hide() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        FLabCore.start(applicationContext)
        FLabCore.acquire("App awareness", needsHinge = false)
        FLabCore.setAccessibilityConnected(true)
        overlay = ImmersiveOverlay(this)
        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), RECEIVER_NOT_EXPORTED)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        val packageName = event.packageName?.toString()
        FLabCore.reportForeground(packageName)
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

    override fun onInterrupt() { overlay.hide() }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        if (::overlay.isInitialized) overlay.hide()
        FLabCore.setAccessibilityConnected(false)
        FLabCore.release("App awareness")
        super.onDestroy()
    }
}
