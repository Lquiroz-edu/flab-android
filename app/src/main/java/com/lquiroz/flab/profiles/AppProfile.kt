package com.lquiroz.flab.profiles

/** How aggressively F/LAB may treat one app. */
enum class TreatmentMode(val displayName: String) {
    /** Always apply the treatment when a compatible context is detected. */
    On("On"),
    /** Apply it only where the compatibility rules say it adds something (DoD 47, 48). */
    Auto("Auto"),
    /** Never apply it. */
    Off("Off"),
}

/**
 * Per-app configuration, the user-facing half of DoD 6 and DoD 13.
 *
 * [locked] marks an app the safe-app policy protects (DoD 14). A locked profile is shown in the
 * app list but cannot be switched on, and the UI explains why rather than silently ignoring taps.
 */
data class AppProfile(
    val packageName: String,
    val displayName: String,
    val immersive: TreatmentMode,
    val continuity: TreatmentMode,
    val locked: Boolean = false,
    val note: String? = null,
) {
    val isDisabled: Boolean
        get() = immersive == TreatmentMode.Off && continuity == TreatmentMode.Off

    /** The one-line status shown in the app list. */
    val summary: String
        get() = when {
            locked -> "F/LAB disabled — protected app"
            isDisabled -> "F/LAB disabled"
            else -> "Immersive ${immersive.displayName} · Continuity ${continuity.displayName}"
        }
}

/**
 * The seeded app profiles from DoD 6.
 *
 * These are defaults, not decisions: every one of them can be changed by the user, and every one of
 * them still has to get past the compatibility rules and the context detector before anything is
 * actually applied.
 */
object DefaultAppProfiles {

    const val INSTAGRAM = "com.instagram.android"
    const val YOUTUBE = "com.google.android.youtube"
    const val TIKTOK = "com.zhiliaoapp.musically"
    const val CHROME = "com.android.chrome"
    const val SAMSUNG_CAMERA = "com.sec.android.app.camera"
    const val GOOGLE_MAPS = "com.google.android.apps.maps"
    const val ONE_UI_HOME = "com.sec.android.app.launcher"
    const val SAMSUNG_GALLERY = "com.sec.android.gallery3d"
    const val WHATSAPP = "com.whatsapp"

    val seeded: List<AppProfile> = listOf(
        AppProfile(
            packageName = INSTAGRAM,
            displayName = "Instagram",
            immersive = TreatmentMode.On,
            continuity = TreatmentMode.On,
            note = "Reels and Stories only. Feed and DM stay untouched.",
        ),
        AppProfile(
            packageName = YOUTUBE,
            displayName = "YouTube",
            immersive = TreatmentMode.Auto,
            continuity = TreatmentMode.On,
            note = "Already immersive in most places, so F/LAB mostly stands back.",
        ),
        AppProfile(
            packageName = TIKTOK,
            displayName = "TikTok",
            immersive = TreatmentMode.On,
            continuity = TreatmentMode.On,
            note = "Treated as full-screen vertical content.",
        ),
        AppProfile(
            packageName = CHROME,
            displayName = "Chrome",
            immersive = TreatmentMode.Auto,
            continuity = TreatmentMode.On,
            note = "Colour continuity only. No overlays over page content.",
        ),
        AppProfile(
            packageName = SAMSUNG_CAMERA,
            displayName = "Camera",
            immersive = TreatmentMode.Off,
            continuity = TreatmentMode.Off,
            locked = true,
            note = "Nothing may sit between you and the shutter.",
        ),
        AppProfile(
            packageName = GOOGLE_MAPS,
            displayName = "Maps",
            immersive = TreatmentMode.Off,
            continuity = TreatmentMode.On,
            note = "Navigation information must never be covered.",
        ),
        AppProfile(
            packageName = ONE_UI_HOME,
            displayName = "One UI Home",
            immersive = TreatmentMode.Off,
            continuity = TreatmentMode.Auto,
            note = "The launcher owns its own transitions.",
        ),
        AppProfile(
            packageName = SAMSUNG_GALLERY,
            displayName = "Gallery",
            immersive = TreatmentMode.Auto,
            continuity = TreatmentMode.On,
        ),
        AppProfile(
            packageName = WHATSAPP,
            displayName = "WhatsApp",
            immersive = TreatmentMode.Off,
            continuity = TreatmentMode.On,
        ),
    )

    /**
     * The profile for [packageName], falling back to a conservative default.
     *
     * An app nobody has configured gets Continuity on Auto and Immersive off. Continuity is
     * safe everywhere because it only ever shortens a transition F/LAB is already showing;
     * Immersive changes how another app looks, so it stays opt-in.
     */
    fun forPackage(packageName: String, overrides: List<AppProfile> = emptyList()): AppProfile {
        overrides.firstOrNull { it.packageName == packageName }?.let { return it }
        if (SafeApps.isProtected(packageName)) return SafeApps.lockedProfile(packageName)
        seeded.firstOrNull { it.packageName == packageName }?.let { return it }
        return AppProfile(
            packageName = packageName,
            displayName = packageName,
            immersive = TreatmentMode.Off,
            continuity = TreatmentMode.Auto,
        )
    }
}
