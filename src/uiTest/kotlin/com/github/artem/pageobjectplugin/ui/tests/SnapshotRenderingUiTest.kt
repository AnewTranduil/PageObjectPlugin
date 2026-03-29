package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.fixtures.SnapshotBrowserFixture
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

    @BeforeEach
    fun loadSnapshot() {
        openFileInEditor("login.page.ts")
        Thread.sleep(1_000)
        if (!PageMirrorToolWindowFixture.isVisible(robot)) {
            openToolWindow()
        }
        // Wait for auto-discovery and snapshot load
        waitFor(Duration.ofSeconds(15)) {
            try {
                val tw = PageMirrorToolWindowFixture.find(robot)
                val name = tw.selectedSnapshotName()
                name.isNotBlank() && !name.contains("No snapshot")
            } catch (_: Exception) { false }
        }
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
     * (verified via window.__layoutData elements count from layout.json).
     *
     * The login/initial snapshot has 8 elements in layout.json.
     */
    @Test
    fun `layout data is populated in jcef after snapshot load`() {
        // Wait a bit for JCEF page to fully execute JS
        Thread.sleep(2_000)

        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        assertTrue(toolWindow.isBrowserVisible(), "JCEF component must be visible")

        // Query layout element count from the browser fixture
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)
        val count = browser.layoutElementCount()
        assertTrue(
            count >= 8,
            "layout.json should have 8 elements for login/initial snapshot, got: $count"
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
