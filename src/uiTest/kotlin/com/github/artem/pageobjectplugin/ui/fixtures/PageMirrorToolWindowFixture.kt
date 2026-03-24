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
 *   - The JCEF browser component
 */
class PageMirrorToolWindowFixture(robot: RemoteRobot, component: RemoteComponent) :
    CommonContainerFixture(robot, component) {

    /** The snapshot selector combo box. */
    val comboBox: ComponentFixture
        get() = find(byXpath(".//div[@class='ComboBox']"), Duration.ofSeconds(5))

    /** The Refresh button. */
    val refreshButton: ComponentFixture
        get() = find(byXpath(".//div[@class='JButton' and @text='Refresh']"), Duration.ofSeconds(5))

    /** Returns the currently displayed text in the combo box. */
    fun selectedSnapshotName(): String =
        comboBox.callJs("component.getSelectedItem()?.toString() ?: ''")

    /**
     * Opens the combo box dropdown and selects the entry whose display text
     * contains [partialName].
     */
    fun selectSnapshot(partialName: String) {
        comboBox.click()
        waitFor(Duration.ofSeconds(5)) {
            remoteRobot.findAll<CommonContainerFixture>(
                byXpath("//div[@class='JList']")
            ).isNotEmpty()
        }
        val list = remoteRobot.find<CommonContainerFixture>(
            byXpath("//div[@class='JList']"),
            Duration.ofSeconds(5)
        )
        list.findAll<ComponentFixture>(
            byXpath(".//div[contains(@text, '$partialName')]")
        ).firstOrNull()?.click()
            ?: throw AssertionError("Snapshot entry containing '$partialName' not found in combo list")
        Thread.sleep(500)
    }

    /** Returns all snapshot names visible in the combo box dropdown. */
    fun allSnapshotNames(): List<String> {
        comboBox.click()
        waitFor(Duration.ofSeconds(5)) {
            remoteRobot.findAll<CommonContainerFixture>(byXpath("//div[@class='JList']")).isNotEmpty()
        }
        val items = remoteRobot.find<CommonContainerFixture>(
            byXpath("//div[@class='JList']"),
            Duration.ofSeconds(5)
        ).findAll<ComponentFixture>(byXpath(".//div[@class='SimpleColoredComponent']"))
            .map { it.callJs<String>("component.toString()") }
        // Dismiss the dropdown
        keyboard { key(KeyEvent.VK_ESCAPE) }
        return items
    }

    /** True if the JCEF browser component is present and visible. */
    fun isBrowserVisible(): Boolean = try {
        find<ComponentFixture>(
            byXpath(
                ".//div[@class='JBCefBrowser\$CefBrowserOsrWithHandler' " +
                    "or @class='CefBrowserWr' or contains(@class, 'JBCefBrowser')]"
            ),
            Duration.ofSeconds(3)
        ).isShowing
    } catch (_: Exception) { false }

    companion object {
        private const val TOOL_WINDOW_XPATH =
            "//div[@class='InternalDecorator' and @accessiblename='Page Mirror']"

        fun find(robot: RemoteRobot): PageMirrorToolWindowFixture =
            robot.find(byXpath(TOOL_WINDOW_XPATH), Duration.ofSeconds(10))

        fun isVisible(robot: RemoteRobot): Boolean = try {
            robot.findAll<ComponentFixture>(byXpath(TOOL_WINDOW_XPATH)).isNotEmpty()
        } catch (_: Exception) { false }
    }
}
