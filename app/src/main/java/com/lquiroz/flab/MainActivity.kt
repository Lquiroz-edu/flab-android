package com.lquiroz.flab

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.lquiroz.flab.ui.FLabApp
import com.lquiroz.flab.ui.FLabViewModel
import com.lquiroz.flab.ui.theme.FLabTheme
import kotlinx.coroutines.launch

/**
 * F/LAB's only Activity.
 *
 * `configChanges` covers the fold-related changes so the window is resized in place rather than
 * recreated. That is not a micro-optimisation: an Activity recreation mid-unfold is exactly the
 * "pantalla negra -> resize -> app" sequence DoD 4 is trying to remove, and F/LAB cannot credibly
 * ask other apps to avoid it while doing it itself.
 *
 * The Core is attached for the `STARTED` lifecycle only. Backgrounded F/LAB holds no window
 * subscription and no sensor listener (DoD 22).
 */
class MainActivity : ComponentActivity() {

    private val viewModel: FLabViewModel by viewModels()

    private val core get() = (application as FLabApplication).core

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                core.attach(this@MainActivity)
                core.onForegroundPackage(packageName)
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    core.detach()
                }
            }
        }

        setContent {
            FLabTheme {
                FLabApp(viewModel = viewModel, onShare = ::shareReport)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Window metrics change without a new WindowLayoutInfo when only the size moved, so the
        // Core is told directly rather than waiting for the next posture event.
        core.onWindowMetrics(this)
        core.refreshPowerPosture()
    }

    override fun onResume() {
        super.onResume()
        core.refreshPowerPosture()
    }

    /**
     * Shares a debug report (DoD 38).
     *
     * Sent as plain text through the system share sheet, so the user picks the destination and
     * F/LAB never uploads anything by itself.
     */
    private fun shareReport(report: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.debug_report_subject))
            putExtra(Intent.EXTRA_TEXT, report)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.debug_report_share)))
    }
}
