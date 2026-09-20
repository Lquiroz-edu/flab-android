package com.lquiroz.flab.launcher

import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lquiroz.flab.FLabApplication
import com.lquiroz.flab.MainActivity
import com.lquiroz.flab.system.SystemAccess
import com.lquiroz.flab.ui.FLabViewModel
import com.lquiroz.flab.ui.motion.rememberFoldMotionChannels
import com.lquiroz.flab.ui.theme.FLabTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * F/LAB Home — the launcher.
 *
 * This is where the Duo effect actually lives. The wallpaper behind this window bends at the hinge
 * (Fold Wallpaper), and every icon on it moves through the same displacement field, so the whole
 * home screen folds as one surface and settles as the device opens. Apps open by growing out of
 * their own icon. None of that is possible over One UI Home from the outside, which is why F/LAB
 * offers its own — and only if the user chooses it as the default home; nothing here is forced.
 *
 * Same lifecycle discipline as [MainActivity]: `configChanges` keeps the window alive across the
 * fold so the reflow can animate instead of the Activity restarting, the Core is attached only
 * while STARTED, and Back never finishes a home screen.
 */
class HomeLauncherActivity : ComponentActivity() {

    private val launcher: LauncherViewModel by viewModels()
    private val flab: FLabViewModel by viewModels()

    private val core get() = (application as FLabApplication).core

    private val isDefaultHome = MutableStateFlow(false)
    private val isFoldWallpaperActive = MutableStateFlow(false)

    private val roleRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // A home screen has nothing behind it to go back to.
        onBackPressedDispatcher.addCallback(this) { }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                core.attach(this@HomeLauncherActivity)
                core.onForegroundPackage(packageName)
                // The hand-off has to begin at the first degree, and on the cover nothing else
                // announces that the device has started to open: no posture event fires until the
                // inner panel wakes. So while the home screen is on screen it holds the hinge
                // listener open, exactly as the wallpaper does while it is visible, and lets go the
                // moment it is not.
                core.requestContinuousTracking(this@HomeLauncherActivity)
                try {
                    awaitCancellation()
                } finally {
                    core.releaseContinuousTracking(this@HomeLauncherActivity)
                    core.detach(this@HomeLauncherActivity)
                }
            }
        }

        setContent {
            FLabTheme {
                val ui by flab.uiState.collectAsStateWithLifecycle()
                val catalogue by launcher.catalogue.collectAsStateWithLifecycle()
                val homePresses by launcher.homePresses.collectAsStateWithLifecycle()
                val defaultHome by isDefaultHome.collectAsStateWithLifecycle()
                val wallpaperActive by isFoldWallpaperActive.collectAsStateWithLifecycle()

                val channels = rememberFoldMotionChannels(
                    evidence = flab.evidence,
                    tuning = ui.effectiveMotion,
                    enabled = ui.configuration.enabled,
                    onSettled = flab::onMotionSettled,
                )
                FLabHomeScreen(
                    channels = channels,
                    catalogue = catalogue,
                    homePresses = homePresses,
                    isDefaultHome = defaultHome,
                    isFoldWallpaperActive = wallpaperActive,
                    onRequestDefaultHome = ::requestDefaultHome,
                    onSetWallpaper = { open(flab.liveWallpaperIntent()) },
                    onOpenFLab = { open(Intent(this, MainActivity::class.java)) },
                    onLaunch = ::launch,
                    onAppDetails = launcher::openAppDetails,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launcher.onHomePressed()
    }

    override fun onResume() {
        super.onResume()
        core.refreshPowerPosture()
        refreshStatus()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        core.onWindowMetrics(this)
        core.refreshPowerPosture()
    }

    private fun refreshStatus() {
        isDefaultHome.value = SystemAccess.isDefaultHome(this)
        isFoldWallpaperActive.value = SystemAccess.isFoldWallpaperActive(this)
    }

    private fun requestDefaultHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                runCatching { roleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
                    .onSuccess { return }
            }
        }
        open(SystemAccess.homeSettingsIntent())
    }

    /**
     * Opens an app by growing it out of its own icon — the platform's own clip-reveal, which is
     * what the Duo's launcher does too. No custom window animation, no overlay: Android is simply
     * told where the icon was.
     */
    private fun launch(entry: LauncherEntry, iconBounds: Rect) {
        val source = android.graphics.Rect(
            iconBounds.left.roundToInt(),
            iconBounds.top.roundToInt(),
            iconBounds.right.roundToInt(),
            iconBounds.bottom.roundToInt(),
        )
        val options = ActivityOptions.makeClipRevealAnimation(
            window.decorView,
            source.left,
            source.top,
            source.width(),
            source.height(),
        )
        launcher.launch(entry, source, options.toBundle())
    }

    private fun open(intent: Intent) {
        runCatching { startActivity(intent) }
    }
}
