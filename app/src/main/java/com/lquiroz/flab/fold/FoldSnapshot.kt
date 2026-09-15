package com.lquiroz.flab.fold

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

enum class FoldPosture(val label: String) {
    Flat("Fully open"),
    HalfOpened("Half open"),
    NotDetected("No hinge reported"),
}

data class FoldSnapshot(
    val posture: FoldPosture,
    val orientation: String,
    val isSeparating: Boolean,
) {
    companion object {
        val empty = FoldSnapshot(FoldPosture.NotDetected, "None", false)

        fun from(layoutInfo: WindowLayoutInfo): FoldSnapshot {
            val feature = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                ?: return empty

            val posture = when (feature.state) {
                FoldingFeature.State.FLAT -> FoldPosture.Flat
                FoldingFeature.State.HALF_OPENED -> FoldPosture.HalfOpened
                else -> FoldPosture.NotDetected
            }

            val orientation = when (feature.orientation) {
                FoldingFeature.Orientation.HORIZONTAL -> "Horizontal"
                FoldingFeature.Orientation.VERTICAL -> "Vertical"
                else -> "Unknown"
            }

            return FoldSnapshot(posture, orientation, feature.isSeparating)
        }
    }
}

@Composable
fun rememberFoldSnapshot(): State<FoldSnapshot> {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val flow: Flow<FoldSnapshot> = remember(context, activity) {
        if (activity == null) {
            flowOf(FoldSnapshot.empty)
        } else {
            WindowInfoTracker.getOrCreate(context)
                .windowLayoutInfo(activity)
                .map(FoldSnapshot::from)
        }
    }
    return flow.collectAsState(initial = FoldSnapshot.empty)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
