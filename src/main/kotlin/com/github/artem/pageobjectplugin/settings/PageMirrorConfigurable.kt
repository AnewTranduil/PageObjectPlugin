package com.github.artem.pageobjectplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel

class PageMirrorConfigurable(private val project: Project) : Configurable {

    private var searchDepthSpinner: JSpinner? = null
    private var autoReloadCheckbox: JCheckBox? = null
    private var highlightColorField: JTextField? = null
    private var codeGenStyleCombo: JComboBox<String>? = null

    override fun getDisplayName(): String = "Page Mirror"

    override fun createComponent(): JComponent {
        val settings = PageMirrorSettings.getInstance(project).state

        searchDepthSpinner = JSpinner(SpinnerNumberModel(settings.snapshotSearchDepth, 1, 10, 1))
        autoReloadCheckbox = JCheckBox("Auto-reload on file change", settings.autoReloadOnChange)
        highlightColorField = JTextField(settings.highlightColor, 10)
        codeGenStyleCombo = JComboBox(arrayOf("Property", "Variable")).apply {
            selectedItem = settings.codeGenStyle
        }

        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            accessibleContext.accessibleName = "Page Mirror"

            add(row("Snapshot search depth:", searchDepthSpinner!!))
            add(autoReloadCheckbox!!)
            add(row("Highlight color:", highlightColorField!!))
            add(row("Code generation style:", codeGenStyleCombo!!))
        }

        return panel
    }

    private fun row(label: String, component: JComponent): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel(label))
            add(component)
        }
    }

    override fun isModified(): Boolean {
        val settings = PageMirrorSettings.getInstance(project).state
        return searchDepthSpinner?.value != settings.snapshotSearchDepth ||
            autoReloadCheckbox?.isSelected != settings.autoReloadOnChange ||
            highlightColorField?.text != settings.highlightColor ||
            codeGenStyleCombo?.selectedItem != settings.codeGenStyle
    }

    override fun apply() {
        val settings = PageMirrorSettings.getInstance(project)
        settings.loadState(
            PageMirrorSettings.State(
                snapshotSearchDepth = searchDepthSpinner?.value as? Int ?: 3,
                autoReloadOnChange = autoReloadCheckbox?.isSelected ?: true,
                highlightColor = highlightColorField?.text ?: "#3B82F6",
                codeGenStyle = codeGenStyleCombo?.selectedItem as? String ?: "Property"
            )
        )
    }

    override fun reset() {
        val settings = PageMirrorSettings.getInstance(project).state
        searchDepthSpinner?.value = settings.snapshotSearchDepth
        autoReloadCheckbox?.isSelected = settings.autoReloadOnChange
        highlightColorField?.text = settings.highlightColor
        codeGenStyleCombo?.selectedItem = settings.codeGenStyle
    }
}
