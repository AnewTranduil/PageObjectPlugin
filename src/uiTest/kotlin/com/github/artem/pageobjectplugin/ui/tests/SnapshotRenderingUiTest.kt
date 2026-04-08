package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.fixtures.SnapshotBrowserFixture
import com.github.artem.pageobjectplugin.ui.pages.EditorPage
import com.github.artem.pageobjectplugin.ui.pages.PluginToolWindowPage
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * UI tests: UT-06 to UT-08 — Snapshot HTML rendering in the JCEF iframe.
 *
 * Prerequisites:
 *   - IDE started with test-project/ open (runIdeForUiTests task)
 *   - test-project/.snapshots/login/initial/ contains a valid snapshot bundle
 */
class SnapshotRenderingUiTest : BaseUiTest() {

    private val editor by lazy { EditorPage(robot) }
    private val toolWindow by lazy { PluginToolWindowPage(robot) }

    @BeforeEach
    fun loadSnapshot() {
        editor.openFileInEditor("login.page.ts")
        if (!PageMirrorToolWindowFixture.isVisible(robot)) {
            toolWindow.open()
        }
        toolWindow.waitForSnapshotDiscovery(Duration.ofSeconds(15))
    }

    /**
     * UT-06: The JCEF browser component is present and visible when a snapshot is loaded.
     */
    @Test
    fun `jcef browser component is visible after snapshot load`() {
        takeScreenshot("after-snapshot-load")
        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        assertTrue(
            toolWindow.isBrowserVisible(),
            "JCEF browser component should be visible after snapshot load"
        )
    }

    /**
     * UT-07: The iframe rendered inside JCEF contains the snapshot HTML
     * (verified by checking a snapshot bundle is loaded in the service).
     */
    @Test
    fun `snapshot is loaded in service after snapshot load`() {
        // Wait a bit for JCEF page to fully execute JS
        Thread.sleep(2_000)

        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        assertTrue(toolWindow.isBrowserVisible(), "JCEF component must be visible")

        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)
        assertTrue(
            browser.isSnapshotLoaded(),
            "Snapshot bundle should be loaded in SnapshotService after snapshot load"
        )
    }

    /**
     * UT-08: The JCEF browser is still visible after the tool window snapshot selection is
     * changed back to the same item (simulating a reload / re-render cycle).
     */
    @Test
    fun `snapshot reload refreshes browser content without crash`() {
        val toolWindow = PageMirrorToolWindowFixture.find(robot)

        // Re-select the currently loaded snapshot to trigger a reload
        val currentName = toolWindow.selectedSnapshotName()
        if (currentName.isNotBlank()) {
            toolWindow.selectSnapshot(currentName.substringAfterLast("/").trim())
        }

        Thread.sleep(2_000)

        assertTrue(
            PageMirrorToolWindowFixture.find(robot).isBrowserVisible(),
            "JCEF browser should still be visible after snapshot reload"
        )
    }
}
