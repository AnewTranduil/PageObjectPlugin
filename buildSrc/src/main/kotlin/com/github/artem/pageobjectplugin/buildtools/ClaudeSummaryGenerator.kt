package com.github.artem.pageobjectplugin.buildtools

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
 */
object ClaudeSummaryGenerator {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    fun run(rootDir: File, buildDir: File, gitSha: String? = null): ClaudeSummary {
        val unit = JUnitXmlParser.parse(buildDir.resolve("test-results/test"))
            .map { it.copy() }
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
        reportsDir.resolve("claude-summary.json").writeText(json.encodeToString(summary))
        reportsDir.resolve("claude-summary.md").writeText(MarkdownEmitter.render(summary, gitSha))

        return summary
    }
}
