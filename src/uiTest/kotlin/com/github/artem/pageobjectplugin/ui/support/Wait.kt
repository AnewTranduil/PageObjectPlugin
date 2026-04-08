package com.github.artem.pageobjectplugin.ui.support

import java.time.Duration

/**
 * Polling primitives for UI tests. Replaces fixed [Thread.sleep] in
 * test infrastructure (BaseUiTest + fixtures) with bounded polls so the
 * suite is fast on the happy path and predictable on failure.
 */
object Wait {

    /**
     * Polls [block] until it returns a non-null value or [timeout] expires.
     * Throws [AssertionError] (with the last block exception as cause) on timeout.
     *
     * @param timeout total budget for the poll
     * @param interval pause between attempts
     * @param message lazy diagnostic appended to the timeout error
     * @param block returns non-null on success, null/throws to keep polling
     */
    fun <T : Any> pollUntil(
        timeout: Duration = Duration.ofSeconds(10),
        interval: Duration = Duration.ofMillis(100),
        message: () -> String = { "condition not met" },
        block: () -> T?,
    ): T {
        val deadline = System.nanoTime() + timeout.toNanos()
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            try {
                val v = block()
                if (v != null) return v
            } catch (t: Throwable) {
                lastError = t
            }
            Thread.sleep(interval.toMillis())
        }
        throw AssertionError(
            "pollUntil: ${message()} (timeout=${timeout.toMillis()}ms)",
            lastError,
        )
    }

    /**
     * Polls [condition] until it returns true or [timeout] expires.
     */
    fun pollUntilTrue(
        timeout: Duration = Duration.ofSeconds(10),
        interval: Duration = Duration.ofMillis(100),
        message: () -> String = { "condition not met" },
        condition: () -> Boolean,
    ) {
        pollUntil(timeout, interval, message) { if (condition()) Unit else null }
    }

    /**
     * Runs [block]; on any throwable, runs it once more. Only the second
     * failure escapes. Useful for one-off transient assertion sites where
     * a class-level retry would be too coarse.
     */
    fun retryOnce(block: () -> Unit) {
        try {
            block()
            return
        } catch (_: Throwable) {
            // fall through to second attempt
        }
        block()
    }
}
