package com.github.artem.pageobjectplugin.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.writeText

/**
 * Pure JUnit 4 test (no IntelliJ fixtures) for the CSS sidecar
 * inliner. Runs in the regular `test` source set alongside integration
 * tests but does not need a sandboxed IDE.
 */
class SnapshotHtmlResolverTest {

    @Test
    fun `inlines a local resources sidecar`() {
        val dir = Files.createTempDirectory("resolver-test-")
        val resources = Files.createDirectory(dir.resolve("resources"))
        val css = "body { color: red; }"
        val sidecar = resources.resolve("abc123.css")
        sidecar.writeText(css)
        val html = """
            <!DOCTYPE html>
            <html><head><link rel="stylesheet" href="resources/abc123.css"></head>
            <body><p>hi</p></body></html>
        """.trimIndent()

        val out = SnapshotHtmlResolver.inlineLocalResources(html, resources)

        assertTrue("<style> element was not injected: $out", out.contains("<style>"))
        assertTrue("CSS body missing from output: $out", out.contains(css))
        assertFalse("original <link> still present: $out", out.contains("href=\"resources/abc123.css\""))
    }

    @Test
    fun `leaves absolute URL stylesheets untouched`() {
        val dir = Files.createTempDirectory("resolver-test-abs-")
        val resources = Files.createDirectory(dir.resolve("resources"))
        val html = "<html><head><link rel=\"stylesheet\" href=\"https://cdn.example/app.css\"></head><body></body></html>"

        val out = SnapshotHtmlResolver.inlineLocalResources(html, resources)

        assertTrue(out.contains("https://cdn.example/app.css"))
    }

    @Test
    fun `drops link tags whose sidecar is missing`() {
        val dir = Files.createTempDirectory("resolver-test-missing-")
        val resources = Files.createDirectory(dir.resolve("resources"))
        val html = "<html><head><link rel=\"stylesheet\" href=\"resources/gone.css\"></head><body></body></html>"

        val out = SnapshotHtmlResolver.inlineLocalResources(html, resources)

        assertFalse("dead <link> still present: $out", out.contains("resources/gone.css"))
        assertFalse("phantom <style> injected", out.contains("<style"))
    }

    @Test
    fun `rejects path traversal in href`() {
        val dir = Files.createTempDirectory("resolver-test-traversal-")
        val resources = Files.createDirectory(dir.resolve("resources"))
        val outside = dir.resolve("secret.css")
        outside.writeText("body { color: secret; }")
        val html = "<html><head><link rel=\"stylesheet\" href=\"resources/../secret.css\"></head><body></body></html>"

        val out = SnapshotHtmlResolver.inlineLocalResources(html, resources)

        // Traversal path should NOT load `secret.css`; sidecar-locator returns null and the <link> stays.
        assertTrue("href should be preserved: $out", out.contains("resources/../secret.css"))
        assertFalse("secret CSS was read despite traversal attempt", out.contains("color: secret"))
    }

    @Test
    fun `passes through html with no link tags`() {
        val dir = Files.createTempDirectory("resolver-test-none-")
        val resources = Files.createDirectory(dir.resolve("resources"))
        val html = "<html><body><p>plain</p></body></html>"

        val out = SnapshotHtmlResolver.inlineLocalResources(html, resources)

        assertEquals(html, out)
    }

    @Test
    fun `loadResolved returns raw html when resourcesDir is null`() {
        val dir = Files.createTempDirectory("resolver-test-null-")
        val htmlPath = dir.resolve("index.html")
        val html = "<html><head><link rel=\"stylesheet\" href=\"resources/abc.css\"></head><body></body></html>"
        htmlPath.writeText(html)

        val out = SnapshotHtmlResolver.loadResolved(htmlPath, null)

        assertEquals(html, out)
    }

    @Test
    fun `preserves descendant selectors containing gt in CSS`() {
        // Regression: using Jsoup's Element.text() would HTML-escape the
        // `>` in `.parent > .child`, breaking the CSS. The resolver must
        // use a DataNode so `<`/`>` survive serialization.
        val dir = Files.createTempDirectory("resolver-test-gt-")
        val resources = Files.createDirectory(dir.resolve("resources"))
        val css = ".parent > .child { color: red; }"
        resources.resolve("gt.css").writeText(css)
        val html = "<html><head><link rel=\"stylesheet\" href=\"resources/gt.css\"></head><body></body></html>"

        val out = SnapshotHtmlResolver.inlineLocalResources(html, resources)

        assertTrue("`>` should NOT be HTML-escaped: $out", out.contains(".parent > .child"))
        assertFalse(out.contains("&gt;"))
    }

    @Test
    fun `inlines multiple sidecars in one pass`() {
        val dir = Files.createTempDirectory("resolver-test-multi-")
        val resources = Files.createDirectory(dir.resolve("resources"))
        resources.resolve("a.css").writeText("a{}")
        resources.resolve("b.css").writeText("b{}")
        val html = """
            <html><head>
              <link rel="stylesheet" href="resources/a.css">
              <link rel="stylesheet" href="resources/b.css">
            </head><body></body></html>
        """.trimIndent()

        val out = SnapshotHtmlResolver.inlineLocalResources(html, resources)

        assertTrue("a sidecar not inlined: $out", out.contains("a{}"))
        assertTrue("b sidecar not inlined: $out", out.contains("b{}"))
        assertEquals("expected two <style> tags", 2, Regex("<style").findAll(out).count())
        assertFalse(out.contains("href=\"resources/a.css\""))
        assertFalse(out.contains("href=\"resources/b.css\""))
    }
}
