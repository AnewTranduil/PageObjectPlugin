package com.github.artem.pageobjectplugin.ui.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import java.awt.event.KeyEvent
import java.time.Duration

/**
 * Fixture wrapping the "Page Mirror" tool window panel.
 *
 * The tool window content is a JPanel containing:
 *   - A JComboBox (snapshot selector)
 *   - A JButton("Refresh")
 *   - The JCEF browser component (JBCefOsrComponent)
 */
class PageMirrorToolWindowFixture(robot: RemoteRobot, component: RemoteComponent) :
    CommonContainerFixture(robot, component) {

    /** The snapshot selector combo box. */
    val comboBox: ComponentFixture
        get() = find(byXpath(".//div[@class='JComboBox' or @class='ComboBox']"), Duration.ofSeconds(5))

    /** The Refresh button. */
    val refreshButton: ComponentFixture
        get() = find(byXpath(".//div[@class='JButton' and @text='Refresh']"), Duration.ofSeconds(5))

    /** Returns the currently displayed text in the combo box. */
    fun selectedSnapshotName(): String =
        comboBox.callJs("component.getSelectedItem() != null ? '' + component.getSelectedItem() : ''")

    /**
     * Opens the combo box dropdown and selects the entry whose display text
     * contains [partialName].
     */
    fun selectSnapshot(partialName: String) {
        comboBox.click()
        Thread.sleep(500)
        // Use callJs to iterate items and select the matching one
        comboBox.callJs<Boolean>("""
            var model = component.getModel()
            for (var i = 0; i < model.getSize(); i++) {
                var item = '' + model.getElementAt(i)
                if (item.indexOf("$partialName") >= 0) {
                    component.setSelectedIndex(i)
                    break
                }
            }
            true
        """, runInEdt = true)
        Thread.sleep(500)
    }

    /** Returns all snapshot names from the combo box model. */
    fun allSnapshotNames(): List<String> {
        val joined = comboBox.callJs<String>("""
            var model = component.getModel()
            var s = ""
            for (var i = 0; i < model.getSize(); i++) {
                if (i > 0) s += "|||"
                s += model.getElementAt(i)
            }
            s
        """, runInEdt = true)
        return if (joined.isBlank()) emptyList() else joined.split("|||")
    }

    /** True if the JCEF browser component is present and visible. */
    fun isBrowserVisible(): Boolean = try {
        find<ComponentFixture>(
            byXpath(
                ".//div[@class='JBCefOsrComponent' " +
                    "or @class='CefBrowserWr' or contains(@class, 'JBCefBrowser')]"
            ),
            Duration.ofSeconds(3)
        ).isShowing
    } catch (_: Exception) { false }

    companion object {
        private const val TOOL_WINDOW_XPATH =
            "//div[@class='InternalDecoratorImpl' and contains(@accessiblename, 'Page Mirror')]"

        fun find(robot: RemoteRobot): PageMirrorToolWindowFixture =
            robot.find(byXpath(TOOL_WINDOW_XPATH), Duration.ofSeconds(30))

        fun isVisible(robot: RemoteRobot): Boolean = try {
            robot.findAll<ComponentFixture>(byXpath(TOOL_WINDOW_XPATH)).isNotEmpty()
        } catch (_: Exception) { false }
    }
}
