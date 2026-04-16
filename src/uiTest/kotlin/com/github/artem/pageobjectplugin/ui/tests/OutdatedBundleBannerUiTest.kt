package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.annotations.Feature
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * UI tests: outdated-bundle banner.
 *
 * The banner is the in-tool-window signal that the snapshot scanner
 * found directories on disk that _look_ like snapshot bundles but
 * declare an unsupported `manifest.version`. These tests verify the
 * end-to-end wiring that drives the banner:
 *
 *   scanBundles classifies v1 → rejected
 *     → SnapshotService.updateAvailableSnapshots(ScanResult(..., rejected=[…]))
 *     → jsExecutor emits window.showOutdatedBanner
 *     → isOutdatedBannerActive flips to true
 *
 * We drive the service via the
 * [com.github.artem.pageobjectplugin.services.SnapshotService.simulateRejectedBundlesForTesting]
 * seam rather than seeding v1 bundles on the test project's disk —
 * that isolates this test from the filesystem state other UI tests
 * rely on and keeps it fast and deterministic.
 */
@Feature("outdated-bundle-banner")
class OutdatedBundleBannerUiTest : BaseUiTest() {

    @AfterEach
    fun resetBanner() {
        // Keep the following test from inheriting banner-visible state
        // — the SnapshotService is project-scoped and the sandbox IDE
        // reuses the project across tests.
        simulateCleanScan()
    }

    /**
     * Core assertion: a scan that found one v1 bundle routes through
     * the service and flips the banner flag on. This is the canonical
     * "bundle version is not expected → banner is shown" test.
     */
    @Test
    fun `v1 rejected bundle shows banner`() {
        takeScreenshot("banner-before")

        simulateRejectedBundles(listOf(1))

        takeScreenshot("banner-after-show")

        assertTrue(
            isBannerActive(),
            "isOutdatedBannerActive should be true after scan with a v1 rejection",
        )
    }

    /**
     * Clean scan after a dirty scan clears the banner. Verifies the
     * "signal, not nag" property — the banner is a pure function of
     * the latest scan, not a sticky modal.
     */
    @Test
    fun `clean scan hides the banner`() {
        simulateRejectedBundles(listOf(1))
        assertTrue(isBannerActive(), "setup precondition failed")

        simulateCleanScan()

        takeScreenshot("banner-hidden")

        assertFalse(
            isBannerActive(),
            "isOutdatedBannerActive should be false after a clean scan",
        )
    }

    /**
     * Multiple declared versions deduplicate. Any mix of rejected
     * versions should still surface the banner.
     */
    @Test
    fun `mixed declared versions keep the banner visible`() {
        simulateRejectedBundles(listOf(1, 3, 3))

        takeScreenshot("banner-mixed-versions")

        assertTrue(
            isBannerActive(),
            "isOutdatedBannerActive should be true when multiple unsupported versions exist",
        )
    }

    // --- Service bridge ---------------------------------------------

    private val getServiceJs = """
        var __pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.artem.pageobjectplugin")
        var __plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(__pluginId)
        var __cl = __plugin.getPluginClassLoader()
        var __svcClass = __cl.loadClass("com.github.artem.pageobjectplugin.services.SnapshotService")
        var __project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
        var __service = __project.getService(__svcClass)
    """.trimIndent()

    private fun simulateRejectedBundles(versions: List<Int>) {
        // Build the list element-by-element so we don't rely on the
        // IDE's JS engine coercing a JS array literal to `Object[]` for
        // Arrays.asList — that conversion is fragile across Nashorn /
        // GraalJS.
        val adds = versions.joinToString("\n") {
            "__versions.add(java.lang.Integer.valueOf($it))"
        }
        ideFrame().callJs<Boolean>(
            """
            $getServiceJs
            var __versions = new java.util.ArrayList()
            $adds
            __service.simulateRejectedBundlesForTesting(__versions)
            true
            """,
            runInEdt = true,
        )
    }

    private fun simulateCleanScan() {
        ideFrame().callJs<Boolean>(
            """
            $getServiceJs
            __service.simulateCleanScanForTesting()
            true
            """,
            runInEdt = true,
        )
    }

    private fun isBannerActive(): Boolean =
        ideFrame().callJs<Boolean>(
            """
            $getServiceJs
            new java.lang.Boolean(__service.isOutdatedBannerActive())
            """,
            runInEdt = true,
        )
}
