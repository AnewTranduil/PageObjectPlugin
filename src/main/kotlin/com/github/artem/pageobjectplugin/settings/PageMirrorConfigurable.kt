package com.github.artem.pageobjectplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import javax.swing.JComponent
import kotlin.reflect.KMutableProperty0

class PageMirrorConfigurable(private val project: Project) : Configurable {

    private val settingsPanel by lazy { createSettingsPanel() }

    override fun getDisplayName(): String = "Page Mirror"

    override fun createComponent(): JComponent = settingsPanel

    override fun isModified(): Boolean = settingsPanel.isModified()

    override fun apply() {
        settingsPanel.apply()
        PageMirrorSettings.getInstance(project).loadState(
            PageMirrorSettings.State(
                snapshotSearchDepth = searchDepth,
                autoReloadOnChange = autoReload,
                highlightColor = highlightColor,
                codeGenStyle = codeGenStyle
            )
        )
    }

    override fun reset() {
        val state = PageMirrorSettings.getInstance(project).state
        searchDepth = state.snapshotSearchDepth
        autoReload = state.autoReloadOnChange
        highlightColor = state.highlightColor
        codeGenStyle = state.codeGenStyle
        settingsPanel.reset()
    }

    private var searchDepth = 3
    private var autoReload = true
    private var highlightColor = "#3B82F6"
    private var codeGenStyle = "Property"

    private fun createSettingsPanel(): DialogPanel {
        val state = PageMirrorSettings.getInstance(project).state
        searchDepth = state.snapshotSearchDepth
        autoReload = state.autoReloadOnChange
        highlightColor = state.highlightColor
        codeGenStyle = state.codeGenStyle

        return panel {
            row("Snapshot search depth:") {
                spinner(1..10, 1)
                    .bindIntValue(::searchDepth)
            }
            row {
                checkBox("Auto-reload on file change")
                    .bindSelected(::autoReload)
            }
            row("Highlight color:") {
                textField()
                    .bindText(::highlightColor)
            }
            row("Code generation style:") {
                comboBox(listOf("Property", "Variable"))
                    .bindItem(::codeGenStyle.toNullableProperty())
            }
        }
    }
}

/**
 * Binds a JSpinner cell to an Int property.
 * The DSL's built-in bindValue expects Double; this adapter bridges to Int.
 */
private fun <T : javax.swing.JSpinner> com.intellij.ui.dsl.builder.Cell<T>.bindIntValue(
    prop: KMutableProperty0<Int>
): com.intellij.ui.dsl.builder.Cell<T> {
    return this.bind(
        { (it.value as Number).toInt() },
        { spinner, value -> spinner.value = value },
        com.intellij.ui.dsl.builder.MutableProperty(prop::get, prop::set)
    )
}
