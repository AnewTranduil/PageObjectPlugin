package com.github.artem.pageobjectplugin

import com.github.artem.pageobjectplugin.listeners.CaretHighlightListener
import com.github.artem.pageobjectplugin.listeners.SnapshotDiscoveryListener
import com.github.artem.pageobjectplugin.listeners.SnapshotWatcher
import com.github.artem.pageobjectplugin.locators.LocatorExtractor
import com.github.artem.pageobjectplugin.model.SnapshotBundle
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingConstants

class PageMirrorToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        if (!JBCefApp.isSupported()) {
            val label = JBLabel("JCEF is not supported in this environment", SwingConstants.CENTER)
            val content = ContentFactory.getInstance().createContent(label, "", false)
            toolWindow.contentManager.addContent(content)
            return
        }

        val browser = JBCefBrowser()

        val service = SnapshotService.getInstance(project)
        service.onPageReady { refreshSnapshots(project, service) }
        service.browser = browser

        // Build toolbar
        val comboModel = DefaultComboBoxModel<SnapshotBundle>()
        val comboBox = ComboBox(comboModel).apply {
            renderer = SnapshotComboRenderer()
            addActionListener {
                val selected = selectedItem as? SnapshotBundle ?: return@addActionListener
                service.loadSnapshot(selected)
            }
        }

        val refreshButton = JButton("Refresh").apply {
            addActionListener {
                refreshSnapshots(project, service)
            }
        }

        val inspectButton = JButton("Pick").apply {
            toolTipText = "Toggle element picker mode"
            addActionListener {
                val cefBrowser = browser.cefBrowser
                service.isInspectModeActive = !service.isInspectModeActive
                cefBrowser.executeJavaScript("window.toggleInspectMode();", cefBrowser.url, 0)
                text = if (service.isInspectModeActive) "Pick *" else "Pick"
            }
        }

        val showAllButton = JButton("Show All").apply {
            toolTipText = "Highlight all locators in the current file"
            addActionListener {
                if (service.isHighlightAllActive) {
                    service.clearHighlight()
                    text = "Show All"
                } else {
                    val locators = collectLocatorsFromEditor(project)
                    if (locators.isNotEmpty()) {
                        service.highlightAllLocators(locators)
                        text = "Show All *"
                    }
                }
            }
        }

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(comboBox)
            add(refreshButton)
            add(inspectButton)
            add(showAllButton)
        }

        val mainPanel = JPanel(BorderLayout()).apply {
            add(toolbar, BorderLayout.NORTH)
            add(browser.component, BorderLayout.CENTER)
        }

        // Listen for snapshot list changes
        service.addSnapshotListener {
            val snapshots = service.availableSnapshots
            val current = service.currentBundle
            comboModel.removeAllElements()
            for (bundle in snapshots) {
                comboModel.addElement(bundle)
            }
            if (current != null && snapshots.contains(current)) {
                comboBox.selectedItem = current
            }
        }

        // Start file watcher and caret listener
        val watcher = SnapshotWatcher(project)
        watcher.start()
        val caretListener = CaretHighlightListener(project)
        caretListener.register()
        val disposable = Disposer.newDisposable("PageMirrorToolWindow")
        Disposer.register(disposable, watcher)
        Disposer.register(disposable, caretListener)

        val content = ContentFactory.getInstance().createContent(mainPanel, "", false).apply {
            setDisposer(disposable)
        }
        toolWindow.contentManager.addContent(content)

        // Load the shell page AFTER all handlers are registered so onLoadEnd is caught
        val htmlContent = assemblePageMirrorHtml()
        if (htmlContent != null) {
            browser.loadHTML(htmlContent)
        }
    }

    private fun collectLocatorsFromEditor(project: Project): List<com.github.artem.pageobjectplugin.locators.ExtractedLocator> {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return emptyList()
        val document = editor.document
        val locators = mutableListOf<com.github.artem.pageobjectplugin.locators.ExtractedLocator>()
        for (line in 0 until document.lineCount) {
            val lineStart = document.getLineStartOffset(line)
            val lineEnd = document.getLineEndOffset(line)
            val lineText = document.getText(TextRange(lineStart, lineEnd))
            val locator = LocatorExtractor.extract(lineText)
            if (locator != null) {
                locators.add(locator)
            }
        }
        return locators
    }

    private fun assemblePageMirrorHtml(): String? {
        val template = javaClass.getResourceAsStream("/html/page-mirror.html")
            ?.bufferedReader()?.readText() ?: return null
        val jsFiles = listOf("snapshot", "query", "highlight", "inspect", "theme")
        val jsBundle = jsFiles.joinToString("\n\n") { name ->
            javaClass.getResourceAsStream("/html/js/$name.js")
                ?.bufferedReader()?.readText() ?: ""
        }
        return template.replace("/* __JS_BUNDLE__ */", jsBundle)
    }

    private fun refreshSnapshots(project: Project, service: SnapshotService) {
        val log = logger<PageMirrorToolWindowFactory>()
        val openFiles = FileEditorManager.getInstance(project).openFiles
        log.info("refreshSnapshots: ${openFiles.size} open file(s): ${openFiles.map { it.name }}")
        val tsFile = openFiles.firstOrNull { it.name.endsWith(".ts") || it.name.endsWith(".tsx") }

        if (tsFile != null) {
            log.info("refreshSnapshots: discovering from ${tsFile.path}")
            val bundles = SnapshotDiscoveryListener.discoverSnapshots(tsFile.toNioPath())
            log.info("refreshSnapshots: discovered ${bundles.size} bundle(s)")
            service.updateAvailableSnapshots(bundles)
        } else {
            log.info("refreshSnapshots: no .ts/.tsx file open, skipping discovery")
        }
    }

    private class SnapshotComboRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            val bundle = value as? SnapshotBundle
            val displayName = if (bundle != null) {
                val parent = bundle.htmlPath.parent
                val grandparent = parent.parent
                if (grandparent != null && grandparent.fileName.toString() != ".snapshots") {
                    "${grandparent.fileName}/${parent.fileName}"
                } else {
                    parent.fileName.toString()
                }
            } else {
                "No snapshots"
            }
            return super.getListCellRendererComponent(list, displayName, index, isSelected, cellHasFocus)
        }
    }
}
