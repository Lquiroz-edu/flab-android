package com.lquiroz.flab.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.Collator
import kotlin.math.roundToInt

/** One launchable activity, with its icon already rasterised so drawing a page allocates nothing. */
data class LauncherEntry(
    val component: ComponentName,
    val user: UserHandle,
    val label: String,
    val icon: ImageBitmap,
) {
    val key: String get() = component.flattenToShortString()
}

/** Everything F/LAB Home shows: the dock, and the grid (every app not already in the dock). */
data class LauncherCatalogue(
    val apps: List<LauncherEntry> = emptyList(),
    val dock: List<LauncherEntry> = emptyList(),
) {
    val grid: List<LauncherEntry> = apps.filterNot { entry -> dock.any { it.key == entry.key } }
}

/**
 * The apps F/LAB Home can show, through [LauncherApps] — the API Android provides to home screens
 * and the whole of what F/LAB reads about other apps. It lists launchable activities and their
 * icons, which any home screen must; it does not read anything about how they are used. The
 * manifest's `<queries>` are the launcher-standard ones, not `QUERY_ALL_PACKAGES`.
 */
class LauncherAppsRepository(context: Context, private val scope: CoroutineScope) {

    private val appContext = context.applicationContext
    private val launcherApps = appContext.getSystemService(LauncherApps::class.java)
    private val iconPx = (ICON_DP * appContext.resources.displayMetrics.density).roundToInt()

    private val _catalogue = MutableStateFlow(LauncherCatalogue())
    val catalogue: StateFlow<LauncherCatalogue> = _catalogue.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = reload()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = reload()
        override fun onPackagesAvailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean,
        ) = reload()

        override fun onPackagesUnavailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean,
        ) = reload()
    }

    init {
        launcherApps?.registerCallback(callback, Handler(Looper.getMainLooper()))
        reload()
    }

    fun reload() {
        scope.launch(Dispatchers.IO) { _catalogue.value = load() }
    }

    fun launch(entry: LauncherEntry, sourceBounds: Rect?, options: Bundle?) {
        runCatching { launcherApps?.startMainActivity(entry.component, entry.user, sourceBounds, options) }
    }

    fun openAppDetails(entry: LauncherEntry) {
        runCatching { launcherApps?.startAppDetailsActivity(entry.component, entry.user, null, null) }
    }

    fun release() {
        launcherApps?.unregisterCallback(callback)
    }

    private fun load(): LauncherCatalogue {
        val apps = launcherApps ?: return LauncherCatalogue()
        val user = Process.myUserHandle()
        val collator = Collator.getInstance()
        val entries = apps.getActivityList(null, user)
            .map { info ->
                LauncherEntry(
                    component = info.componentName,
                    user = user,
                    label = info.label.toString(),
                    icon = info.getIcon(0).toBitmap(iconPx, iconPx).asImageBitmap(),
                )
            }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
        val dock = dockPackages()
            .mapNotNull { pkg -> entries.firstOrNull { it.component.packageName == pkg } }
            .distinctBy { it.key }
            .take(MAX_DOCK)
        return LauncherCatalogue(apps = entries, dock = dock)
    }

    /** The user's own defaults for what a dock is for: calls, messages, the browser, the camera. */
    private fun dockPackages(): List<String> {
        val pm = appContext.packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
        )
        // "android" is the system's chooser standing in for "no default yet", not an app.
        return intents.mapNotNull { intent -> resolveDefault(pm, intent)?.takeUnless { it == "android" } }
    }

    private fun resolveDefault(pm: PackageManager, intent: Intent): String? {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return info?.activityInfo?.packageName
    }

    private companion object {
        const val ICON_DP = 64f
        const val MAX_DOCK = 4
    }
}
