package com.lquiroz.flab

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lquiroz.flab.settings.FLabSettings

/**
 * Boot persistence (DoD 19).
 *
 * After a restart, F/LAB has to come back the way the user left it without being reopened. There
 * is nothing to start here — the stored configuration is the state, and the Core reads it when the
 * process next runs — so this receiver deliberately does almost nothing. Touching the settings is
 * enough to confirm the store survived the reboot, and any failure is swallowed rather than
 * crashing at boot, where a crash would be both useless and very visible.
 *
 * What this does *not* do is start a foreground service at boot to keep F/LAB "alive". F/LAB's
 * modules run inside its own process when it has work to do; a permanent service would cost
 * battery all day for no user-visible gain, which DoD 25 rules out.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        runCatching { FLabSettings(context).current() }
    }
}
