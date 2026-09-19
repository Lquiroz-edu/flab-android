package com.lquiroz.flab.system

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Reports which app is in front. Nothing else.
 *
 * ### What this reads, precisely
 *
 * One string per window change: [AccessibilityEvent.getPackageName]. That is the entire data flow.
 *
 * It does not call `getRootInActiveWindow`, does not walk the node tree, does not read text, does
 * not observe typing, and does not persist anything. The service config in
 * `res/xml/flab_accessibility.xml` requests neither `canRetrieveWindowContent` nor
 * `flagRequestFilterKeyEvents`, so the platform does not even hand it screen content to ignore.
 * That restraint is not politeness — an accessibility service is the most powerful thing a user can
 * grant an app, and F/LAB should be auditable in one screenful.
 *
 * ### Why it is needed at all
 *
 * The system-wide overlay has to know whether the app underneath is a bank before it draws over it.
 * Android gives a normal app no other way to know what is in front. So the safety gate in
 * [SystemEffectPolicy] and this service are the same feature: without the package name, the policy
 * resolves to [OverlayVerdict.UnknownApp] and F/LAB does nothing at all.
 *
 * ### If the user says no
 *
 * F/LAB still runs. Fold Motion inside F/LAB, Continuity, Live Preview, Profiles and Diagnostics
 * are unaffected. Only the system-wide effect stands down, and F/LAB Access says so (DoD 16, 17).
 */
class FLabAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        connected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString()?.takeIf { it.isNotBlank() } ?: return
        // A StateFlow, so a repeated package costs nothing and no history is kept.
        foregroundPackage.value = packageName
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        connected.value = false
        foregroundPackage.value = null
        super.onDestroy()
    }

    companion object {
        private val foregroundPackage = MutableStateFlow<String?>(null)
        private val connected = MutableStateFlow(false)

        /** The package in front, or null when unknown. Null always means "do not act". */
        val currentForegroundPackage: StateFlow<String?> = foregroundPackage.asStateFlow()

        /** Whether the user has this service enabled right now. */
        val isConnected: StateFlow<Boolean> = connected.asStateFlow()
    }
}
