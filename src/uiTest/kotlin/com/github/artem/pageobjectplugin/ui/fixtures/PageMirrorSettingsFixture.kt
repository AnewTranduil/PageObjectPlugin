package com.github.artem.pageobjectplugin.ui.fixtures

import com.github.artem.pageobjectplugin.ui.support.Wait
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
 * Fixture for the Page Mirror settings panel inside the IDE Settings dialog.
 *
 * Open via: [PageMirrorSettingsFixture.open]
 */
class PageMirrorSettingsFixture(robot: RemoteRobot, component: RemoteComponent) :
    CommonContainerFixture(robot, component) {

    /** The "Snapshot search depth" JSpinner. */
    val searchDepthSpinner: ComponentFixture
        get() = find(byXpath(".//div[@class='JSpinner']"), Duration.ofSeconds(5))

    /** The "Auto-reload on file change" JCheckBox. */
    val autoReloadCheckbox: ComponentFixture
        get() = find(byXpath(".//div[@class='JCheckBox']"), Duration.ofSeconds(5))

    /** The "Highlight color" JTextField. */
    val highlightColorField: ComponentFixture
        get() = find(byXpath(".//div[@class='JTextField']"), Duration.ofSeconds(5))

    /** The "Code generation style" JComboBox. */
    val codeGenStyleCombo: ComponentFixture
        get() = find(byXpath(".//div[@class='JComboBox']"), Duration.ofSeconds(5))

    /** Returns the current value shown in the search depth spinner. */
    fun searchDepth(): Int = searchDepthSpinner.callJs("java.lang.Integer.parseInt(component.getValue().toString())")

    /** Returns the current highlight color text. */
    fun highlightColor(): String = highlightColorField.callJs("component.getText()")

    /** Returns the selected code-gen style. */
    fun codeGenStyle(): String = codeGenStyleCombo.callJs("component.getSelectedItem().toString()")

    /** Returns true if auto-reload is checked. */
    fun isAutoReloadEnabled(): Boolean = autoReloadCheckbox.callJs("component.isSelected()")

    /**
     * Sets the search depth spinner to [value].
     * Selects the text field inside the spinner, clears it, and types the new value.
     */
    fun setSearchDepth(value: Int) {
        // Click the formatted text field inside the spinner to give it focus
        find<ComponentFixture>(
            byXpath(".//div[@class='JSpinner']//div[@class='JFormattedTextField']"),
            Duration.ofSeconds(5)
        ).click()
        // Send keys to the focused field (keyboard acts on the global focus)
        keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
            enterText(value.toString())
            key(KeyEvent.VK_TAB)
        }
    }

    /** Sets the highlight color field to [color] (hex string, e.g. "#FF0000"). */
    fun setHighlightColor(color: String) {
        highlightColorField.click()
        keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
            enterText(color)
            key(KeyEvent.VK_TAB)
        }
    }

    /** Selects [style] ("Property" or "Variable") in the code-gen style combo. */
    fun setCodeGenStyle(style: String) {
        codeGenStyleCombo.callJs<Boolean>("""
            component.setSelectedItem("$style")
            true
        """, runInEdt = true)
    }

    companion object {
        private const val SETTINGS_DIALOG_XPATH = "//div[@class='DialogRootPane']"

        /**
         * Opens the IDE Settings dialog, navigates to Tools > Page Mirror,
         * and returns the settings fixture.
         *
         * Uses the IDE's ShowSettingsUtil API to navigate directly to the
         * Page Mirror configurable, avoiding fragile tree-search navigation.
         */
        fun open(robot: RemoteRobot): PageMirrorSettingsFixture {
            // Close any stale dialogs first
            closeAllDialogs(robot)

            val frame = robot.find<CommonContainerFixture>(
                byXpath("//div[@class='IdeFrameImpl']"),
                Duration.ofSeconds(5)
            )

            // Open Settings dialog programmatically via ShowSettingsUtil
            // Must use invokeLater because showSettingsDialog is modal and blocks EDT
            frame.callJs<Boolean>("""
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater(new java.lang.Runnable() {
                    run: function() {
                        var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
                        com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(project, "Page Mirror")
                    }
                })
                true
            """, runInEdt = true)

            // Wait for settings dialog
            waitFor(Duration.ofSeconds(15)) {
                robot.findAll<CommonContainerFixture>(byXpath(SETTINGS_DIALOG_XPATH)).isNotEmpty()
            }
            // Poll until at least one inner control (a JSpinner is present on Page Mirror)
            // is findable instead of a fixed sleep that papers over slow dialog rendering.
            Wait.pollUntilTrue(
                timeout = Duration.ofSeconds(5),
                interval = Duration.ofMillis(100),
                message = { "settings dialog body never rendered" },
            ) {
                try {
                    robot.findAll<ComponentFixture>(
                        byXpath("$SETTINGS_DIALOG_XPATH//div[@class='JSpinner']")
                    ).isNotEmpty()
                } catch (_: Exception) {
                    false
                }
            }

            // Find the settings panel — try Page Mirror panel first, then dialog root
            return try {
                robot.find(
                    byXpath("$SETTINGS_DIALOG_XPATH//div[@accessiblename='Page Mirror']"),
                    Duration.ofSeconds(10)
                )
            } catch (_: Exception) {
                robot.find(byXpath(SETTINGS_DIALOG_XPATH), Duration.ofSeconds(5))
            }
        }

        /** Closes all open dialogs by clicking Cancel on each. */
        private fun closeAllDialogs(robot: RemoteRobot) {
            try {
                val dialogs = robot.findAll<CommonContainerFixture>(byXpath(SETTINGS_DIALOG_XPATH))
                for (dialog in dialogs) {
                    try {
                        dialog.findAll<ComponentFixture>(byXpath(".//div[@text='Cancel']"))
                            .firstOrNull()?.click()
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
            // Wait until no DialogRootPane remains so the next dialog opens cleanly.
            try {
                Wait.pollUntilTrue(
                    timeout = Duration.ofSeconds(3),
                    interval = Duration.ofMillis(100),
                    message = { "stale dialogs failed to close" },
                ) {
                    robot.findAll<CommonContainerFixture>(byXpath(SETTINGS_DIALOG_XPATH)).isEmpty()
                }
            } catch (_: AssertionError) {
                // Best-effort cleanup; let the caller fail if a stale dialog matters.
            }
        }

        /** Clicks the OK button to apply and close the settings dialog. */
        fun clickOk(robot: RemoteRobot) {
            robot.findAll<ComponentFixture>(
                byXpath("//div[@class='DialogRootPane']//div[@text='OK']")
            ).firstOrNull()?.click()
            Wait.pollUntilTrue(
                timeout = Duration.ofSeconds(3),
                interval = Duration.ofMillis(50),
                message = { "settings dialog still open after OK" },
            ) {
                robot.findAll<CommonContainerFixture>(byXpath(SETTINGS_DIALOG_XPATH)).isEmpty()
            }
        }

        /** Clicks the Apply button (keeps dialog open). */
        fun clickApply(robot: RemoteRobot) {
            robot.findAll<ComponentFixture>(
                byXpath("//div[@class='DialogRootPane']//div[@text='Apply']")
            ).firstOrNull()?.click()
            // TODO(13b): Apply intentionally does not close the dialog and there is no
            // observable completion signal — short bounded wait covers the apply round-trip.
            Thread.sleep(300)
        }

        /** Clicks the Cancel button. */
        fun clickCancel(robot: RemoteRobot) {
            robot.findAll<ComponentFixture>(
                byXpath("//div[@class='DialogRootPane']//div[@text='Cancel']")
            ).firstOrNull()?.click()
            Wait.pollUntilTrue(
                timeout = Duration.ofSeconds(3),
                interval = Duration.ofMillis(50),
                message = { "settings dialog still open after Cancel" },
            ) {
                robot.findAll<CommonContainerFixture>(byXpath(SETTINGS_DIALOG_XPATH)).isEmpty()
            }
        }
    }
}
