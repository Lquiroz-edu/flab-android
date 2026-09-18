package com.lquiroz.flab.diagnostics

import com.lquiroz.flab.core.EngineStatus
import com.lquiroz.flab.core.FLabState
import com.lquiroz.flab.core.FoldPosture
import com.lquiroz.flab.core.FoldState
import com.lquiroz.flab.core.ModuleId
import com.lquiroz.flab.core.ModuleRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DoD 38: enough to debug a problem, and nothing that is none of our business. */
class DebugReportTest {

    private val device = DeviceReport(
        manufacturer = "samsung",
        model = "SM-F971B",
        androidRelease = "16",
        sdkInt = 36,
        oneUiVersion = "8.0",
        appVersionName = "0.2.0",
        hasHingeSensor = true,
        isFoldable = true,
    )

    private fun snapshot(
        state: FLabState = FLabState(engineStatus = EngineStatus.Active),
        lastError: ModuleError? = null,
    ) = DiagnosticsSnapshot(
        device = device,
        state = state,
        access = listOf(
            AccessRequirement("Hinge sensor", "why", "what breaks", granted = true),
            AccessRequirement("Accessibility", "why", "what breaks", granted = false, experimental = true),
        ),
        lastError = lastError,
        compatibilityRuleCount = 14,
        capturedAtMillis = 10_000_000L,
    )

    @Test
    fun `the report covers what F-LAB was doing`() {
        val report = DebugReport.build(
            snapshot(
                FLabState(
                    engineStatus = EngineStatus.Active,
                    fold = FoldState(posture = FoldPosture.HalfOpened, progress = 0.5f, hingeAngleDegrees = 91f),
                ),
            ),
        )

        assertTrue(report.contains("samsung SM-F971B"))
        assertTrue(report.contains("Half open"))
        assertTrue(report.contains("0.50"))
        assertTrue(report.contains("91.00"))
        assertTrue(report.contains("Rules loaded   14"))
    }

    @Test
    fun `the report says why a module is not running`() {
        val state = FLabState(
            engineStatus = EngineStatus.Active,
            moduleStates = ModuleId.entries.associateWith { ModuleRuntimeState() } +
                (
                    ModuleId.FoldMotion to ModuleRuntimeState(
                        disabledByBreaker = true,
                        lastErrorMessage = "IllegalStateException: no window",
                    )
                    ),
        )

        val report = DebugReport.build(snapshot(state))

        assertTrue(report.contains("Disabled automatically after repeated failures"))
        assertTrue(report.contains("IllegalStateException: no window"))
    }

    @Test
    fun `the report contains no absolute timestamps`() {
        val report = DebugReport.build(
            snapshot(lastError = ModuleError(ModuleId.Immersive, "boom", atMillis = 9_400_000L)),
        )

        assertTrue("should be relative", report.contains("10m 0s ago"))
        assertFalse("must not leak a wall clock", report.contains("9400000"))
        assertFalse(report.contains("10000000"))
    }

    @Test
    fun `configured apps can be omitted entirely`() {
        val withApps = DebugReport.build(
            snapshot(),
            configuredPackages = listOf("com.instagram.android"),
            includeConfiguredApps = true,
        )
        assertTrue(withApps.contains("com.instagram.android"))

        val withoutApps = DebugReport.build(
            snapshot(),
            configuredPackages = listOf("com.instagram.android"),
            includeConfiguredApps = false,
        )
        assertFalse(withoutApps.contains("com.instagram.android"))
        assertTrue(withoutApps.contains("Configured     omitted"))
    }

    @Test
    fun `the report never enumerates installed apps`() {
        // Only what the user configured appears, and only because they configured it.
        val report = DebugReport.build(snapshot(), configuredPackages = emptyList())

        assertTrue(report.contains("Configured     omitted"))
    }

    @Test
    fun `relative durations read sensibly`() {
        assertEquals("0s", DebugReport.relativeDuration(0L))
        assertEquals("0s", DebugReport.relativeDuration(-5L))
        assertEquals("45s", DebugReport.relativeDuration(45_000L))
        assertEquals("2m 5s", DebugReport.relativeDuration(125_000L))
        assertEquals("1h 1m", DebugReport.relativeDuration(3_660_000L))
    }

    @Test
    fun `permission state is reported without explaining it away`() {
        val report = DebugReport.build(snapshot())

        assertTrue(report.contains("Hinge sensor   granted"))
        assertTrue(report.contains("Accessibility  not granted (experimental)"))
    }

    @Test
    fun `a tripped module counts as a blocking issue`() {
        val tripped = snapshot(
            FLabState(
                engineStatus = EngineStatus.Active,
                moduleStates = mapOf(ModuleId.FoldMotion to ModuleRuntimeState(disabledByBreaker = true)),
            ),
        )

        assertTrue(tripped.hasBlockingIssue)
    }
}
