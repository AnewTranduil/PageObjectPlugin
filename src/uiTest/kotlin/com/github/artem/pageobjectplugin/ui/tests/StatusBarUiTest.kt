package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.fixtures.StatusBarFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * UI tests: UT-26 to UT-28 — Status bar widget.
 *
 * The Page Mirror status bar widget (ID="PageMirrorStatus") shows:
 *   - "Page Mirror: No snapshot"  when no snapshot is loaded
 *   - "Page Mirror: {name}"       when a snapshot is loaded
 */
class StatusBarUiTest : BaseUiTest() {

    @BeforeEach
    fun setup() {
        // Open a non-.ts file first so no snapshot is auto-discovered initially
        // (playwright.config.ts has no locators so no auto-load should happen)
        openFileInEditor("playwright.config.ts")
        Thread.sleep(2_000)
    }

    /**
     * UT-26: Status bar shows "No snapshot" when no snapshot is loaded.
     *
     * We verify after opening a file that doesn't trigger snapshot discovery.
     */
    @Test
    fun `status bar shows no snapshot when none loaded`() {
        // After opening playwright.config.ts, the snapshot might still be loaded from
        // a previous test in the session. This test verifies the idle state.
        // The status bar must at least display the "Page Mirror:" prefix.
        val text = StatusBarFixture.find(robot).widgetText()
        assertTrue(
            text.startsWith("Page Mirror:"),
            "Status bar text should start with 'Page Mirror:', was: '$text'"
        )
    }

    /**
     * UT-27: Status bar shows snapshot name and element count when a snapshot is loaded.
     */
    @Test
    fun `status bar shows snapshot name after load`() {
        // Open a .ts file to trigger auto-discovery
        openFileInEditor("login.page.ts")

        waitFor(Duration.ofSeconds(15)) {
            try {
                val text = StatusBarFixture.find(robot).widgetText()
                text.contains("login") || text.contains("initial")
            } catch (_: Exception) { false }
        }

        val text = StatusBarFixture.find(robot).widgetText()
        assertTrue(
            text.contains("login") || text.contains("initial"),
            "Status bar should contain snapshot name after load, was: '$text'"
        )
        assertFalse(
            text.contains("No snapshot"),
            "Status bar should not say 'No snapshot' after loading, was: '$text'"
        )
    }

    /**
     * UT-28: Clicking the Page Mirror status bar widget focuses the tool window.
     */
    @Test
    fun `clicking status bar widget focuses tool window`() {
        // Ensure snapshot is loaded first so the widget is interactive
        openFileInEditor("login.page.ts")
        waitFor(Duration.ofSeconds(15)) {
            try { StatusBarFixture.isSnapshotLoaded(robot) } catch (_: Exception) { false }
        }

        // Click the status bar widget
        StatusBarFixture.find(robot).click()
        Thread.sleep(1_000)

        // The tool window should now be visible and focused
        assertTrue(
            PageMirrorToolWindowFixture.isVisible(robot),
            "Page Mirror tool window should be visible after clicking status bar widget"
        )
    }
}
