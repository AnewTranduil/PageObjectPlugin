package com.github.artem.pageobjectplugin.model

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Unit tests for the [SnapshotBundle.load] classification, the sealed
 * [BundleLoadResult] hierarchy, and the legacy-compatible
 * [SnapshotBundle.fromDirectory] convenience.
 *
 * These tests are the canonical guardrail for the v1 bundle refusal:
 * any regression that drops the [BundleLoadResult.UnsupportedVersion]
 * signal will break the Page Mirror tool window's outdated-bundle
 * banner.
 */
class SnapshotBundleTest : BasePlatformTestCase() {

    private lateinit var tmpRoot: Path

    override fun setUp() {
        super.setUp()
        tmpRoot = Files.createTempDirectory("pm-bundle-load-")
    }

    override fun tearDown() {
        try {
            tmpRoot.toFile().deleteRecursively()
        } finally {
            super.tearDown()
        }
    }

    fun `test load returns Loaded for v2 bundle`() {
        val dir = tmpRoot.resolve("initial")
        Files.createDirectories(dir)
        dir.resolve("index.html").writeText("<html><body/></html>")
        dir.resolve("manifest.json").writeText(MANIFEST_V2)

        val result = SnapshotBundle.load(dir)

        assertTrue("expected Loaded, was $result", result is BundleLoadResult.Loaded)
        val loaded = (result as BundleLoadResult.Loaded).bundle
        assertEquals("initial", loaded.name)
        assertEquals(dir.resolve("index.html"), loaded.htmlPath)
    }

    fun `test load returns UnsupportedVersion for v1 manifest`() {
        val dir = tmpRoot.resolve("initial")
        Files.createDirectories(dir)
        dir.resolve("index.html").writeText("<html><body/></html>")
        dir.resolve("manifest.json").writeText("""{"version": 1, "url": ""}""")

        val result = SnapshotBundle.load(dir)

        assertTrue(
            "expected UnsupportedVersion, was $result",
            result is BundleLoadResult.UnsupportedVersion,
        )
        val rejection = result as BundleLoadResult.UnsupportedVersion
        assertEquals(dir, rejection.dir)
        assertEquals(1, rejection.declared)
    }

    fun `test load returns UnsupportedVersion preserving the declared integer`() {
        val dir = tmpRoot.resolve("weird")
        Files.createDirectories(dir)
        dir.resolve("index.html").writeText("<html/>")
        dir.resolve("manifest.json").writeText("""{"version": 7}""")

        val result = SnapshotBundle.load(dir)

        assertTrue(result is BundleLoadResult.UnsupportedVersion)
        assertEquals(7, (result as BundleLoadResult.UnsupportedVersion).declared)
    }

    fun `test load returns Empty for missing directory`() {
        val absent = tmpRoot.resolve("nope")

        assertEquals(BundleLoadResult.Empty, SnapshotBundle.load(absent))
    }

    fun `test load returns Empty when index html is missing`() {
        val dir = tmpRoot.resolve("no-html")
        Files.createDirectories(dir)
        dir.resolve("manifest.json").writeText(MANIFEST_V2)

        assertEquals(BundleLoadResult.Empty, SnapshotBundle.load(dir))
    }

    fun `test load is permissive when manifest is absent`() {
        val dir = tmpRoot.resolve("no-manifest")
        Files.createDirectories(dir)
        dir.resolve("index.html").writeText("<html/>")

        val result = SnapshotBundle.load(dir)

        assertTrue(
            "absent manifest should NOT be treated as an unsupported version",
            result is BundleLoadResult.Loaded,
        )
    }

    fun `test load is permissive when manifest has no version field`() {
        val dir = tmpRoot.resolve("no-version")
        Files.createDirectories(dir)
        dir.resolve("index.html").writeText("<html/>")
        dir.resolve("manifest.json").writeText("""{"url": "about:blank"}""")

        val result = SnapshotBundle.load(dir)

        assertTrue(result is BundleLoadResult.Loaded)
    }

    fun `test fromDirectory returns null for v1 bundles`() {
        val dir = tmpRoot.resolve("initial")
        Files.createDirectories(dir)
        dir.resolve("index.html").writeText("<html/>")
        dir.resolve("manifest.json").writeText("""{"version": 1}""")

        assertNull(SnapshotBundle.fromDirectory(dir))
    }

    fun `test fromDirectory returns bundle for v2`() {
        val dir = tmpRoot.resolve("initial")
        Files.createDirectories(dir)
        dir.resolve("index.html").writeText("<html/>")
        dir.resolve("manifest.json").writeText(MANIFEST_V2)

        val bundle = SnapshotBundle.fromDirectory(dir)

        assertNotNull(bundle)
        assertEquals("initial", bundle!!.name)
    }

    fun `test load resolves resources screenshot when present`() {
        val dir = tmpRoot.resolve("with-screenshot")
        Files.createDirectories(dir.resolve("resources"))
        dir.resolve("index.html").writeText("<html/>")
        dir.resolve("manifest.json").writeText(MANIFEST_V2)
        dir.resolve("resources/screenshot.png").writeText("fake")

        val result = SnapshotBundle.load(dir)

        assertTrue(result is BundleLoadResult.Loaded)
        val bundle = (result as BundleLoadResult.Loaded).bundle
        assertEquals(dir.resolve("resources/screenshot.png"), bundle.screenshotPath)
    }

    companion object {
        private val MANIFEST_V2 = """
            {
              "version": 2,
              "url": "about:blank",
              "viewport": { "width": 1280, "height": 720 },
              "timestamp": "2026-04-16T00:00:00Z"
            }
        """.trimIndent()
    }
}
