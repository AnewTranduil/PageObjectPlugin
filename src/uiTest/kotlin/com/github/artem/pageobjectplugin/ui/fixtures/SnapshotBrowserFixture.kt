package com.github.artem.pageobjectplugin.ui.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import java.time.Duration

/**
 * Fixture wrapping the JCEF browser component inside the Page Mirror tool window.
 *
 * Note: JCEF JS execution via `executeJavaScript` is fire-and-forget (no return value).
 * State queries use the IDE-side service/model instead of browser JS.
 */
class SnapshotBrowserFixture(robot: RemoteRobot, component: RemoteComponent) :
    CommonContainerFixture(robot, component) {

    /** JS snippet to load the SnapshotService via the plugin's own classloader. */
    private val getServiceJs = """
        var __pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.artem.pageobjectplugin")
        var __plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(__pluginId)
        var __cl = __plugin.getPluginClassLoader()
        var __svcClass = __cl.loadClass("com.github.artem.pageobjectplugin.services.SnapshotService")
        var __project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
        var __service = __project.getService(__svcClass)
    """.trimIndent()

    /**
     * Returns the number of elements in the currently loaded snapshot's layout data.
     */
    fun layoutElementCount(): Int = try {
        callJs<Int>("""
            $getServiceJs
            var bundle = __service.getCurrentBundle()
            var count = 0
            if (bundle != null) {
                var layoutText = java.nio.file.Files.readString(bundle.getLayoutPath())
                var json = com.google.gson.JsonParser.parseString(layoutText).getAsJsonObject()
                if (json.has("elements")) {
                    count = json.getAsJsonArray("elements").size()
                }
            }
            new java.lang.Integer(count)
        """, runInEdt = true)
    } catch (_: Exception) { 0 }

    /**
     * Returns true if a snapshot bundle is currently loaded.
     */
    fun isHighlightVisible(): Boolean = try {
        callJs<Boolean>("""
            $getServiceJs
            new java.lang.Boolean(__service.isHighlightActive())
        """, runInEdt = true)
    } catch (_: Exception) { false }

    /**
     * Returns true when inspect mode is active.
     * Queries the SnapshotService Kotlin-side flag (kept in sync by ToggleInspectAction
     * and PickerResultHandler).
     */
    fun isInspectModeActive(): Boolean = try {
        callJs<Boolean>("""
            $getServiceJs
            new java.lang.Boolean(__service.isInspectModeActive())
        """, runInEdt = true)
    } catch (_: Exception) { false }

    /**
     * Returns true if the JCEF component is showing.
     */
    fun isBrowserShowing(): Boolean = try {
        callJs<Boolean>("""
            new java.lang.Boolean(component.isShowing())
        """, runInEdt = true)
    } catch (_: Exception) { false }

    companion object {
        private val JCEF_XPATHS = listOf(
            "//div[@class='JBCefOsrComponent']",
            "//div[contains(@class, 'JBCefBrowser')]",
            "//div[@class='CefBrowserWr']",
        )

        fun find(robot: RemoteRobot): SnapshotBrowserFixture {
            for (xpath in JCEF_XPATHS) {
                try {
                    return robot.find(byXpath(xpath), Duration.ofSeconds(3))
                } catch (_: Exception) { /* try next */ }
            }
            throw AssertionError("JCEF browser component not found. Tried XPaths: $JCEF_XPATHS.")
        }

        fun findInsideToolWindow(toolWindow: PageMirrorToolWindowFixture): SnapshotBrowserFixture {
            for (xpath in JCEF_XPATHS.map { it.replace("//", ".//") }) {
                try {
                    return toolWindow.find(byXpath(xpath), Duration.ofSeconds(3))
                } catch (_: Exception) { /* try next */ }
            }
            throw AssertionError("JCEF browser not found inside Page Mirror tool window")
        }
    }
}
