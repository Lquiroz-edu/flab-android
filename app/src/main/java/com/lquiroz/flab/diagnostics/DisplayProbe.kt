package com.lquiroz.flab.diagnostics

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

/** Reads what `DisplayManager` exposes. Cheap; a handful of Binder reads, only when asked. */
object DisplayProbe {

    /**
     * The category string behind `DisplayManager.DISPLAY_CATEGORY_ALL_INCLUDING_DISABLED`, which
     * is not in the public SDK on every API level this app runs on. It is only a filter name: a
     * platform that does not honour it returns the ordinary list, and nothing here depends on it
     * doing more.
     */
    const val CATEGORY_ALL_INCLUDING_DISABLED = "android.hardware.display.category.ALL_INCLUDING_DISABLED"

    fun probe(context: Context): List<DisplayReport> {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return emptyList()
        val visible = manager.displays.map { it.displayId }.toSet()
        return allDisplays(manager).map { display ->
            val mode = display.mode
            DisplayReport(
                id = display.displayId,
                name = display.name,
                widthPx = mode.physicalWidth,
                heightPx = mode.physicalHeight,
                state = stateLabel(display.state),
                isDefault = display.displayId == Display.DEFAULT_DISPLAY,
                hidden = display.displayId !in visible,
            )
        }
    }

    /** Every display the platform will name, disabled ones included where it allows that. */
    fun allDisplays(manager: DisplayManager): List<Display> {
        val plain = manager.displays.toList()
        val extended = runCatching { manager.getDisplays(CATEGORY_ALL_INCLUDING_DISABLED).toList() }
            .getOrDefault(emptyList())
        return (plain + extended).distinctBy { it.displayId }
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
