package com.lquiroz.flab.core

/**
 * Why a feature is not in the stable set.
 *
 * Each of these maps onto a real reason a feature might misbehave, and each one is shown to the
 * user before they turn the feature on (DoD 15, 16).
 */
enum class ExperimentRisk(val label: String) {
    RequiresAccessibility("Needs an accessibility service"),
    LimitedCompatibility("Works on some devices only"),
    OneUiSpecific("Depends on a specific One UI version"),
    FoldSpecific("Only meaningful on a foldable"),
    UnderTest("Still being tested"),
}

/**
 * An F/LAB Experiment (DoD 15).
 *
 * Experiments are separated from the stable modules for one reason: nothing in this list is
 * trustworthy enough that F/LAB should turn it on for you. They are all off by default, they are
 * all behind a section the user has to open deliberately, and each states its risk before the
 * switch rather than after it.
 *
 * On accessibility specifically (DoD 16): F/LAB opens, configures, runs Fold Motion, Continuity
 * and its own Immersive treatment with no accessibility service at all. Only the experiments
 * marked [ExperimentRisk.RequiresAccessibility] need one, and each declares what it needs it for.
 */
data class Experiment(
    val id: String,
    val title: String,
    val description: String,
    val risk: ExperimentRisk,
    /** What the feature does with the access it asks for. Shown verbatim on the Access screen. */
    val accessRationale: String? = null,
    val enabledByDefault: Boolean = false,
)

object Experiments {

    const val FOREGROUND_APP_DETECTION = "foreground_app_detection"
    const val CONTEXT_DETECTION = "context_detection"
    const val SYSTEM_BAR_SAMPLING = "system_bar_sampling"
    const val COVER_DISPLAY_BRIDGE = "cover_display_bridge"

    val all: List<Experiment> = listOf(
        Experiment(
            id = FOREGROUND_APP_DETECTION,
            title = "Foreground app detection",
            description = "Lets App Profiles know which app you are actually in. Without it, " +
                "per-app settings apply only inside F/LAB's own surfaces.",
            risk = ExperimentRisk.RequiresAccessibility,
            accessRationale = "Reads only the package name of the window in front. " +
                "It does not read screen contents, text, or what you type.",
        ),
        Experiment(
            id = CONTEXT_DETECTION,
            title = "Context detection",
            description = "Tells a Reel from a Feed so immersion applies where it helps. " +
                "Accuracy varies by app version; when it is unsure, F/LAB does nothing.",
            risk = ExperimentRisk.RequiresAccessibility,
            accessRationale = "Reads window and layout structure to classify the current screen. " +
                "No text content is read or stored.",
        ),
        Experiment(
            id = SYSTEM_BAR_SAMPLING,
            title = "System bar sampling",
            description = "Samples the colour behind the status bar to keep icons legible. " +
                "Falls back to the system treatment whenever the sample is unreliable.",
            risk = ExperimentRisk.LimitedCompatibility,
        ),
        Experiment(
            id = COVER_DISPLAY_BRIDGE,
            title = "Cover display bridge",
            description = "Extends Continuity across the cover and inner panels of a Fold. " +
                "Tuned against One UI on the Galaxy Z Fold line.",
            risk = ExperimentRisk.OneUiSpecific,
        ),
    )

    fun byId(id: String): Experiment? = all.firstOrNull { it.id == id }

    /** Experiments needing an accessibility service, for the Access screen. */
    val accessibilityDependent: List<Experiment>
        get() = all.filter { it.risk == ExperimentRisk.RequiresAccessibility }
}
