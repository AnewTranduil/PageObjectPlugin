package com.github.artem.pageobjectplugin.integration

import com.github.artem.pageobjectplugin.fixtures.SnapshotFixtures
import com.github.artem.pageobjectplugin.listeners.RejectedBundle
import com.github.artem.pageobjectplugin.listeners.ScanResult
import com.github.artem.pageobjectplugin.model.SnapshotBundle
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import kotlin.io.path.writeText

class SnapshotServiceTest : BasePlatformTestCase() {

    private val capturedJs = mutableListOf<String>()
    private lateinit var service: SnapshotService

    override fun setUp() {
        super.setUp()
        service = SnapshotService.getInstance(project)
        service.jsExecutor = { code -> capturedJs.add(code) }
    }

    override fun tearDown() {
        service.resetStateForTesting()
        capturedJs.clear()
        super.tearDown()
    }

    fun `test loadSnapshot sets snapshotDocument`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        service.loadSnapshot(bundle)

        assertNotNull(service.snapshotDocument)
        assertEquals(1, service.snapshotDocument!!.select("[data-testid=login-username]").size)
    }

    fun `test loadSnapshot emits window loadSnapshot call`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        service.loadSnapshot(bundle)

        assertTrue(capturedJs.any { it.startsWith("window.loadSnapshot(") })
    }

    fun `test loadSnapshot sets currentBundle`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        service.loadSnapshot(bundle)

        assertEquals(bundle, service.currentBundle)
    }

    fun `test loadSnapshot notifies listeners`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        var listenerFired = false
        service.addSnapshotListener { listenerFired = true }

        service.loadSnapshot(bundle)

        assertTrue(listenerFired)
    }

    fun `test highlightElement emits correct JS`() {
        service.highlightElement("locator", "#username")

        assertTrue(capturedJs.any { it.startsWith("window.highlightElement(") && it.contains("locator") && it.contains("#username") })
    }

    fun `test clearHighlight emits correct JS`() {
        service.clearHighlight()

        assertEquals("window.clearHighlight();", capturedJs.last())
    }

    fun `test highlightAllLocators emits highlightAll with JSON array`() {
        val locators = listOf(
            com.github.artem.pageobjectplugin.locators.ExtractedLocator("getByTestId", "login-username", "[data-testid=\"login-username\"]"),
            com.github.artem.pageobjectplugin.locators.ExtractedLocator("getByRole", "button:Login", "[role=\"button\"]")
        )
        service.highlightAllLocators(locators)

        val js = capturedJs.last()
        assertTrue(js.startsWith("window.highlightAll("))
        assertTrue(js.contains("getByTestId"))
        assertTrue(js.contains("login-username"))
        assertTrue(js.contains("getByRole"))
        assertTrue(js.contains("button:Login"))
        assertTrue(service.isHighlightAllActive)
    }

    fun `test clearHighlight resets highlightAll state`() {
        service.isHighlightAllActive = true
        service.clearHighlight()

        assertFalse(service.isHighlightAllActive)
    }

    fun `test updateAvailableSnapshots with no current auto loads first bundle`() {
        val bundleA = SnapshotFixtures.createMinimalSnapshotDir()
        val bundleB = SnapshotFixtures.createMinimalSnapshotDir()

        service.updateAvailableSnapshots(listOf(bundleA, bundleB))

        assertEquals(bundleA, service.currentBundle)
        assertTrue(capturedJs.any { it.startsWith("window.loadSnapshot(") })
    }

    fun `test updateAvailableSnapshots with existing current does not auto load again`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        service.loadSnapshot(bundle)
        val loadCallsBefore = capturedJs.count { it.startsWith("window.loadSnapshot(") }

        service.updateAvailableSnapshots(listOf(bundle))

        val loadCallsAfter = capturedJs.count { it.startsWith("window.loadSnapshot(") }
        assertEquals(loadCallsBefore, loadCallsAfter)
    }

    fun `test loadSnapshot snapshotDocument is set before listeners fire`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        var documentAtNotification: org.jsoup.nodes.Document? = null
        service.addSnapshotListener { documentAtNotification = service.snapshotDocument }

        service.loadSnapshot(bundle)

        assertNotNull(documentAtNotification)
    }

    fun `test loadSnapshot inlines v2 sidecar CSS before rendering`() {
        // Hand-build a v2 bundle with an external stylesheet reference
        // and a matching resources/<sha1>.css sidecar, then load it
        // through SnapshotService and verify the JS that would be sent
        // to JCEF contains the inlined style text.
        val dir = Files.createTempDirectory("pm-v2-sidecar-")
        val resources = Files.createDirectory(dir.resolve("resources"))
        val css = ".login-button { background: tomato; }"
        resources.resolve("abc123.css").writeText(css)
        dir.resolve("index.html").writeText(
            """
            <!DOCTYPE html>
            <html><head><link rel="stylesheet" href="resources/abc123.css"></head>
            <body><button class="login-button">Login</button></body></html>
            """.trimIndent()
        )
        dir.resolve("manifest.json").writeText(
            """
            {
              "version": 2,
              "url": "about:blank",
              "viewport": { "width": 1280, "height": 720 },
              "timestamp": "2026-04-11T00:00:00Z"
            }
            """.trimIndent()
        )
        val bundle = SnapshotBundle.fromDirectory(dir)
        assertNotNull("fromDirectory should accept the v2 bundle", bundle)

        service.loadSnapshot(bundle!!)

        val loadCall = capturedJs.last { it.startsWith("window.loadSnapshot(") }
        assertTrue("sidecar CSS should be inlined: $loadCall", loadCall.contains("login-button"))
        assertTrue(loadCall.contains("background"))
        // The link tag's href should be gone from the rendered payload.
        assertFalse(
            "original <link> should have been replaced: $loadCall",
            loadCall.contains("resources/abc123.css"),
        )
    }

    fun `test fromDirectory rejects v1 bundles`() {
        val dir = Files.createTempDirectory("pm-v1-reject-")
        dir.resolve("index.html").writeText("<html><body/></html>")
        dir.resolve("manifest.json").writeText("""{"version": 1, "url": ""}""")

        val bundle = SnapshotBundle.fromDirectory(dir)

        assertNull("v1 bundles must be refused", bundle)
    }

    // --- Outdated-bundle banner ------------------------------------

    fun `test updateAvailableSnapshots emits showOutdatedBanner when rejections present`() {
        val v1Dir = Files.createTempDirectory("pm-banner-v1-")
        val scan = ScanResult(
            loaded = emptyList(),
            rejected = listOf(RejectedBundle(v1Dir, 1)),
        )

        service.updateAvailableSnapshots(scan)

        val bannerCall = capturedJs.lastOrNull { it.startsWith("window.showOutdatedBanner(") }
        assertNotNull(
            "expected window.showOutdatedBanner to be emitted, captured: $capturedJs",
            bannerCall,
        )
        assertTrue(
            "banner payload should include count:1: $bannerCall",
            bannerCall!!.contains("count:1"),
        )
        assertTrue(
            "banner payload should include versions:[1]: $bannerCall",
            bannerCall.contains("versions:[1]"),
        )
    }

    fun `test updateAvailableSnapshots hides banner when scan is clean`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        val scan = ScanResult(loaded = listOf(bundle), rejected = emptyList())

        service.updateAvailableSnapshots(scan)

        assertTrue(
            "expected window.hideOutdatedBanner(); in $capturedJs",
            capturedJs.any { it.trim() == "window.hideOutdatedBanner();" },
        )
    }

    fun `test updateAvailableSnapshots emits both load and banner when mixed`() {
        val v2Bundle = SnapshotFixtures.createMinimalSnapshotDir()
        val v1Dir = Files.createTempDirectory("pm-banner-mixed-v1-")
        val scan = ScanResult(
            loaded = listOf(v2Bundle),
            rejected = listOf(RejectedBundle(v1Dir, 1)),
        )

        service.updateAvailableSnapshots(scan)

        assertTrue(
            "v2 bundle should auto-load: $capturedJs",
            capturedJs.any { it.startsWith("window.loadSnapshot(") },
        )
        assertTrue(
            "banner should warn about v1: $capturedJs",
            capturedJs.any { it.startsWith("window.showOutdatedBanner(") && it.contains("versions:[1]") },
        )
    }

    fun `test updateAvailableSnapshots dedupes and sorts declared versions`() {
        val dirA = Files.createTempDirectory("pm-banner-v3a-")
        val dirB = Files.createTempDirectory("pm-banner-v3b-")
        val dirC = Files.createTempDirectory("pm-banner-v1-")
        val scan = ScanResult(
            loaded = emptyList(),
            rejected = listOf(
                RejectedBundle(dirA, 3),
                RejectedBundle(dirB, 3),
                RejectedBundle(dirC, 1),
            ),
        )

        service.updateAvailableSnapshots(scan)

        val bannerCall = capturedJs.last { it.startsWith("window.showOutdatedBanner(") }
        assertTrue(
            "expected count:3 (total rejected), versions:[1,3] (deduped/sorted): $bannerCall",
            bannerCall.contains("count:3") && bannerCall.contains("versions:[1,3]"),
        )
    }

    fun `test legacy list overload hides banner`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()

        service.updateAvailableSnapshots(listOf(bundle))

        assertTrue(
            "list overload should route through ScanResult and hide banner: $capturedJs",
            capturedJs.any { it.trim() == "window.hideOutdatedBanner();" },
        )
    }
}
