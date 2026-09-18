package com.lquiroz.flab

import android.app.Application
import com.lquiroz.flab.core.FLabCore
import com.lquiroz.flab.settings.FLabSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Process-level owner of the Core.
 *
 * The Core lives here rather than in an Activity or a ViewModel because DoD 2 requires the state to
 * survive a full `closed -> open -> closed` cycle, and on a foldable that cycle destroys and
 * recreates Activities. Anything holding fold state below the Application would lose it exactly
 * when it mattered most.
 *
 * A `SupervisorJob` means a module's coroutine failing cancels that module and nothing else, which
 * is the coroutine half of the crash containment in DoD 20.
 */
class FLabApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob())

    lateinit var settings: FLabSettings
        private set

    lateinit var core: FLabCore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = FLabSettings(this)
        core = FLabCore(applicationContext, settings, scope)
    }

    override fun onTerminate() {
        // Only called on emulators, but leaving a scope dangling in a Application subclass is the
        // kind of thing that turns into a leak in a test harness.
        scope.cancel()
        super.onTerminate()
    }
}
