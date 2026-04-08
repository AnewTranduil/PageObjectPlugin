package com.github.artem.pageobjectplugin.buildtools

import kotlinx.serialization.Serializable

/**
 * Schema v1 of `build/reports/claude-summary.json`. Documented in
 * `docs/tasks/task-14-ci-test-reporting.md`. Consumed by Claude Code
 * sessions reading the bundle from reports.artemon.cloud (suite slug
 * `claude-summary`) — see CLAUDE.md "Test Loop".
 *
 * No `quarantined` field: Task 13b never shipped `@Quarantine`, so the
 * Task 14 schema drops the field rather than carrying a perpetually-empty
 * placeholder.
 */
@Serializable
data class ClaudeSummary(
    val version: Int = 1,
    val generatedAt: String,
    val totals: Totals,
    val suites: List<Suite>,
)

@Serializable
data class Totals(
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val flaky: Int,
    val durationMs: Long,
)

@Serializable
data class Suite(
    /** "unit" | "uiTest" | "npm:playwright-snapshot-saver" | ... */
    val suite: String,
    val tests: List<TestEntry>,
)

@Serializable
data class TestEntry(
    /** Fully-qualified `ClassName.testName` (or Playwright `file > test`). */
    val name: String,
    /** "passed" | "failed" | "skipped" | "flaky" */
    val status: String,
    val durationMs: Long,
    val file: String? = null,
    val line: Int? = null,
    val failureMessage: String? = null,
    /**
     * Relative path under the report bundle to the per-test trace dir.
     * Populated only for `uiTest` entries with a matching trace.json bundle.
     */
    val tracePath: String? = null,
)
