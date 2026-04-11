package com.github.artem.pageobjectplugin.buildtools

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlaywrightJsonParserTest {

    private fun fixture(name: String): File {
        val url = javaClass.classLoader.getResource("fixtures/$name")
            ?: error("missing fixture: $name")
        return File(url.toURI())
    }

    @Test
    fun `maps Playwright statuses to passed, failed, and skipped`() {
        val entries = PlaywrightJsonParser.parse(fixture("playwright/results.json"))
        assertEquals(3, entries.size)

        val passed = entries.single { it.name.endsWith("logs in with valid creds") }
        assertEquals("passed", passed.status)
        assertEquals(1234L, passed.durationMs)
        assertEquals("tests/login.spec.ts", passed.file)
        assertEquals(12, passed.line)

        val failed = entries.single { it.name.endsWith("rejects empty password") }
        assertEquals("failed", failed.status)
        assertNotNull(failed.failureMessage)
        assertTrue(failed.failureMessage!!.contains("expected toast"))

        val skipped = entries.single { it.name.endsWith("is intentionally skipped") }
        assertEquals("skipped", skipped.status)
    }

    @Test
    fun `returns empty list when results file is missing`() {
        assertEquals(emptyList(), PlaywrightJsonParser.parse(File("/nonexistent/${System.nanoTime()}.json")))
    }
}
