package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.fixtures.SnapshotBrowserFixture
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import java.time.Duration

/**
 * UI tests: UT-13 to UT-16 — Element picker (inspect mode).
 *
 * Inspect mode is toggled with Alt+Shift+I. In inspect mode, hovering over
 * elements in the JCEF snapshot shows green boxes; clicking an element sends
 * its JSON back to the IDE and inserts a Playwright locator into the editor.
 */
class ElementPickerUiTest : BaseUiTest() {

    @BeforeEach
    fun setup() {
        openFileInEditor("login.page.ts")
        waitFor(Duration.ofSeconds(15)) {
            try {
                val name = PageMirrorToolWindowFixture.find(robot).selectedSnapshotName()
                name.isNotBlank() && !name.contains("No snapshot")
            } catch (_: Exception) { false }
        }
        Thread.sleep(2_000)
    }

    /**
     * UT-13: Alt+Shift+I activates inspect mode in the JCEF page.
     */
    @Test
    fun `alt shift I activates inspect mode`() {
        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)

        // Make sure inspect mode is off first
        if (browser.isInspectModeActive()) {
            ideFrame().keyboard { hotKey(KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_I) }
            Thread.sleep(300)
        }
        assertFalse(browser.isInspectModeActive(), "Inspect mode should be off before test")

        ideFrame().keyboard { hotKey(KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_I) }
        Thread.sleep(500)

        assertTrue(
            browser.isInspectModeActive(),
            "Inspect mode should be active after Alt+Shift+I"
        )
    }

    /**
     * UT-14: Pressing Alt+Shift+I again deactivates inspect mode.
     */
    @Test
    fun `alt shift I again deactivates inspect mode`() {
        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)

        // Activate
        if (!browser.isInspectModeActive()) {
            ideFrame().keyboard { hotKey(KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_I) }
            Thread.sleep(300)
        }
        assertTrue(browser.isInspectModeActive(), "Inspect mode should be on before toggle-off")

        // Deactivate
        ideFrame().keyboard { hotKey(KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_I) }
        Thread.sleep(500)

        assertFalse(
            browser.isInspectModeActive(),
            "Inspect mode should be off after second Alt+Shift+I"
        )
    }

    /**
     * UT-15: Clicking an element in the JCEF snapshot while in inspect mode inserts
     * a Playwright locator into the currently active editor.
     *
     * The login snapshot contains a button element at approximately (527, 341) in the
     * 1280×720 viewport.  We click the JCEF component at a proportionally scaled position.
     */
    @Test
    fun `clicking element in inspect mode inserts locator into editor`() {
        // Position caret at end of class body for insertion
        openFileInEditor("login.page.ts")
        goToLine(8)  // line after last property

        // Activate inspect mode
        ideFrame().keyboard { hotKey(KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_I) }
        Thread.sleep(500)

        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)
        assertTrue(browser.isInspectModeActive(), "Inspect mode must be active before clicking")

        // Click the login button area in the JCEF component
        // The button is roughly centered in the snapshot; click the JCEF component
        // at a position that maps to the button (approx. 40% from left, 50% from top)
        browser.click()  // clicks center by default — adjust with .moveMouse() if needed
        Thread.sleep(1_500)

        // Inspect mode should exit automatically after click
        assertFalse(
            browser.isInspectModeActive(),
            "Inspect mode should auto-exit after element click"
        )

        // The editor should now contain a new locator line
        val editorContent = ideFrame()
            .find<ComponentFixture>(
                byXpath("//div[@class='EditorComponentImpl']"),
                Duration.ofSeconds(5)
            )
            .callJs<String>("component.getDocument().getText()")

        val hasLocator = editorContent.contains("page.getBy") ||
            editorContent.contains("page.locator")
        assertTrue(hasLocator, "Editor should contain a newly inserted Playwright locator")
    }

    /**
     * UT-16: Inspect mode exits automatically after an element is clicked.
     * Covered in UT-15 as part of the same flow.
     */
    @Test
    fun `inspect mode auto exits after element click`() {
        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)

        // Activate inspect mode
        if (!browser.isInspectModeActive()) {
            ideFrame().keyboard { hotKey(KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_I) }
            Thread.sleep(300)
        }
        assertTrue(browser.isInspectModeActive(), "Inspect mode must be on before click")

        // Click an element
        browser.click()
        Thread.sleep(1_500)

        assertFalse(
            browser.isInspectModeActive(),
            "Inspect mode should be off after clicking an element"
        )
    }
}
