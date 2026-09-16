package com.lquiroz.flab.profiles

import android.content.Context

enum class AppStrategy { OBSERVE_ONLY, IMMERSIVE_OPT_IN, NO_INTERVENTION }

data class CompatibilityRule(
    val id: String,
    val label: String,
    val packages: Set<String>,
    val strategy: AppStrategy,
    val continuity: Boolean,
    val reason: String,
    val safe: Boolean = false,
)

/** Replaceable local provider; a future signed remote config can implement the same contract. */
interface CompatibilityRulesProvider {
    fun rules(): List<CompatibilityRule>
    fun find(packageName: String?): CompatibilityRule?
}

object ProfileCatalog : CompatibilityRulesProvider {
    private val catalog = listOf(
        CompatibilityRule("instagram", "Instagram", setOf("com.instagram.android"),
            AppStrategy.IMMERSIVE_OPT_IN, true, "Capa cromática opcional; ante contexto incierto no interviene."),
        CompatibilityRule("youtube", "YouTube", setOf("com.google.android.youtube"),
            AppStrategy.OBSERVE_ONLY, true, "YouTube ya resuelve correctamente la mayoría de superficies."),
        CompatibilityRule("tiktok", "TikTok", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill"),
            AppStrategy.IMMERSIVE_OPT_IN, true, "Integración vertical opcional y conservadora."),
        CompatibilityRule("chrome", "Chrome", setOf("com.android.chrome"),
            AppStrategy.OBSERVE_ONLY, true, "Sin overlays agresivos."),
        CompatibilityRule("maps", "Google Maps", setOf("com.google.android.apps.maps"),
            AppStrategy.NO_INTERVENTION, false, "No se oculta información de navegación.", safe = true),
        CompatibilityRule("camera", "Cámara Samsung", setOf("com.sec.android.app.camera"),
            AppStrategy.NO_INTERVENTION, false, "Preview y controles protegidos.", safe = true),
        CompatibilityRule("gallery", "Galería Samsung", setOf("com.sec.android.gallery3d"),
            AppStrategy.OBSERVE_ONLY, true, "Continuidad sin cubrir controles."),
        CompatibilityRule("whatsapp", "WhatsApp", setOf("com.whatsapp"),
            AppStrategy.OBSERVE_ONLY, true, "Mensajes y teclado permanecen intactos."),
        CompatibilityRule("settings", "Ajustes y permisos", setOf("com.android.settings", "com.samsung.android.app.settings"),
            AppStrategy.NO_INTERVENTION, false, "Superficie crítica del sistema.", safe = true),
        CompatibilityRule("installer", "Instalador", setOf("com.google.android.packageinstaller", "com.samsung.android.packageinstaller"),
            AppStrategy.NO_INTERVENTION, false, "Instalación protegida.", safe = true),
    )

    override fun rules() = catalog
    override fun find(packageName: String?) = catalog.firstOrNull { packageName in it.packages }

    fun isProtected(packageName: String?): Boolean {
        if (packageName == null) return true
        if (find(packageName)?.safe == true) return true
        val normalized = packageName.lowercase()
        return PROTECTED_HINTS.any { it in normalized }
    }

    fun immersiveEnabled(context: Context, rule: CompatibilityRule): Boolean =
        context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
            .getBoolean("immersive_${rule.id}", false)

    fun setImmersiveEnabled(context: Context, rule: CompatibilityRule, enabled: Boolean) {
        context.getSharedPreferences("profiles", Context.MODE_PRIVATE)
            .edit().putBoolean("immersive_${rule.id}", enabled).apply()
    }

    private val PROTECTED_HINTS = setOf(
        "bank", "wallet", "payment", "authenticator", "password", "keyguard",
        "permissioncontroller", "packageinstaller", "systemui", "camera", "knox", ".spay", "finance",
    )
}
