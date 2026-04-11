package com.github.artem.pageobjectplugin.ui.fixtures

import com.github.artem.pageobjectplugin.ui.locators.PageMirrorLocators
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.search.locators.Locator
import java.time.Duration

/**
 * Fixture for the Page Mirror status bar widget.
 *
 * The widget is rendered as a text component inside [IdeStatusBarImpl].
 * Its ID is "PageMirrorStatus" and its text follows the pattern:
 *   - "Page Mirror: No snapshot"
 *   - "Page Mirror: login/initial (8 elements)"
 */
class StatusBarFixture(robot: RemoteRobot, component: RemoteComponent) :
    CommonContainerFixture(robot, component) {

    /** Returns the full text displayed by the status bar widget. */
    fun widgetText(): String = callJs("component.getText()")

    companion object {
        /**
         * Finds the Page Mirror status bar widget by looking for a component
         * inside the IDE status bar whose accessible name or text starts with "Page Mirror".
         */
        fun find(robot: RemoteRobot): StatusBarFixture {
            val candidates: List<Locator> = listOf(
                PageMirrorLocators.statusBarWidgetById,
                PageMirrorLocators.statusBarWidgetByText,
            )
            for (locator in candidates) {
                try {
                    return robot.find(locator, Duration.ofSeconds(5))
                } catch (_: Exception) { /* try next */ }
            }
            throw AssertionError(
                "Page Mirror status bar widget not found. " +
                    "Ensure the IDE has a project open and the widget is enabled in Settings."
            )
        }

        /**
         * Returns true when the widget text indicates a snapshot is currently loaded
         * (i.e. does not end with "No snapshot").
         */
        fun isSnapshotLoaded(robot: RemoteRobot): Boolean = try {
            val text = find(robot).widgetText()
            text.contains("Page Mirror:") && !text.contains("No snapshot")
        } catch (_: Exception) { false }

        /**
         * Returns true when the widget displays "No snapshot".
         */
        fun isNoSnapshot(robot: RemoteRobot): Boolean = try {
            find(robot).widgetText().contains("No snapshot")
        } catch (_: Exception) { false }
    }
}
