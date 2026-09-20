package com.lquiroz.flab.system

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import android.text.TextUtils

/**
 * The capabilities the system-wide effect needs, and how to ask for them.
 *
 * Nothing here grants anything. Every one of these is a trip to Settings that the user makes
 * themselves — which is the whole design of these permissions, and F/LAB does not try to be clever
 * about it.
 */
object SystemAccess {

    /** "Display over other apps". Without it the overlay cannot be added at all. */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun overlaySettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.packageName}".toUri(),
        )

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** App info, which is where "Allow restricted settings" lives. */
    fun appDetailsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri(),
        )

    /**
     * Opens the system's live-wallpaper preview pre-selected to [FoldWallpaperService].
     *
     * `EXTRA_LIVE_WALLPAPER_COMPONENT` is honoured by AOSP's own picker and by One UI's; nothing
     * about it is Samsung-specific. If a launcher does not support it, the intent still resolves
     * to a picker the user can navigate manually, and the caller (`MainActivity.openSettings`)
     * already wraps every settings intent in `runCatching`.
     */
    fun changeLiveWallpaperIntent(context: Context): Intent =
        Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(context, FoldWallpaperService::class.java),
        )

    /** Whether F/LAB's Fold Wallpaper is the live wallpaper right now. */
    fun isFoldWallpaperActive(context: Context): Boolean {
        val expected = ComponentName(context, FoldWallpaperService::class.java)
        return runCatching { WallpaperManager.getInstance(context).wallpaperInfo?.component }
            .getOrNull() == expected
    }

    /** Whether F/LAB Home is the default home screen. */
    fun isDefaultHome(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pm = context.packageManager
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.resolveActivity(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** The system's default-home picker. */
    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)

    /**
     * Whether F/LAB's accessibility service is enabled.
     *
     * Read from the secure setting rather than from the service object, because the service is only
     * instantiated once it is already enabled — asking the object would always answer "no" at the
     * moment the answer matters.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expected = ComponentName(context, FLabAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false

        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        for (entry in splitter) {
            val component = ComponentName.unflattenFromString(entry) ?: continue
            if (component == expected) return true
        }
        return false
    }

    /**
     * The manual unblock steps for a sideloaded build, in the order they have to be done.
     *
     * Android 13 introduced Restricted Settings: an app installed without the session-based
     * `PackageInstaller` API — which is what happens when an APK is opened from a file manager or a
     * browser — has Accessibility and Notification Listener blocked. There is no API for an app to
     * lift that on itself, by design, so the only honest thing F/LAB can do is explain the route.
     *
     * Ordering matters and is the part people get wrong. On recent One UI, Auto Blocker prevents
     * sideloading outright and hides the "Allow restricted settings" entry, so it has to come down
     * first. And the entry itself stays hidden until the user has actually tried to enable the
     * service and been refused, which is why the last step reads the way it does.
     *
     * Installing through something that uses the session API — a store, or a tool like Obtainium
     * pointed at a release — avoids all of this, which is why it is offered first.
     */
    val restrictedSettingsSteps: List<AccessStep> = listOf(
        AccessStep(
            title = "Best route: install from a release",
            body = "Installers that use Android's session API — an app store, or a tool like " +
                "Obtainium pointed at F/LAB's GitHub releases — are exempt from this block " +
                "entirely. If you install that way, none of the steps below are needed.",
        ),
        AccessStep(
            title = "1. Turn Auto Blocker down",
            body = "Settings › Security and privacy › Auto Blocker. While it is on, nothing " +
                "installs from outside Play or Galaxy Store, and the option in step 3 stays " +
                "hidden. On One UI 9 its Maximum restrictions mode also blocks USB connections " +
                "completely, so adb will not reach the device either.",
        ),
        AccessStep(
            title = "2. Try to enable the service",
            body = "Settings › Accessibility › Installed apps › F/LAB. You will be refused with " +
                "\"Restricted setting\". That refusal is required: the option in step 3 does not " +
                "appear until Android has shown it to you at least once.",
        ),
        AccessStep(
            title = "3. Allow restricted settings",
            body = "Settings › Apps › F/LAB, then either the ⋮ menu at the top right or an entry " +
                "near the bottom of the page, depending on the build. Confirm with your PIN or " +
                "fingerprint, then go back to step 2 and the switch will work.",
        ),
    )
}

/** One step of a guided flow, rendered on the F/LAB Access screen. */
data class AccessStep(val title: String, val body: String)
