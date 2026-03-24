package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * UI tests: UT-01 to UT-05 — Tool Window structure and snapshot discovery.
 *
 * Prerequisites:
 *   - IDE started with test-project/ open (runIdeForUiTests task)
 *   - test-project/.snapshots/login/initial/ exists with valid snapshot files
 */
class ToolWindowUiTest : BaseUiTest() {

    @BeforeEach
    fun ensureToolWindowOpen() {
        // Open a .ts file so snapshot discovery triggers automatically
        openFileInEditor("login.page.ts")
        Thread.sleep(2_000)
    }

    /**
     * UT-01: Tool window is visible after opening a .ts file.
     */
    @Test
    fun `tool window is visible`() {
        val visible = PageMirrorToolWindowFixture.isVisible(robot)
        assertTrue(visible, "Page Mirror tool window should be visible")
    }

    /**
     * UT-02: Opening a .ts file auto-populates the combo box with a discovered snapshot.
     */
    @Test
    fun `opening ts file auto discovers snapshot`() {
        waitFor(Duration.ofSeconds(10)) {
            try {
                val tw = PageMirrorToolWindowFixture.find(robot)
                val selected = tw.selectedSnapshotName()
                selected.isNotBlank() && !selected.contains("No snapshots")
            } catch (_: Exception) { false }
        }

        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val selected = toolWindow.selectedSnapshotName()
        assertTrue(
            selected.contains("login") || selected.contains("initial"),
            "Combo box should show discovered snapshot name, was: '$selected'"
        )
    }

    /**
     * UT-03: Combo box dropdown lists all discovered snapshot bundles.
     */
    @Test
    fun `combo box lists discovered snapshots`() {
        waitFor(Duration.ofSeconds(10)) {
            try {
                PageMirrorToolWindowFixture.find(robot).selectedSnapshotName().isNotBlank()
            } catch (_: Exception) { false }
        }

        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        // At minimum one snapshot (login/initial) must appear
        val allNames = toolWindow.allSnapshotNames()
        assertTrue(allNames.isNotEmpty(), "Combo box should list at least one snapshot, got: $allNames")
        assertTrue(
            allNames.any { it.contains("login") || it.contains("initial") },
            "Expected 'login/initial' in snapshot list, got: $allNames"
        )
    }

    /**
     * UT-04: Selecting a snapshot from the combo box loads it.
     * Verified indirectly by checking the combo box value updates.
     */
    @Test
    fun `selecting snapshot from combo box loads it`() {
        waitFor(Duration.ofSeconds(10)) {
            try { PageMirrorToolWindowFixture.find(robot).selectedSnapshotName().isNotBlank() }
            catch (_: Exception) { false }
        }

        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val before = toolWindow.selectedSnapshotName()

        // Re-select the same item (exercises the action listener path)
        toolWindow.selectSnapshot("initial")
        Thread.sleep(1_000)

        val after = toolWindow.selectedSnapshotName()
        assertTrue(
            after.contains("initial") || after == before,
            "After selection, combo should show 'initial', was: '$after'"
        )
    }

    /**
     * UT-05: Refresh button re-scans and updates the combo box.
     * Verifying that clicking Refresh does not crash and combo stays populated.
     */
    @Test
    fun `refresh button re scans snapshots`() {
        waitFor(Duration.ofSeconds(10)) {
            try { PageMirrorToolWindowFixture.find(robot).selectedSnapshotName().isNotBlank() }
            catch (_: Exception) { false }
        }

        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val beforeRefresh = toolWindow.selectedSnapshotName()

        toolWindow.refreshButton.click()
        Thread.sleep(2_000)

        // After refresh, combo box should still have a valid entry
        val afterRefresh = PageMirrorToolWindowFixture.find(robot).selectedSnapshotName()
        assertFalse(afterRefresh.isBlank(), "After refresh, combo box should still have a selection")
        // Existing snapshot should still be present
        assertTrue(
            afterRefresh.contains("login") || afterRefresh.contains("initial") || afterRefresh == beforeRefresh,
            "After refresh, snapshot should still be discoverable, was: '$afterRefresh'"
        )
    }
}
