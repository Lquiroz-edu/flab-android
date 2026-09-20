package com.lquiroz.flab.launcher

import android.app.Presentation
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Puts F/LAB Home on the *other* panel while the device is between closed and open.
 *
 * ### The idea, and where it comes from
 *
 * A Galaxy Fold's cover and inner displays are physically separate, and One UI switches between
 * them part-way through the opening. The iPhone Duo shows both surfaces at once through that
 * moment. A third-party proof of concept on the Z Fold 8 (moomanjohnny, r/GalaxyFold, 2026) showed
 * the missing piece: with the hinge angle as input, Android's `Presentation` API can render onto
 * the cover panel as a second display, with no root and no special permission. That demo
 * cross-faded two screenshots; this bridge renders the live home screen instead, driven by the same
 * channels as the main window, so both panels show one motion.
 *
 * ### What is honest about it
 *
 * Nothing here can turn a panel on, and nothing here is documented by Samsung. If the platform
 * exposes a second display, the bridge presents on it and says so; if it does not, or refuses the
 * presentation, the bridge says that instead — in Diagnostics, next to the raw list of displays the
 * device reports, so the outcome on a real Fold is visible rather than guessed. `FLAG_KEEP_SCREEN_ON`
 * is set on the presentation for the few hundred milliseconds it exists, which is the most an app
 * may ask of a display's power state.
 *
 * ### Lifetime
 *
 * Shown only while [CoverBridgePolicy.shouldMirror] holds — the first half of the opening — and
 * dismissed the moment it does not, so an open device never keeps its rear-facing cover lit. The
 * display listener re-evaluates when displays appear or change state, which on a Fold is exactly
 * the moment the switch happens.
 */
class CoverDisplayBridge(
    private val activity: ComponentActivity,
    private val content: @Composable () -> Unit,
) {
    private val displayManager = activity.getSystemService(DisplayManager::class.java)
    private var presentation: Presentation? = null
    private var wanted = false

    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = reconcile()
        override fun onDisplayRemoved(displayId: Int) = reconcile()
        override fun onDisplayChanged(displayId: Int) = reconcile()
    }

    fun start() {
        displayManager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        report("Idle")
    }

    fun setWanted(wanted: Boolean) {
        if (this.wanted == wanted) return
        this.wanted = wanted
        reconcile()
    }

    fun release() {
        displayManager?.unregisterDisplayListener(listener)
        dismiss()
        report("Released")
    }

    private fun reconcile() {
        val target = if (wanted) otherDisplay() else null
        if (target == null) {
            dismiss()
            report(
                when {
                    !wanted -> "Idle"
                    displayManager == null -> "No DisplayManager"
                    else -> "No second display exposed (${displayManager.displays.size} total)"
                },
            )
            return
        }
        val current = presentation
        if (current != null && current.display.displayId == target.displayId) return
        dismiss()
        show(target)
    }

    private fun otherDisplay(): Display? {
        val own = ContextCompat.getDisplayOrDefault(activity).displayId
        return displayManager?.displays?.firstOrNull { it.displayId != own && it.isValid }
    }

    private fun show(display: Display) {
        val fresh = Presentation(activity, display)
        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(activity)
            setViewTreeViewModelStoreOwner(activity)
            setViewTreeSavedStateRegistryOwner(activity)
            setContent(content)
        }
        fresh.setContentView(view)
        fresh.window?.apply {
            decorView.setViewTreeLifecycleOwner(activity)
            decorView.setViewTreeViewModelStoreOwner(activity)
            decorView.setViewTreeSavedStateRegistryOwner(activity)
            addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            )
        }
        fresh.setOnDismissListener { if (presentation === fresh) presentation = null }
        runCatching { fresh.show() }
            .onSuccess {
                presentation = fresh
                report(
                    "Presenting on display ${display.displayId} “${display.name}” " +
                        "(${stateLabel(display.state)})",
                )
            }
            .onFailure { report("Presentation refused on display ${display.displayId}: ${it.javaClass.simpleName}") }
    }

    private fun dismiss() {
        val current = presentation ?: return
        presentation = null
        runCatching { current.dismiss() }
    }

    private fun stateLabel(state: Int): String = when (state) {
        Display.STATE_OFF -> "off"
        Display.STATE_ON -> "on"
        Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> "doze"
        Display.STATE_ON_SUSPEND -> "on, suspended"
        else -> "state $state"
    }

    private fun report(status: String) {
        lastStatus.value = status
    }

    companion object {
        private val lastStatus = MutableStateFlow("Not started")

        /** What the bridge last did, for Diagnostics. */
        val status: StateFlow<String> = lastStatus.asStateFlow()
    }
}
