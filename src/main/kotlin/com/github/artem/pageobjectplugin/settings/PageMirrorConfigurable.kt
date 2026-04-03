package com.github.artem.pageobjectplugin.settings

import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.toNullableProperty
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
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
                codeGenStyle = codeGenStyle,
                pageObjectPattern = pageObjectPattern,
                snapshotsRoot = snapshotsRoot,
                fileExtensions = fileExtensions
            )
        )
        SnapshotService.getInstance(project).applyHighlightColor()
    }

    override fun reset() {
        val state = PageMirrorSettings.getInstance(project).state
        searchDepth = state.snapshotSearchDepth
        autoReload = state.autoReloadOnChange
        highlightColor = state.highlightColor
        codeGenStyle = state.codeGenStyle
        pageObjectPattern = state.pageObjectPattern
        snapshotsRoot = state.snapshotsRoot
        fileExtensions = state.fileExtensions
        settingsPanel.reset()
    }

    private var searchDepth = 3
    private var autoReload = true
    private var highlightColor = "#3B82F6"
    private var codeGenStyle = "Property"
    private var pageObjectPattern = "(.+)\\.page\\.ts"
    private var snapshotsRoot = ".snapshots"
    private var fileExtensions = ".ts,.tsx"

    private fun createSettingsPanel(): DialogPanel {
        val state = PageMirrorSettings.getInstance(project).state
        searchDepth = state.snapshotSearchDepth
        autoReload = state.autoReloadOnChange
        highlightColor = state.highlightColor
        codeGenStyle = state.codeGenStyle
        pageObjectPattern = state.pageObjectPattern
        snapshotsRoot = state.snapshotsRoot
        fileExtensions = state.fileExtensions

        return panel {
            row("File extensions:") {
                textField()
                    .bindText(::fileExtensions)
                    .comment("Comma-separated, e.g. .ts,.tsx,.js")
            }
            row("Page object pattern:") {
                val patternStatus = JLabel(validatePattern(pageObjectPattern))
                textField()
                    .bindText(::pageObjectPattern)
                    .applyToComponent {
                        document.addDocumentListener(object : DocumentListener {
                            override fun insertUpdate(e: DocumentEvent) = updateStatus()
                            override fun removeUpdate(e: DocumentEvent) = updateStatus()
                            override fun changedUpdate(e: DocumentEvent) = updateStatus()
                            private fun updateStatus() {
                                patternStatus.text = validatePattern(text)
                            }
                        })
                    }
                cell(patternStatus)
            }
            row("Snapshots root:") {
                val resolvedLabel = JLabel(resolveSnapshotsRootHint(snapshotsRoot))
                textField()
                    .bindText(::snapshotsRoot)
                    .applyToComponent {
                        document.addDocumentListener(object : DocumentListener {
                            override fun insertUpdate(e: DocumentEvent) = updateHint()
                            override fun removeUpdate(e: DocumentEvent) = updateHint()
                            override fun changedUpdate(e: DocumentEvent) = updateHint()
                            private fun updateHint() {
                                resolvedLabel.text = resolveSnapshotsRootHint(text)
                            }
                        })
                    }
                cell(resolvedLabel)
            }
            row("Snapshot search depth:") {
                spinner(1..10, 1)
                    .bindIntValue(::searchDepth)
            }
            row {
                checkBox("Auto-reload on file change")
                    .bindSelected(::autoReload)
            }
            row("Highlight color:") {
                val colorPreview = JLabel(ColorSwatchIcon(parseHexColor(highlightColor)))
                textField()
                    .bindText(::highlightColor)
                    .applyToComponent {
                        document.addDocumentListener(object : DocumentListener {
                            override fun insertUpdate(e: DocumentEvent) = updatePreview()
                            override fun removeUpdate(e: DocumentEvent) = updatePreview()
                            override fun changedUpdate(e: DocumentEvent) = updatePreview()
                            private fun updatePreview() {
                                (colorPreview.icon as ColorSwatchIcon).color = parseHexColor(text)
                                colorPreview.repaint()
                            }
                        })
                    }
                cell(colorPreview)
            }
            row("Code generation style:") {
                comboBox(listOf("Property", "Variable"))
                    .bindItem(::codeGenStyle.toNullableProperty())
            }
        }
    }
    private fun validatePattern(pattern: String): String {
        if (pattern.isBlank()) return "⚠ Pattern is empty"
        return try {
            val regex = Regex(pattern)
            val sampleFiles = listOf("example.page.ts", "ExamplePage.ts")
            for (sample in sampleFiles) {
                val match = regex.matchEntire(sample)
                if (match != null) {
                    val pageName = match.groupValues.getOrNull(1)
                    if (!pageName.isNullOrEmpty()) {
                        return "✓ \"$sample\" → page name: \"$pageName\""
                    }
                    return "⚠ No capture group — add (...) to extract page name"
                }
            }
            "✓ Valid (no match on samples)"
        } catch (_: Exception) {
            "✗ Invalid regex"
        }
    }

    private fun resolveSnapshotsRootHint(root: String): String {
        val basePath = project.basePath ?: return ""
        return "→ $basePath/$root"
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        Color.decode(hex)
    } catch (_: NumberFormatException) {
        Color.GRAY
    }
}

private class ColorSwatchIcon(var color: Color, private val size: Int = 16) : Icon {
    override fun getIconWidth() = size
    override fun getIconHeight() = size
    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        g.color = color
        g.fillRoundRect(x, y, size, size, 4, 4)
        g.color = Color.GRAY
        g.drawRoundRect(x, y, size - 1, size - 1, 4, 4)
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
