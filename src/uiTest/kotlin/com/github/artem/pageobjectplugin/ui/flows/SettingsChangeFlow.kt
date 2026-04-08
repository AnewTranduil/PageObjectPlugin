package com.github.artem.pageobjectplugin.ui.flows

import com.github.artem.pageobjectplugin.ui.pages.PluginSettingsPage
import com.github.artem.pageobjectplugin.ui.support.StepRecorder
import com.intellij.remoterobot.RemoteRobot

/**
 * Flow for changing IDE settings via the Page Mirror configurable.
 *
 * Two usage modes:
 *   - [withSettings] hands a [PluginSettingsPage] to the caller and guarantees
 *     the dialog is closed (Cancel) on exception.
 *   - The dedicated single-field helpers (e.g. [setSearchDepth]) open, set,
 *     and OK in one shot.
 */
class SettingsChangeFlow(private val robot: RemoteRobot) {

    /**
     * Opens the Page Mirror settings panel, runs [block] against it, and
     * leaves the dialog state to the caller (the caller must invoke
     * `clickOk` / `clickApply` / `clickCancel` inside [block]).
     *
     * If [block] throws, this flow attempts to Cancel the dialog so a
     * subsequent test does not inherit a stuck modal.
     */
    fun withSettings(block: (PluginSettingsPage) -> Unit) = StepRecorder.step(
        label = "flow.withSettings",
        robot = robot,
    ) {
        val page = PluginSettingsPage(robot).apply { open() }
        try {
            block(page)
        } catch (t: Throwable) {
            try { page.clickCancel() } catch (_: Throwable) { /* swallow cleanup error */ }
            throw t
        }
    }

    fun setSearchDepth(value: Int) = withSettings { page ->
        page.setSearchDepth(value)
        page.clickOk()
    }

    fun setHighlightColor(color: String) = withSettings { page ->
        page.setHighlightColor(color)
        page.clickOk()
    }

    fun setCodeGenStyle(style: String) = withSettings { page ->
        page.setCodeGenStyle(style)
        page.clickOk()
    }
}
