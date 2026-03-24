package com.github.artem.pageobjectplugin.ui.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import java.time.Duration

/**
 * Fixture for the editor gutter component.
 *
 * Provides helpers to check Page Mirror gutter annotations (match-count badges)
 * produced by [SelectorValidationAnnotator].
 */
class GutterFixture(robot: RemoteRobot, component: RemoteComponent) :
    CommonContainerFixture(robot, component) {

    /**
     * Returns all gutter icon tooltip texts currently visible in this gutter.
     *
     * Page Mirror badges use tooltips like "1 match", "0 matches", "2 matches".
     */
    fun allIconTooltips(): List<String> =
        findAll<ComponentFixture>(byXpath(".//div[@tooltiptext]"))
            .mapNotNull { icon ->
                try { icon.callJs<String>("component.getToolTipText()") } catch (_: Exception) { null }
            }

    /**
     * Returns gutter icon tooltips for icons whose Y-coordinate falls within the
     * approximate line [lineNumber] (1-based). The mapping is approximate because
     * gutter icons don't expose line numbers directly; each line is ~[lineHeightPx] pixels.
     */
    fun tooltipsOnLine(lineNumber: Int, lineHeightPx: Int = 20): List<String> {
        val expectedY = (lineNumber - 1) * lineHeightPx
        return findAll<ComponentFixture>(byXpath(".//div[@tooltiptext]"))
            .filter { icon ->
                val y = try { icon.callJs<Int>("component.getY()") } catch (_: Exception) { -1 }
                y in (expectedY - lineHeightPx)..(expectedY + lineHeightPx)
            }
            .mapNotNull { icon ->
                try { icon.callJs<String>("component.getToolTipText()") } catch (_: Exception) { null }
            }
    }

    /**
     * Returns true if any Page Mirror gutter badge is visible with a tooltip
     * containing [text] (e.g. "1 match", "0 matches").
     */
    fun hasBadgeWithTooltip(text: String): Boolean =
        allIconTooltips().any { it.contains(text, ignoreCase = true) }

    companion object {
        fun find(robot: RemoteRobot): GutterFixture =
            robot.find(
                byXpath("//div[@class='EditorGutterComponentImpl']"),
                Duration.ofSeconds(10)
            )
    }
}
