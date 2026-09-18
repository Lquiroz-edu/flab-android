package com.lquiroz.flab.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DoD 20: repeated failures take a module out, and never take the phone with them. */
class ModuleCircuitBreakerTest {

    private val breaker = ModuleCircuitBreaker(threshold = 3, windowMillis = 60_000L)

    @Test
    fun `a module survives failures below the threshold`() {
        var state = ModuleRuntimeState()
        state = breaker.recordFailure(state, "boom", 1_000L)
        state = breaker.recordFailure(state, "boom", 2_000L)

        assertFalse(state.disabledByBreaker)
        assertTrue(state.isAvailable)
    }

    @Test
    fun `three failures in the window disable the module`() {
        var state = ModuleRuntimeState()
        state = breaker.recordFailure(state, "boom", 1_000L)
        state = breaker.recordFailure(state, "boom", 2_000L)
        state = breaker.recordFailure(state, "boom", 3_000L)

        assertTrue(state.disabledByBreaker)
        assertFalse(state.isAvailable)
        assertEquals("Disabled automatically after repeated failures", state.unavailableReason)
    }

    @Test
    fun `failures spread across the day do not accumulate`() {
        var state = ModuleRuntimeState()
        state = breaker.recordFailure(state, "boom", 0L)
        state = breaker.recordFailure(state, "boom", 3_600_000L)
        state = breaker.recordFailure(state, "boom", 7_200_000L)

        assertFalse("one failure an hour is not a broken module", state.disabledByBreaker)
        assertEquals(1, state.consecutiveFailures)
    }

    @Test
    fun `a clean run clears the failure count`() {
        var state = ModuleRuntimeState()
        state = breaker.recordFailure(state, "boom", 1_000L)
        state = breaker.recordFailure(state, "boom", 2_000L)
        state = breaker.recordSuccess(state)
        state = breaker.recordFailure(state, "boom", 3_000L)

        assertFalse(state.disabledByBreaker)
    }

    @Test
    fun `a tripped module stays tripped until reset`() {
        var state = ModuleRuntimeState()
        repeat(3) { state = breaker.recordFailure(state, "boom", it * 1_000L) }
        assertTrue(state.disabledByBreaker)

        state = breaker.recordSuccess(state)
        assertTrue("success alone must not re-arm it", state.disabledByBreaker)

        state = breaker.reset(state)
        assertFalse(state.disabledByBreaker)
        assertTrue(state.isAvailable)
    }

    @Test
    fun `the breaker records what went wrong`() {
        val state = breaker.recordFailure(ModuleRuntimeState(), "IllegalStateException: no window", 5L)

        assertEquals("IllegalStateException: no window", state.lastErrorMessage)
        assertEquals(5L, state.lastErrorAtMillis)
    }

    @Test
    fun `the user's own preference is never overwritten by the breaker`() {
        var state = ModuleRuntimeState(enabled = true)
        repeat(3) { state = breaker.recordFailure(state, "boom", it * 1_000L) }

        assertTrue("the user still wants this module on", state.enabled)
        assertFalse("but it is not running", state.isAvailable)
    }
}
