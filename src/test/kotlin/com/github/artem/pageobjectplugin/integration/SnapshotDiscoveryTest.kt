package com.github.artem.pageobjectplugin.integration

import com.github.artem.pageobjectplugin.listeners.SnapshotDiscoveryListener
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

class SnapshotDiscoveryTest : BasePlatformTestCase() {

    // --- extractPageName unit tests ---

    fun `test default pattern extracts page name from login page ts`() {
        val result = SnapshotDiscoveryListener.extractPageName("login.page.ts", "(.+)\\.page\\.ts")
        assertEquals("login", result)
    }

    fun `test default pattern extracts page name from dashboard page ts`() {
        val result = SnapshotDiscoveryListener.extractPageName("dashboard.page.ts", "(.+)\\.page\\.ts")
        assertEquals("dashboard", result)
    }

    fun `test PascalCase pattern extracts page name`() {
        val result = SnapshotDiscoveryListener.extractPageName("LoginPage.ts", "(.+)Page\\.ts")
        assertEquals("Login", result)
    }

    fun `test po pattern extracts page name`() {
        val result = SnapshotDiscoveryListener.extractPageName("login.po.ts", "(.+)\\.po\\.ts")
        assertEquals("login", result)
    }

    fun `test non-matching file returns null`() {
        val result = SnapshotDiscoveryListener.extractPageName("helpers.ts", "(.+)\\.page\\.ts")
        assertNull(result)
    }

    fun `test spec file returns null`() {
        val result = SnapshotDiscoveryListener.extractPageName("login.spec.ts", "(.+)\\.page\\.ts")
        assertNull(result)
    }

    fun `test invalid regex returns null`() {
        val result = SnapshotDiscoveryListener.extractPageName("login.page.ts", "[invalid")
        assertNull(result)
    }

    fun `test pattern without capture group returns null`() {
        val result = SnapshotDiscoveryListener.extractPageName("login.page.ts", ".*\\.page\\.ts")
        assertNull(result)
    }

    fun `test empty filename returns null`() {
        val result = SnapshotDiscoveryListener.extractPageName("", "(.+)\\.page\\.ts")
        assertNull(result)
    }

    // --- scanForBundles integration tests ---

