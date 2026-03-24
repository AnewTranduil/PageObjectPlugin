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
 * Provides helpers to execute JavaScript within the JCEF page and inspect
 * the rendered snapshot state.
 *
 * Note: The exact JCEF component class name varies by IntelliJ version.
 * Adjust [JCEF_XPATH] if needed by inspecting the component tree with the
 * Remote Robot inspector (run IDE with robot-server and open port 8082 in browser).
 */
class SnapshotBrowserFixture(robot: RemoteRobot, component: RemoteComponent) :
    CommonContainerFixture(robot, component) {

    /**
     * Executes [script] inside the JCEF browser and returns the string result.
     *
     * The script runs in the top-level page (not inside the iframe).
     * Use `window.__layoutData`, `window.__inspectMode`, etc. for state checks.
     */
    fun executeJs(script: String): String =
        callJs<String>("component.getCefBrowser().executeJavaScript(\"$script\", '', 0); ''")

    /**
     * Returns true if the highlight overlay is currently shown in the JCEF page.
     * Checks for presence of the `.pm-highlight` element injected by highlightElement().
     */
    fun isHighlightVisible(): Boolean = try {
        callJs<Boolean>(
            "component.getCefBrowser().executeJavaScript(" +
                "\"window.__lastHighlight !== undefined && window.__lastHighlight !== null\", '', 0); false"
        )
    } catch (_: Exception) { false }

    /**
     * Returns the number of elements in the loaded layout.json.
     * Requires window.__layoutData to be populated by loadSnapshot().
     */
    fun layoutElementCount(): Int = try {
        callJs("component.getCefBrowser().executeJavaScript(" +
            "\"window.__layoutData && window.__layoutData.elements ? window.__layoutData.elements.length : 0\", '', 0); 0"
        )
    } catch (_: Exception) { 0 }

    /**
     * Returns true when inspect mode is active (green hover boxes visible).
     */
    fun isInspectModeActive(): Boolean = try {
        callJs<Boolean>(
            "component.getCefBrowser().executeJavaScript(\"!!window.__inspectMode\", '', 0); false"
        )
    } catch (_: Exception) { false }

    companion object {
        /** XPath patterns for JCEF browser component across IntelliJ 2024.x. */
        private val JCEF_XPATHS = listOf(
            "//div[contains(@class, 'JBCefBrowser')]",
            "//div[@class='CefBrowserWr']",
            "//div[@class='JBCefOsrComponent']",
        )

        fun find(robot: RemoteRobot): SnapshotBrowserFixture {
            for (xpath in JCEF_XPATHS) {
                try {
                    return robot.find(byXpath(xpath), Duration.ofSeconds(3))
                } catch (_: Exception) { /* try next */ }
            }
            throw AssertionError(
                "JCEF browser component not found. Tried XPaths: $JCEF_XPATHS. " +
                    "Inspect the component tree at http://localhost:8082 to find the correct class name."
            )
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
