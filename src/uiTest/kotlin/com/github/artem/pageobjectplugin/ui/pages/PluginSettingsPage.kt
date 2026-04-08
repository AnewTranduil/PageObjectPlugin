package com.github.artem.pageobjectplugin.ui.pages

import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorSettingsFixture
import com.github.artem.pageobjectplugin.ui.support.StepRecorder
import com.intellij.remoterobot.RemoteRobot

/**
 * Page object for the IDE Settings dialog scoped to the Page Mirror panel.
 *
 * Wraps [PageMirrorSettingsFixture]'s static helpers (`open`, `clickOk`, etc.)
 * plus per-field setters/getters into a single object whose lifetime is the
 * settings dialog itself. Tests typically construct one via [SettingsChangeFlow].
 */
class PluginSettingsPage(private val robot: RemoteRobot) {

    private var fixture: PageMirrorSettingsFixture? = null

    fun open(): PluginSettingsPage = StepRecorder.step(
        label = "openSettings(Page Mirror)",
        robot = robot,
    ) {
        fixture = PageMirrorSettingsFixture.open(robot)
        this
    }

    private fun requireOpen(): PageMirrorSettingsFixture =
        fixture ?: error("PluginSettingsPage: open() must be called before this method")

    // Getters

    fun searchDepth(): Int = requireOpen().searchDepth()
    fun highlightColor(): String = requireOpen().highlightColor()
    fun codeGenStyle(): String = requireOpen().codeGenStyle()
    fun isAutoReloadEnabled(): Boolean = requireOpen().isAutoReloadEnabled()

    // Setters

    fun setSearchDepth(value: Int): PluginSettingsPage = StepRecorder.step(
        label = "setSearchDepth($value)",
        robot = robot,
    ) {
        requireOpen().setSearchDepth(value)
        this
    }

    fun setHighlightColor(color: String): PluginSettingsPage = StepRecorder.step(
        label = "setHighlightColor($color)",
        robot = robot,
    ) {
        requireOpen().setHighlightColor(color)
        this
    }

    fun setCodeGenStyle(style: String): PluginSettingsPage = StepRecorder.step(
        label = "setCodeGenStyle($style)",
        robot = robot,
    ) {
        requireOpen().setCodeGenStyle(style)
        this
    }

    // Buttons

    fun clickOk(): PluginSettingsPage = StepRecorder.step(
        label = "settingsClickOk",
        robot = robot,
    ) {
        PageMirrorSettingsFixture.clickOk(robot)
        fixture = null
        this
    }

    fun clickApply(): PluginSettingsPage = StepRecorder.step(
        label = "settingsClickApply",
        robot = robot,
    ) {
        PageMirrorSettingsFixture.clickApply(robot)
        this
    }

    fun clickCancel(): PluginSettingsPage = StepRecorder.step(
        label = "settingsClickCancel",
        robot = robot,
    ) {
        PageMirrorSettingsFixture.clickCancel(robot)
        fixture = null
        this
    }
}
