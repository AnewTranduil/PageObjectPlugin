package com.github.artem.pageobjectplugin.buildtools

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.time.Instant

/**
 * Top-level orchestrator. Wires the parsers + augmenter + emitter together
 * and writes both `claude-summary.json` and `claude-summary.md` under
 * `<buildDir>/reports/`.
 *
 * Path layout (relative to [rootDir] / [buildDir]):
 *  - Unit tests:    `<buildDir>/test-results/test/`
 *  - UI tests:      `<buildDir>/test-results/uiTest/`
 *  - UI traces:     `<buildDir>/reports/uiTest/traces/`
 *  - Playwright:    `<rootDir>/packages/playwright-snapshot-saver/test-results/results.json`
 *
 * JSON output is built manually via `buildJsonObject {}` so buildSrc does
 * not need the `kotlinx.serialization` Gradle compiler plugin (which
 * conflicts with `kotlin-dsl`'s embedded Kotlin — see
 * `buildSrc/build.gradle.kts`). Only the runtime library is required.
 */
object ClaudeSummaryGenerator {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    fun run(rootDir: File, buildDir: File, gitSha: String? = null): ClaudeSummary {
        val unit = JUnitXmlParser.parse(buildDir.resolve("test-results/test"))
        val uiRaw = JUnitXmlParser.parse(buildDir.resolve("test-results/uiTest"))
        val ui = TraceJsonAugmenter.augment(
            tracesRoot = buildDir.resolve("reports/uiTest/traces"),
            entries = uiRaw,
            relativizeBase = rootDir,
        )
        val playwright = PlaywrightJsonParser.parse(
            rootDir.resolve("packages/playwright-snapshot-saver/test-results/results.json")
        )

        val suites = listOf(
            Suite(suite = "unit", tests = unit),
            Suite(suite = "uiTest", tests = ui),
            Suite(suite = "npm:playwright-snapshot-saver", tests = playwright),
        ).filter { it.tests.isNotEmpty() }

        val totals = Totals(
            passed = suites.sumOf { s -> s.tests.count { it.status == "passed" } },
            failed = suites.sumOf { s -> s.tests.count { it.status == "failed" } },
            skipped = suites.sumOf { s -> s.tests.count { it.status == "skipped" } },
            flaky = suites.sumOf { s -> s.tests.count { it.status == "flaky" } },
            durationMs = suites.sumOf { s -> s.tests.sumOf { it.durationMs } },
        )

        val summary = ClaudeSummary(
            version = 1,
            generatedAt = Instant.now().toString(),
            totals = totals,
            suites = suites,
        )

        val reportsDir = buildDir.resolve("reports").apply { mkdirs() }
        reportsDir.resolve("claude-summary.json")
            .writeText(json.encodeToString(JsonElement.serializer(), summary.toJsonElement()))
        reportsDir.resolve("claude-summary.md")
            .writeText(MarkdownEmitter.render(summary, gitSha))

        return summary
    }

    private fun ClaudeSummary.toJsonElement(): JsonElement = buildJsonObject {
        put("version", version)
        put("generatedAt", generatedAt)
        putJsonObject("totals") {
            put("passed", totals.passed)
            put("failed", totals.failed)
            put("skipped", totals.skipped)
            put("flaky", totals.flaky)
            put("durationMs", totals.durationMs)
        }
        putJsonArray("suites") {
            suites.forEach { suite ->
                addJsonObject {
                    put("suite", suite.suite)
                    putJsonArray("tests") {
                        suite.tests.forEach { test ->
                            addJsonObject {
                                put("name", test.name)
                                put("status", test.status)
                                put("durationMs", test.durationMs)
                                if (test.file != null) put("file", test.file) else put("file", JsonNull)
                                if (test.line != null) put("line", test.line) else put("line", JsonNull)
                                if (test.failureMessage != null) put("failureMessage", test.failureMessage) else put("failureMessage", JsonNull)
                                if (test.tracePath != null) put("tracePath", test.tracePath) else put("tracePath", JsonNull)
                            }
                        }
                    }
                }
            }
        }
    }
}
