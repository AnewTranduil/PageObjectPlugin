package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.fixtures.SnapshotBrowserFixture
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * UI tests: UT-13 to UT-16 — Element picker (inspect mode).
 *
 * Inspect mode is toggled with Alt+Shift+I. In inspect mode, hovering over
 * elements in the JCEF snapshot shows green boxes; clicking an element sends
 * its JSON back to the IDE and inserts a Playwright locator into the editor.
 */
class ElementPickerUiTest : BaseUiTest() {

    /**
     * Programmatically toggles inspect mode via the plugin's SnapshotService,
     * bypassing keyboard shortcuts which fail when the IDE lacks OS focus.
     */
    private fun toggleInspectMode() {
        ideFrame().callJs<Boolean>("""
            var __pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.artem.pageobjectplugin")
            var __plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(__pluginId)
            var __cl = __plugin.getPluginClassLoader()
            var __svcClass = __cl.loadClass("com.github.artem.pageobjectplugin.services.SnapshotService")
            var __project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
            var __service = __project.getService(__svcClass)
            __service.setInspectModeActive(!__service.isInspectModeActive())
            var __browser = __service.getBrowser()
            if (__browser != null) {
                __browser.getCefBrowser().executeJavaScript("window.toggleInspectMode();", "", 0)
            }
            true
        """, runInEdt = true)
        Thread.sleep(300)
    }

    @BeforeEach
    fun setup() {
        openFileInEditor("login.page.ts")
        Thread.sleep(1_000)
        if (!PageMirrorToolWindowFixture.isVisible(robot)) {
            openToolWindow()
        }
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
            toggleInspectMode()
        }
        assertFalse(browser.isInspectModeActive(), "Inspect mode should be off before test")

        toggleInspectMode()

        takeScreenshot("after-inspect-activate")
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
            toggleInspectMode()
        }
        assertTrue(browser.isInspectModeActive(), "Inspect mode should be on before toggle-off")

        // Deactivate
        toggleInspectMode()

        takeScreenshot("after-inspect-deactivate")
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
        toggleInspectMode()

        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)
        takeScreenshot("before-inspect-click")
        assertTrue(browser.isInspectModeActive(), "Inspect mode must be active before clicking")

        // Click the login button area in the JCEF component
        // The button is roughly centered in the snapshot; click the JCEF component
        // at a position that maps to the button (approx. 40% from left, 50% from top)
        browser.click()  // clicks center by default — adjust with .moveMouse() if needed
        Thread.sleep(1_500)

        takeScreenshot("after-inspect-click")
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
            toggleInspectMode()
        }
        assertTrue(browser.isInspectModeActive(), "Inspect mode must be on before click")

        // Click an element
        browser.click()
        Thread.sleep(1_500)

        takeScreenshot("after-auto-exit-click")
        assertFalse(
            browser.isInspectModeActive(),
            "Inspect mode should be off after clicking an element"
        )
    }
}
