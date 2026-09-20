package com.lquiroz.flab.diagnostics

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/** Reads what `DisplayManager` exposes. Cheap; a handful of Binder reads, only when asked. */
object DisplayProbe {

    fun probe(context: Context): List<DisplayReport> {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return emptyList()
        return manager.displays.map { display ->
            val mode = display.mode
            DisplayReport(
                id = display.displayId,
                name = display.name,
                widthPx = mode.physicalWidth,
                heightPx = mode.physicalHeight,
                state = stateLabel(display.state),
                isDefault = display.displayId == Display.DEFAULT_DISPLAY,
            )
        }
    }

    fun stateLabel(state: Int): String = when (state) {
        Display.STATE_OFF -> "Off"
        Display.STATE_ON -> "On"
        Display.STATE_DOZE -> "Doze"
        Display.STATE_DOZE_SUSPEND -> "Doze (suspended)"
        Display.STATE_ON_SUSPEND -> "On (suspended)"
        Display.STATE_VR -> "VR"
        else -> "Unknown"
    }
}
