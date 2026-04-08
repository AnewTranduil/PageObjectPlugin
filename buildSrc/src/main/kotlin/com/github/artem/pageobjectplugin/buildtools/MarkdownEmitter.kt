package com.github.artem.pageobjectplugin.buildtools

/**
 * Renders a [ClaudeSummary] to the human-readable Markdown layout
 * documented in `docs/tasks/task-14-ci-test-reporting.md` and used by the
 * Test Loop section of `CLAUDE.md`.
 *
 * Sections:
 *  - Header with totals + duration.
 *  - `## Failures` (only if any failed): every failed test, its
 *    `file:line`, message, and (uiTest) trace bundle path.
 *  - `## Flaky` (only if any flaky): every flaky test that passed on retry.
 *  - `## Suites`: per-suite pass/fail/skip counts as a quick TOC.
 */
object MarkdownEmitter {

    fun render(summary: ClaudeSummary, gitSha: String? = null): String {
        val sb = StringBuilder()
        val header = if (gitSha != null) "Test Summary — ${gitSha.take(12)}" else "Test Summary"
        sb.appendLine("# $header")
        sb.appendLine()
        sb.appendLine("Generated: ${summary.generatedAt}")
        sb.appendLine()
        sb.appendLine("Totals: ${formatTotals(summary.totals)}")
        sb.appendLine()

        val failures = summary.suites.flatMap { s -> s.tests.filter { it.status == "failed" }.map { s.suite to it } }
        if (failures.isNotEmpty()) {
            sb.appendLine("## Failures (${failures.size})")
            sb.appendLine()
            failures.forEachIndexed { i, (suite, t) ->
                sb.appendLine("${i + 1}. `${t.name}` _(${suite})_")
                val location = locationOf(t)
                val message = t.failureMessage?.let { msg -> stripStackBoilerplate(msg) }
                if (message != null && location != null) {
                    sb.appendLine("   $message — at $location")
                } else if (message != null) {
                    sb.appendLine("   $message")
                } else if (location != null) {
                    sb.appendLine("   at $location")
                }
                if (t.tracePath != null) {
                    sb.appendLine("   Trace: ${t.tracePath}")
                }
            }
            sb.appendLine()
        }

        val flaky = summary.suites.flatMap { s -> s.tests.filter { it.status == "flaky" }.map { s.suite to it } }
        if (flaky.isNotEmpty()) {
            sb.appendLine("## Flaky (retried once) (${flaky.size})")
            sb.appendLine()
            flaky.forEach { (suite, t) ->
                sb.append("- `${t.name}` _(${suite})_ — passed on retry")
                if (t.tracePath != null) sb.append(" — Trace: ${t.tracePath}")
                sb.appendLine()
            }
            sb.appendLine()
        }

        sb.appendLine("## Suites")
        sb.appendLine()
        summary.suites.forEach { suite ->
            val passed = suite.tests.count { it.status == "passed" }
            val failed = suite.tests.count { it.status == "failed" }
            val skipped = suite.tests.count { it.status == "skipped" }
            val flak = suite.tests.count { it.status == "flaky" }
            val durMs = suite.tests.sumOf { it.durationMs }
            sb.appendLine("- **${suite.suite}** — $passed passed, $failed failed, $skipped skipped, $flak flaky (${formatDuration(durMs)})")
        }

        return sb.toString()
    }

    private fun formatTotals(t: Totals): String {
        return "${t.passed} passed, ${t.failed} failed, ${t.skipped} skipped, ${t.flaky} flaky (${formatDuration(t.durationMs)})"
    }

    private fun formatDuration(ms: Long): String {
        if (ms < 1000) return "${ms}ms"
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return if (min > 0) "${min}m ${sec}s" else "${sec}s"
    }

    private fun locationOf(t: TestEntry): String? {
        val f = t.file ?: return null
        return if (t.line != null) "$f:${t.line}" else f
    }

    private fun stripStackBoilerplate(message: String): String {
        // Use only the first line so the bullet stays one row tall.
        return message.lines().firstOrNull()?.trim().orEmpty().ifEmpty { message.trim() }
    }
}
