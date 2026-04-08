package com.github.artem.pageobjectplugin.buildtools

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClaudeSummaryGeneratorTest {

    private fun fixture(name: String): File {
        val url = javaClass.classLoader.getResource("fixtures/$name")
            ?: error("missing fixture: $name")
        return File(url.toURI())
    }

    /**
     * End-to-end: lay out a fake `rootDir` + `buildDir` containing all
     * three test surfaces, run the generator, and assert the JSON totals
     * + Markdown layout match the documented schema. This is the test
     * that fulfills Task 14's "intentionally failing test shows up in
     * Markdown" verification step from `docs/tasks/task-14-ci-test-reporting.md`.
     */
    @Test
    fun `aggregates unit, ui, and playwright suites into JSON and Markdown`(@TempDir tempDir: File) {
        val rootDir = tempDir.resolve("root").apply { mkdirs() }
        val buildDir = tempDir.resolve("root/build").apply { mkdirs() }

        // Lay out unit test XML
        val unitXmlDir = buildDir.resolve("test-results/test").apply { mkdirs() }
        fixture("junit-unit").listFiles()!!.forEach { it.copyTo(unitXmlDir.resolve(it.name)) }

        // Lay out uiTest XML + matching trace bundle
        val uiXmlDir = buildDir.resolve("test-results/uiTest").apply { mkdirs() }
        fixture("junit-ui").listFiles()!!.forEach { it.copyTo(uiXmlDir.resolve(it.name)) }
        val tracesDir = buildDir.resolve("reports/uiTest/traces").apply { mkdirs() }
        fixture("uiTraces").listFiles()!!.forEach { src ->
            val dst = tracesDir.resolve(src.name).apply { mkdirs() }
            src.listFiles()!!.forEach { it.copyTo(dst.resolve(it.name)) }
        }

        // Lay out Playwright JSON
        val pwDir = rootDir.resolve("packages/playwright-snapshot-saver/test-results").apply { mkdirs() }
        fixture("playwright/results.json").copyTo(pwDir.resolve("results.json"))

        val summary = ClaudeSummaryGenerator.run(rootDir, buildDir, gitSha = "abcdef1234567890")

        // Three suites present (unit, uiTest, playwright)
        assertEquals(3, summary.suites.size)

        // Totals: 1 unit pass + 1 unit skip + 1 unit fail
        //       + 1 ui pass + 1 ui fail (the trace says flaky=true, but augmenter
        //         only flips status to "flaky" when the JUnit verdict was "passed",
        //         so a flaky-then-fail stays "failed")
        //       + 1 pw pass + 1 pw fail + 1 pw skip
        // = 3 passed, 3 failed, 2 skipped, 0 flaky
        assertEquals(3, summary.totals.passed)
        assertEquals(3, summary.totals.failed)
        assertEquals(2, summary.totals.skipped)
        assertEquals(0, summary.totals.flaky)

        // The uiTest entry should have its tracePath populated by the augmenter.
        val ui = summary.suites.single { it.suite == "uiTest" }
        val clickRetried = ui.tests.single { it.name.endsWith(".clickRetried") }
        assertNotNull(clickRetried.tracePath)
        assertTrue(clickRetried.tracePath!!.contains("BarUiTest__clickRetried"))

        // The generator wrote both summary files
        val mdFile = buildDir.resolve("reports/claude-summary.md")
        val jsonFile = buildDir.resolve("reports/claude-summary.json")
        assertTrue(mdFile.isFile, "claude-summary.md should exist")
        assertTrue(jsonFile.isFile, "claude-summary.json should exist")

        val md = mdFile.readText()
        // Header + sha
        assertTrue(md.contains("Test Summary — abcdef123456"), "header missing sha")
        // Failures section lists every failing test with file:line.
        assertTrue(md.contains("## Failures"))
        assertTrue(md.contains("FooTest.kt:42"), "missing unit failure file:line: $md")
        assertTrue(md.contains("BarUiTest.kt:88"), "missing ui failure file:line: $md")
        assertTrue(
            md.contains("rejects empty password"),
            "missing playwright failure title: $md",
        )
        // Trace path is rendered for the ui failure.
        assertTrue(md.contains("Trace: build/reports/uiTest/traces/BarUiTest__clickRetried/"), md)
        // Suites table is present.
        assertTrue(md.contains("## Suites"))
        assertTrue(md.contains("**unit**"))
        assertTrue(md.contains("**uiTest**"))
        assertTrue(md.contains("**npm:playwright-snapshot-saver**"))
    }

    @Test
    fun `intentionally failing unit test surfaces in markdown failures section`(@TempDir tempDir: File) {
        // Reduced fixture: only the unit XML, isolating the "failing test surfaces with file:line"
        // requirement from the task verification step.
        val rootDir = tempDir.resolve("root").apply { mkdirs() }
        val buildDir = tempDir.resolve("root/build").apply { mkdirs() }
        val unitXmlDir = buildDir.resolve("test-results/test").apply { mkdirs() }
        fixture("junit-unit").listFiles()!!.forEach { it.copyTo(unitXmlDir.resolve(it.name)) }

        ClaudeSummaryGenerator.run(rootDir, buildDir)
        val md = buildDir.resolve("reports/claude-summary.md").readText()

        assertTrue(md.contains("## Failures (1)"))
        assertTrue(md.contains("`com.example.FooTest.failsWithStack`"))
        assertTrue(md.contains("FooTest.kt:42"))
    }
}
