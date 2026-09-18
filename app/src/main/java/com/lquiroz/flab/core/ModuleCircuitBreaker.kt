package com.lquiroz.flab.core

/**
 * Crash containment for modules (DoD 20).
 *
 * A module that fails repeatedly in a short window is switched off rather than retried forever.
 * F/LAB failing must never be something the user has to notice — One UI carries on, F/LAB quietly
 * stops doing the thing that was breaking, and Diagnostics records why.
 *
 * Failures older than [windowMillis] are forgotten, so an app that crashed once in the morning and
 * once at night is not treated the same as one crashing twice in ten seconds.
 *
 * Not thread-safe; the Core confines it to its own scope.
 */
class ModuleCircuitBreaker(
    private val threshold: Int = DEFAULT_THRESHOLD,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
) {
    init {
        require(threshold > 0) { "threshold must be positive" }
        require(windowMillis > 0) { "windowMillis must be positive" }
    }

    /**
     * Records a failure and returns the module's new state.
     *
     * The returned state has [ModuleRuntimeState.disabledByBreaker] set once the module has failed
     * [threshold] times within [windowMillis].
     */
    fun recordFailure(
        current: ModuleRuntimeState,
        message: String,
        nowMillis: Long,
    ): ModuleRuntimeState {
        val previousAt = current.lastErrorAtMillis
        val withinWindow = previousAt != null && nowMillis - previousAt <= windowMillis
        val failures = if (withinWindow) current.consecutiveFailures + 1 else 1
        return current.copy(
            disabledByBreaker = current.disabledByBreaker || failures >= threshold,
            lastErrorMessage = message,
            lastErrorAtMillis = nowMillis,
            consecutiveFailures = failures,
        )
    }

    /** Clears the failure history after a clean run, so the counter cannot creep up over a day. */
    fun recordSuccess(current: ModuleRuntimeState): ModuleRuntimeState =
        if (current.consecutiveFailures == 0) current else current.copy(consecutiveFailures = 0)

    /** Re-arms a module the breaker tripped. Only the user, via the UI, may call this. */
    fun reset(current: ModuleRuntimeState): ModuleRuntimeState = current.copy(
        disabledByBreaker = false,
        consecutiveFailures = 0,
    )

    companion object {
        const val DEFAULT_THRESHOLD = 3
        const val DEFAULT_WINDOW_MILLIS = 60_000L
    }
}
