package com.github.artem.pageobjectplugin.ui.fixtures

import com.github.artem.pageobjectplugin.ui.locators.PageMirrorLocators
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
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
     * Returns true if a snapshot bundle is currently loaded in the service.
     */
    fun isSnapshotLoaded(): Boolean = try {
        callJs<Boolean>("""
            $getServiceJs
            new java.lang.Boolean(__service.getCurrentBundle() != null)
        """, runInEdt = true)
    } catch (_: Exception) { false }

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
     * Returns true when the "Show All" multi-highlight mode is active.
     * Queries SnapshotService.isHighlightAllActive (set by the "Show All"
     * toolbar button via highlightAllLocators()).
     */
    fun isHighlightAllActive(): Boolean = try {
        callJs<Boolean>("""
            $getServiceJs
            new java.lang.Boolean(__service.isHighlightAllActive())
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
        fun find(robot: RemoteRobot): SnapshotBrowserFixture {
            for (locator in PageMirrorLocators.jcefBrowserCandidates) {
                try {
                    return robot.find(locator, Duration.ofSeconds(3))
                } catch (_: Exception) { /* try next */ }
            }
            throw AssertionError(
                "JCEF browser component not found. Tried ${PageMirrorLocators.jcefBrowserCandidates.size} candidates."
            )
        }

        fun findInsideToolWindow(toolWindow: PageMirrorToolWindowFixture): SnapshotBrowserFixture {
            for (locator in PageMirrorLocators.jcefBrowserInsideContainer) {
                try {
                    return toolWindow.find(locator, Duration.ofSeconds(3))
                } catch (_: Exception) { /* try next */ }
            }
            throw AssertionError("JCEF browser not found inside Page Mirror tool window")
        }
    }
}
