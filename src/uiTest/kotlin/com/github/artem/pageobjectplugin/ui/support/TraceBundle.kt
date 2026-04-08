package com.github.artem.pageobjectplugin.ui.support

import kotlinx.serialization.Serializable

/**
 * Top-level schema written to `trace.json` inside each test's bundle directory
 * (`build/reports/uiTest/traces/<Class>__<method>/`).
 *
 * The schema is consumed by Task 19 (interactive trace viewer). The version
 * field allows future schema migrations without breaking existing readers.
 */
@Serializable
data class TraceBundle(
    val version: Int = 1,
    val test: TraceTest,
    val startedAt: String,
    val durationMs: Long,
    /** "passed" | "failed" | "aborted" */
    val status: String,
    /** True if the test was retried by RetryOnceExtension. */
    val flaky: Boolean = false,
    val failure: TraceFailure? = null,
    val steps: List<StepEntry> = emptyList(),
    val artifacts: TraceArtifacts = TraceArtifacts(),
)

@Serializable
data class TraceTest(
    val className: String,
    val method: String,
    val displayName: String,
    val feature: String? = null,
)

@Serializable
data class TraceFailure(
    val message: String,
    val stack: String,
    val file: String? = null,
    val line: Int? = null,
)

/**
 * Relative paths (relative to the bundle directory) of the artifacts the
 * extension successfully wrote. Any field that is null means the collector
 * could not produce the artifact for this test (the value is null instead of
 * an empty string so consumers can clearly distinguish "missing" from "empty").
 */
@Serializable
data class TraceArtifacts(
    val ideaLog: String? = null,
    val dom: String? = null,
    val jcefConsole: String? = null,
    val threads: String? = null,
)

/**
 * One step recorded by [StepRecorder.step]. Steps come from `Page.method` calls
 * during 13d, plus marker steps from `BaseUiTest.takeScreenshot(label)`.
 */
@Serializable
data class StepEntry(
    val index: Int,
    val label: String,
    val at: String,
    val durationMs: Long,
    /** Relative path to the screenshot under the bundle dir, or null. */
    val screenshot: String? = null,
    /** Captured exception class + message if the step block threw. */
    val error: String? = null,
)