    fun `test scanForBundles finds bundles in group directory`() {
        val root = Files.createTempDirectory("pm-discovery-test-")
        try {
            val initialDir = root.resolve("initial")
            Files.createDirectories(initialDir)
            Files.writeString(initialDir.resolve("index.html"), "<html><body>Initial</body></html>")

            val errorDir = root.resolve("error-state")
            Files.createDirectories(errorDir)
            Files.writeString(errorDir.resolve("index.html"), "<html><body>Error</body></html>")

            val bundles = SnapshotDiscoveryListener.scanForBundles(root, 3)
            assertEquals(2, bundles.size)

            val names = bundles.map { it.name }.toSet()
            assertTrue(names.contains("initial"))
            assertTrue(names.contains("error-state"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    fun `test scanForBundles returns empty for non-existent directory`() {
        val nonExistent = Files.createTempDirectory("pm-discovery-test-").resolve("does-not-exist")
        val bundles = SnapshotDiscoveryListener.scanForBundles(nonExistent, 3)
        assertTrue(bundles.isEmpty())
    }

    fun `test scanForBundles skips directories without index html`() {
        val root = Files.createTempDirectory("pm-discovery-test-")
        try {
            val noIndexDir = root.resolve("incomplete")
            Files.createDirectories(noIndexDir)
            Files.writeString(noIndexDir.resolve("screenshot.webp"), "fake")

            val validDir = root.resolve("valid")
            Files.createDirectories(validDir)
            Files.writeString(validDir.resolve("index.html"), "<html><body>Valid</body></html>")

            val bundles = SnapshotDiscoveryListener.scanForBundles(root, 3)
            assertEquals(1, bundles.size)
            assertEquals("valid", bundles.first().name)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    fun `test scanForBundles respects max depth`() {
        val root = Files.createTempDirectory("pm-discovery-test-")
        try {
            // depth 0: root itself (no index.html)
            // depth 1: group/
            val deepDir = root.resolve("level1").resolve("level2")
            Files.createDirectories(deepDir)
            Files.writeString(deepDir.resolve("index.html"), "<html><body>Deep</body></html>")

            // maxDepth=1 should not reach level2
            val shallow = SnapshotDiscoveryListener.scanForBundles(root, 1)
            assertTrue(shallow.isEmpty())

            // maxDepth=2 should find it
            val deep = SnapshotDiscoveryListener.scanForBundles(root, 2)
            assertEquals(1, deep.size)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    // --- scanBundles: rejected-bundle propagation ---

    fun `test scanBundles classifies v1 directories as rejected`() {
        val root = Files.createTempDirectory("pm-scan-rejected-")
        try {
            val v1Dir = root.resolve("initial")
            Files.createDirectories(v1Dir)
            Files.writeString(v1Dir.resolve("index.html"), "<html/>")
            Files.writeString(v1Dir.resolve("manifest.json"), """{"version": 1, "url": ""}""")

            val result = SnapshotDiscoveryListener.scanBundles(root, 3)

            assertTrue("no v2 bundles should load", result.loaded.isEmpty())
            assertEquals(1, result.rejected.size)
            val rejected = result.rejected.single()
            assertEquals(v1Dir, rejected.dir)
            assertEquals(1, rejected.declaredVersion)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    fun `test scanBundles returns both loaded and rejected when mixed`() {
        val root = Files.createTempDirectory("pm-scan-mixed-")
        try {
            val v2Dir = root.resolve("initial")
            Files.createDirectories(v2Dir)
            Files.writeString(v2Dir.resolve("index.html"), "<html/>")
            Files.writeString(v2Dir.resolve("manifest.json"), V2_MANIFEST)

            val v1Dir = root.resolve("error-state")
            Files.createDirectories(v1Dir)
            Files.writeString(v1Dir.resolve("index.html"), "<html/>")
            Files.writeString(v1Dir.resolve("manifest.json"), """{"version": 1}""")

            val result = SnapshotDiscoveryListener.scanBundles(root, 3)

            assertEquals(1, result.loaded.size)
            assertEquals("initial", result.loaded.single().name)
            assertEquals(1, result.rejected.size)
            assertEquals(v1Dir, result.rejected.single().dir)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    fun `test scanBundles all v2 returns empty rejected list`() {
        val root = Files.createTempDirectory("pm-scan-v2-only-")
        try {
            val dir = root.resolve("initial")
            Files.createDirectories(dir)
            Files.writeString(dir.resolve("index.html"), "<html/>")
            Files.writeString(dir.resolve("manifest.json"), V2_MANIFEST)

            val result = SnapshotDiscoveryListener.scanBundles(root, 3)

            assertEquals(1, result.loaded.size)
            assertTrue(result.rejected.isEmpty())
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    fun `test scanBundles on missing dir returns fully empty result`() {
        val nonExistent = Files.createTempDirectory("pm-scan-missing-")
            .resolve("does-not-exist")

        val result = SnapshotDiscoveryListener.scanBundles(nonExistent, 3)

        assertTrue(result.loaded.isEmpty())
        assertTrue(result.rejected.isEmpty())
    }

    fun `test scanForBundles hides rejected bundles for backwards compatibility`() {
        val root = Files.createTempDirectory("pm-scan-compat-")
        try {
            val v1Dir = root.resolve("initial")
            Files.createDirectories(v1Dir)
            Files.writeString(v1Dir.resolve("index.html"), "<html/>")
            Files.writeString(v1Dir.resolve("manifest.json"), """{"version": 1}""")

            val bundles = SnapshotDiscoveryListener.scanForBundles(root, 3)

            assertTrue(
                "legacy scanForBundles must keep returning only loaded bundles",
                bundles.isEmpty(),
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    companion object {
        private val V2_MANIFEST = """
            {
              "version": 2,
              "url": "about:blank",
              "viewport": { "width": 1280, "height": 720 },
              "timestamp": "2026-04-16T00:00:00Z"
            }
        """.trimIndent()
    }
}
