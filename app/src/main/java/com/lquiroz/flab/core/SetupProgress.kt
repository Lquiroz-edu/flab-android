package com.lquiroz.flab.core

/** Which concrete action a setup step corresponds to, so the UI knows what to do when tapped. */
enum class SetupStepId { EngineOn, OverlayPermission, AccessibilityPermission }

/** One line of the guided activation checklist. */
data class SetupStep(val id: SetupStepId, val title: String, val done: Boolean)

/**
 * The guided path from "just installed" to "everything is genuinely running" (DoD 18, 43).
 *
 * Three steps, not the four a literal reading of the toggles would suggest. Turning the
 * `systemEffectsEnabled` preference on is not a step here: the setup flow sets that the moment the
 * user starts (see `FLabViewModel.beginGuidedSetup`), and the existing reconciliation in
 * `FLabViewModel.refreshAccess` already starts the service the instant both permissions are
 * granted, in whatever order the user grants them. So there is nothing left for the user to
 * remember after the second permission — no fourth switch to come back and flip. That collapse
 * from four remembered actions to three is the entire point: a feature is not "integrated" if
 * using it correctly depends on the user recalling a step nothing on screen is asking for.
 *
 * Pure and stateless: the same three booleans this already reads from `FLabUiState` and
 * `SystemEffectsState` are the only inputs, so this is trivially testable without an engine.
 */
object SetupProgress {

    fun steps(
        engineEnabled: Boolean,
        hasOverlayPermission: Boolean,
        hasAccessibilityPermission: Boolean,
    ): List<SetupStep> = listOf(
        SetupStep(SetupStepId.EngineOn, "Turn F/LAB on", engineEnabled),
        SetupStep(SetupStepId.OverlayPermission, "Allow display over other apps", hasOverlayPermission),
        SetupStep(
            SetupStepId.AccessibilityPermission,
            "Allow app awareness",
            hasAccessibilityPermission,
        ),
    )

    fun isComplete(steps: List<SetupStep>): Boolean = steps.all { it.done }

    /** The first undone step — what the checklist's single call-to-action should do next. */
    fun nextStep(steps: List<SetupStep>): SetupStep? = steps.firstOrNull { !it.done }
}
