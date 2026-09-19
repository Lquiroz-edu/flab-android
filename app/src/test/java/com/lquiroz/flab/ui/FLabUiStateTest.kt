package com.lquiroz.flab.ui

import com.lquiroz.flab.core.EngineStatus
import com.lquiroz.flab.core.FLabState
import com.lquiroz.flab.core.ModuleId
import com.lquiroz.flab.core.ModuleRuntimeState
import com.lquiroz.flab.core.SetupProgress
import com.lquiroz.flab.settings.FLabConfiguration
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression cover for a real bug: `needsAttention` used to also fire whenever System effects was
 * enabled but missing a grant — exactly the normal, expected state partway through the Home setup
 * checklist — which stacked an empty "Action required" card underneath a checklist already saying
 * the same thing with an actual button attached.
 */
class FLabUiStateTest {

    // needsAttention reads configuration.enabled, a field kept in sync with state.engineStatus by
    // FLabCore in the real app but independently settable in this pure model — both are set
    // together here so the tests reflect a state FLabCore would actually produce.
    private val runningEngine = FLabState(engineStatus = EngineStatus.Active)
    private val engineOnConfig = FLabConfiguration(enabled = true)

    @Test
    fun `system effects missing a grant does not trigger the attention card`() {
        val ui = FLabUiState(
            state = runningEngine,
            configuration = engineOnConfig,
            systemEffects = SystemEffectsState(
                enabled = true,
                hasOverlayPermission = true,
                accessibilityEnabled = false,
                engineEnabled = true,
            ),
        )

        assertFalse(
            "the setup checklist owns this case, not the attention card",
            ui.needsAttention,
        )
    }

    @Test
    fun `a tripped module still triggers the attention card`() {
        val ui = FLabUiState(
            state = runningEngine.copy(
                moduleStates = mapOf(ModuleId.FoldMotion to ModuleRuntimeState(disabledByBreaker = true)),
            ),
            configuration = engineOnConfig,
        )

        assertTrue(ui.needsAttention)
    }

    @Test
    fun `the engine being off never triggers the attention card`() {
        val ui = FLabUiState(
            state = FLabState(
                engineStatus = EngineStatus.Disabled,
                moduleStates = mapOf(ModuleId.FoldMotion to ModuleRuntimeState(disabledByBreaker = true)),
            ),
            configuration = FLabConfiguration(enabled = false),
        )

        assertFalse(ui.needsAttention)
    }

    @Test
    fun `the setup checklist and the attention card never both have something to say`() {
        // Sweep a spread of plausible states and assert the invariant directly, rather than trust
        // a couple of hand-picked examples: whenever the checklist is incomplete, the breaker-only
        // attention card must have nothing of its own to add.
        val configurations = listOf(true, false)
        val overlays = listOf(true, false)
        val accessibility = listOf(true, false)
        val breakerTripped = listOf(true, false)

        for (engineOn in configurations) {
            for (overlay in overlays) {
                for (access in accessibility) {
                    for (tripped in breakerTripped) {
                        val state = FLabState(
                            engineStatus = if (engineOn) EngineStatus.Active else EngineStatus.Disabled,
                            moduleStates = mapOf(
                                ModuleId.FoldMotion to ModuleRuntimeState(disabledByBreaker = tripped),
                            ),
                        )
                        val ui = FLabUiState(
                            state = state,
                            configuration = FLabConfiguration(enabled = engineOn),
                            systemEffects = SystemEffectsState(
                                enabled = true,
                                hasOverlayPermission = overlay,
                                accessibilityEnabled = access,
                                engineEnabled = engineOn,
                            ),
                        )
                        val setupIncomplete = !SetupProgress.isComplete(
                            SetupProgress.steps(engineOn, overlay, access),
                        )

                        if (setupIncomplete && !tripped) {
                            assertFalse(
                                "attention card must be silent while only the checklist has " +
                                    "something to say (engineOn=$engineOn overlay=$overlay " +
                                    "access=$access)",
                                ui.needsAttention,
                            )
                        }
                    }
                }
            }
        }
    }
}
