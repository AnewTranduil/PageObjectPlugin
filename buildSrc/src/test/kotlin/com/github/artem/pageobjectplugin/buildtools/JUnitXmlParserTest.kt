package com.github.artem.pageobjectplugin.buildtools

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JUnitXmlParserTest {

    private fun fixture(name: String): File {
        // resources are copied to test runtime classpath; resolve via classloader.
        val url = javaClass.classLoader.getResource("fixtures/$name")
            ?: error("missing fixture: $name")
        return File(url.toURI())
    }

    @Test
    fun `parses passed, failed, and skipped from a JUnit4 testsuite`() {
        val entries = JUnitXmlParser.parse(fixture("junit-unit"))
        assertEquals(3, entries.size)

        val passed = entries.single { it.name.endsWith(".passes") }
        assertEquals("passed", passed.status)
        assertEquals(123L, passed.durationMs)
        assertNull(passed.failureMessage)

        val failed = entries.single { it.name.endsWith(".failsWithStack") }
        assertEquals("failed", failed.status)
        assertEquals("FooTest.kt", failed.file)
        assertEquals(42, failed.line)
        assertNotNull(failed.failureMessage)
        assertTrue(failed.failureMessage!!.contains("expected"))

        val skipped = entries.single { it.name.endsWith(".isSkipped") }
        assertEquals("skipped", skipped.status)
    }

    @Test
    fun `returns empty list when directory does not exist`() {
        assertEquals(emptyList(), JUnitXmlParser.parse(File("/nonexistent/${System.nanoTime()}")))
    }
}
