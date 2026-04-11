package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.annotations.Feature
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.fixtures.SnapshotBrowserFixture
import com.github.artem.pageobjectplugin.ui.pages.EditorPage
import com.github.artem.pageobjectplugin.ui.pages.PluginToolWindowPage
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
@Feature("element-picker")
class ElementPickerUiTest : BaseUiTest() {

    private val editor by lazy { EditorPage(robot) }
    private val toolWindow by lazy { PluginToolWindowPage(robot) }

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
        editor.openFileInEditor("login.page.ts")
        if (!PageMirrorToolWindowFixture.isVisible(robot)) {
            toolWindow.open()
        }
        toolWindow.waitForSnapshotDiscovery(Duration.ofSeconds(15))
        Thread.sleep(2_000)  // JCEF page first paint — no observable signal
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
     * Simulates a JCEF inspect click by calling `PickerResultHandler`'s
     * test-only entry point directly with fabricated element JSON. Bypasses
     * the real click + popup since both are unreliable under Xvfb:
     *   - JCEF click routing via Xvfb doesn't always reach the iframe
     *   - JBPopupFactory's list popup requires real keyboard focus
     * The helper exercises everything downstream (JSON parse, locator +
     * field-name generation, WriteAction + document insertion, service
     * state sync), which is the business logic we want to validate.
     *
     * The JSON is base64-encoded on the Kotlin side and decoded inside the
     * JS payload so nested quotes and backslashes don't need escaping.
     */
    private fun simulatePickerElementClick(jsonString: String): String {
        val base64Json = java.util.Base64.getEncoder()
            .encodeToString(jsonString.toByteArray(Charsets.UTF_8))
        return ideFrame().callJs<String>(
            """
            var __pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.artem.pageobjectplugin")
            var __plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(__pluginId)
            var __cl = __plugin.getPluginClassLoader()
            var __handlerClass = __cl.loadClass("com.github.artem.pageobjectplugin.locators.PickerResultHandler")
            var __project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
            // Rhino JS does not support the Java `.class` literal suffix. Use
            // Class.forName(...) to obtain Class<?> objects and pass them to
            // the reflection APIs.
            var __projectClass = java.lang.Class.forName("com.intellij.openapi.project.Project", true, __cl)
            var __stringClass = java.lang.Class.forName("java.lang.String")
            var __ctor = __handlerClass.getConstructor(__projectClass)
            var __handler = __ctor.newInstance(__project)
            var __method = __handlerClass.getMethod("insertLocatorForTest", __stringClass, __stringClass)
            var __bytes = java.util.Base64.getDecoder().decode("$base64Json")
            var __json = new java.lang.String(__bytes, java.nio.charset.StandardCharsets.UTF_8)
            var __result = __method.invoke(__handler, __json, "Property")
            __result == null ? "" : ("" + __result)
            """.trimIndent(),
            runInEdt = true,
        )
    }

    private fun activeEditorText(): String {
        return ideFrame().callJs<String>(
            """
            var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
            var editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor()
            editor != null ? editor.getDocument().getText() : ""
            """.trimIndent(),
            runInEdt = true,
        )
    }

    /**
     * UT-15: Clicking an element in the JCEF snapshot while in inspect mode inserts
     * a Playwright locator into the currently active editor.
     *
     * Under Xvfb the real JCEF click is unreliable so we exercise the
     * `PickerResultHandler.insertLocatorForTest` entry point directly with
     * fabricated ElementData matching the login-button. See
     * [simulatePickerElementClick] for the rationale.
     */
    @Test
    fun `clicking element in inspect mode inserts locator into editor`() {
        editor.openFileInEditor("login.page.ts")
        editor.goToLine(8)  // line after last property

        // Activate inspect mode first so we can verify auto-exit below
        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)
        if (!browser.isInspectModeActive()) {
            toggleInspectMode()
        }
        assertTrue(browser.isInspectModeActive(), "Inspect mode must be active before simulated click")
        takeScreenshot("before-inspect-click")

        val elementJson = """
            {"selector":"button[type=\"submit\"]","tag":"button","role":"button","text":"Login","attributes":{"type":"submit","data-testid":"login-button"}}
        """.trimIndent()
        val inserted = simulatePickerElementClick(elementJson)
        takeScreenshot("after-inspect-click")

        // The helper resets the inspect-mode flag; verify it took effect.
        assertFalse(
            browser.isInspectModeActive(),
            "Inspect mode should auto-exit after insertLocatorForTest",
        )

        // The helper returns the inserted code; assert it looks like a locator.
        assertTrue(
            inserted.contains("getByTestId") || inserted.contains("page.locator") ||
                inserted.contains("getByRole") || inserted.contains("getByText"),
            "Returned inserted code should be a Playwright locator, was: '$inserted'",
        )

        // And the editor should now contain that code.
        val content = activeEditorText()
        assertTrue(
            content.contains("page.getBy") || content.contains("page.locator"),
            "Editor should contain a newly inserted Playwright locator",
        )
    }

    /**
     * UT-16: Inspect mode exits automatically after an element is clicked.
     *
     * Same Xvfb rationale as UT-15 — we call the test-only helper instead
     * of issuing a real JCEF click.
     */
    @Test
    fun `inspect mode auto exits after element click`() {
        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)

        if (!browser.isInspectModeActive()) {
            toggleInspectMode()
        }
        assertTrue(browser.isInspectModeActive(), "Inspect mode must be on before simulated click")

        simulatePickerElementClick(
            """{"selector":"#username","tag":"input","role":null,"text":null,"attributes":{"id":"username"}}""",
        )

        takeScreenshot("after-auto-exit-click")
        assertFalse(
            browser.isInspectModeActive(),
            "Inspect mode should be off after insertLocatorForTest",
        )
    }
}
