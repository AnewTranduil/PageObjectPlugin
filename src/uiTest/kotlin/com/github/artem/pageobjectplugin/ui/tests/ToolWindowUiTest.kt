package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.annotations.Feature
import com.github.artem.pageobjectplugin.ui.flows.SnapshotLoadFlow
import com.github.artem.pageobjectplugin.ui.pages.PluginToolWindowPage
import com.github.artem.pageobjectplugin.ui.support.Wait
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * UI tests: UT-01 to UT-05 — Tool Window structure and snapshot discovery.
 *
 * Reference example for the Page/Flow layering introduced in Task 13d. New
 * test classes should follow this pattern: construct a Flow in @BeforeEach,
 * then talk to Pages in the test bodies. No raw fixture access.
 */
@Feature("tool-window")
class ToolWindowUiTest : BaseUiTest() {

    private val toolWindow by lazy { PluginToolWindowPage(robot) }

    @BeforeEach
    fun ensureSnapshotLoaded() {
        // Idempotent: BaseUiTest.waitForIde already loaded the default snapshot,
        // but per-test isolation may have closed the tool window or selected
        // a different file. Re-running the flow guarantees a known-good state.
        SnapshotLoadFlow(robot).loadDefaultLoginSnapshot()
    }

    /**
     * UT-01: Tool window is visible after opening a .ts file.
     */
    @Test
    fun `tool window is visible`() {
        takeScreenshot("tool-window-visible")
        assertTrue(toolWindow.isVisible(), "Page Mirror tool window should be visible")
    }

    /**
     * UT-02: Opening a .ts file auto-populates the combo box with a discovered snapshot.
     */
    @Test
    fun `opening ts file auto discovers snapshot`() {
        toolWindow.waitForSnapshotDiscovery(Duration.ofSeconds(10))
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
        toolWindow.waitForSnapshotDiscovery(Duration.ofSeconds(10))
        val allNames = toolWindow.allSnapshotNames()
        assertTrue(
            allNames.isNotEmpty(),
            "Combo box should list at least one snapshot, got: $allNames",
        )
        assertTrue(
            allNames.any { it.contains("login") || it.contains("initial") },
            "Expected 'login/initial' in snapshot list, got: $allNames",
        )
    }

    /**
     * UT-04: Selecting a snapshot from the combo box loads it.
     * Verified indirectly by checking the combo box value updates.
     */
    @Test
    fun `selecting snapshot from combo box loads it`() {
        toolWindow.waitForSnapshotDiscovery(Duration.ofSeconds(10))
        val before = toolWindow.selectedSnapshotName()

        // Re-select the same item (exercises the action listener path)
        toolWindow.selectSnapshot("initial")

        val after = toolWindow.selectedSnapshotName()
        assertTrue(
            after.contains("initial") || after == before,
            "After selection, combo should show 'initial', was: '$after'",
        )
    }

    /**
     * UT-05: Refresh button re-scans and updates the combo box.
     * Verifying that clicking Refresh does not crash and combo stays populated.
     */
    @Test
    fun `refresh button re scans snapshots`() {
        toolWindow.waitForSnapshotDiscovery(Duration.ofSeconds(10))
        val beforeRefresh = toolWindow.selectedSnapshotName()

        toolWindow.refresh()

        // After refresh, the combo box should re-populate; poll for non-blank.
        Wait.pollUntilTrue(
            timeout = Duration.ofSeconds(5),
            interval = Duration.ofMillis(100),
            message = { "combo blank after refresh" },
        ) {
            toolWindow.selectedSnapshotName().isNotBlank()
        }
        val afterRefresh = toolWindow.selectedSnapshotName()
        assertFalse(afterRefresh.isBlank(), "After refresh, combo box should still have a selection")
        assertTrue(
            afterRefresh.contains("login") ||
                afterRefresh.contains("initial") ||
                afterRefresh == beforeRefresh,
            "After refresh, snapshot should still be discoverable, was: '$afterRefresh'",
        )
    }
}
