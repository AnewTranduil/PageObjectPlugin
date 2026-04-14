package com.github.artem.pageobjectplugin.ui.fixtures

import com.github.artem.pageobjectplugin.ui.locators.PageMirrorLocators
import com.github.artem.pageobjectplugin.ui.support.Wait
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
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
        get() = find(PageMirrorLocators.toolWindowCombo, Duration.ofSeconds(5))

    /** The Refresh button. */
    val refreshButton: ComponentFixture
        get() = find(PageMirrorLocators.toolWindowRefreshButton, Duration.ofSeconds(5))

    /** The "Show All" button (toggles highlight-all mode). */
    val showAllButton: ComponentFixture
        get() = find(PageMirrorLocators.toolWindowShowAllButton, Duration.ofSeconds(5))

    /** Returns the currently displayed text in the combo box. */
    fun selectedSnapshotName(): String =
        comboBox.callJs("component.getSelectedItem() != null ? '' + component.getSelectedItem() : ''")

    /**
     * Opens the combo box dropdown and selects the entry whose display text
     * contains [partialName].
     */
    fun selectSnapshot(partialName: String) {
        comboBox.click()
        // Wait for the popup to actually appear instead of a fixed sleep.
        Wait.pollUntilTrue(
            timeout = Duration.ofSeconds(3),
            interval = Duration.ofMillis(50),
            message = { "combo popup never opened" },
        ) {
            comboBox.callJs("component.isPopupVisible()", runInEdt = true)
        }
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
        // Wait for the selection to actually take effect.
        Wait.pollUntilTrue(
            timeout = Duration.ofSeconds(3),
            interval = Duration.ofMillis(50),
            message = { "selected snapshot never contained '$partialName'" },
        ) {
            selectedSnapshotName().contains(partialName)
        }
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
        find<ComponentFixture>(PageMirrorLocators.jcefBrowserAny, Duration.ofSeconds(3))
            .isShowing
    } catch (_: Exception) { false }

    companion object {
        fun find(robot: RemoteRobot): PageMirrorToolWindowFixture =
            robot.find(PageMirrorLocators.toolWindowDecorator, Duration.ofSeconds(30))

        fun isVisible(robot: RemoteRobot): Boolean = try {
            robot.findAll<ComponentFixture>(PageMirrorLocators.toolWindowDecorator).isNotEmpty()
        } catch (_: Exception) { false }
    }
}
