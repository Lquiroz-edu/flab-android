package com.lquiroz.flab.profiles

/**
 * The safe-app policy (DoD 14).
 *
 * F/LAB does not place interactive layers over banking, authenticators, password managers, the
 * lock screen, permission dialogs, payments, the camera, package installation or critical system
 * UI. This is not a visual preference; a layer over a permission prompt or a payment sheet is a
 * tapjacking surface, and F/LAB should not be one even by accident.
 *
 * Detection is deliberately blunt and errs towards protecting. A false positive costs one app its
 * treatment; a false negative costs the user something real.
 */
object SafeApps {

    /** Exact packages that are always protected. */
    private val protectedPackages: Set<String> = setOf(
        // System surfaces
        "com.android.systemui",
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.samsung.android.permissioncontroller",
        "com.android.keyguard",
        "com.samsung.android.lool",
        // Payments and wallets
        "com.google.android.apps.walletnfcrel",
        "com.samsung.android.spay",
        "com.samsung.android.spaylite",
        // Authenticators and password managers
        "com.google.android.apps.authenticator2",
        "com.azure.authenticator",
        "com.authy.authy",
        "com.bitwarden.authenticator",
        "com.x8bit.bitwarden",
        "com.lastpass.lpandroid",
        "com.agilebits.onepassword",
        "org.keepassdroid",
        "com.keepassdroid",
        "com.dashlane",
        // Cameras
        "com.sec.android.app.camera",
        "com.google.android.GoogleCamera",
    )

    /**
     * Package-name fragments that mark an app as sensitive.
     *
     * Matched against the whole lower-cased package name, so `com.bankinter.launcher` is caught by
     * `bank` while an unrelated `com.example.riverbanks` is the kind of false positive we accept.
     */
    private val protectedFragments: List<String> = listOf(
        "bank", "banco", "banca", "caixa", "cajamar", "bbva", "santander", "sabadell",
        "ing.", "ingdirect", "revolut", "n26", "wise.android", "paypal", "wallet",
        "authenticator", "password", "passkey", "vault", "2fa", "otp",
        "keyguard", "packageinstaller", "permissioncontroller",
    )

    fun isProtected(packageName: String): Boolean {
        val normalized = packageName.lowercase()
        if (normalized in protectedPackages) return true
        return protectedFragments.any { normalized.contains(it) }
    }

    /** The immutable profile a protected app gets. */
    fun lockedProfile(packageName: String): AppProfile = AppProfile(
        packageName = packageName,
        displayName = packageName,
        immersive = TreatmentMode.Off,
        continuity = TreatmentMode.Off,
        locked = true,
        note = "Protected by the F/LAB safe-app policy. No layer is placed over this app.",
    )
}
