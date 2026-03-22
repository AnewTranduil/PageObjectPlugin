package com.github.artem.pageobjectplugin

import com.github.artem.pageobjectplugin.listeners.SnapshotDiscoveryListener
import com.github.artem.pageobjectplugin.listeners.SnapshotWatcher
import com.github.artem.pageobjectplugin.model.SnapshotBundle
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComboBox
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
        val htmlUrl = javaClass.getResource("/html/page-mirror.html")
        if (htmlUrl != null) {
            browser.loadURL(htmlUrl.toExternalForm())
        }

        val service = SnapshotService.getInstance(project)
        service.browser = browser

        // Build toolbar
        val comboModel = DefaultComboBoxModel<SnapshotBundle>()
        val comboBox = JComboBox(comboModel).apply {
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

        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(comboBox)
            add(refreshButton)
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

        // Start file watcher
        val watcher = SnapshotWatcher(project)
        watcher.start()
        val disposable = Disposer.newDisposable("PageMirrorToolWindow")
        Disposer.register(disposable, watcher)

        val content = ContentFactory.getInstance().createContent(mainPanel, "", false).apply {
            setDisposer(disposable)
        }
        toolWindow.contentManager.addContent(content)

        // Trigger initial discovery from currently open files
        refreshSnapshots(project, service)
    }

    private fun refreshSnapshots(project: Project, service: SnapshotService) {
        val openFiles = FileEditorManager.getInstance(project).openFiles
        val tsFile = openFiles.firstOrNull { it.name.endsWith(".ts") || it.name.endsWith(".tsx") }

        if (tsFile != null) {
            val bundles = SnapshotDiscoveryListener.discoverSnapshots(tsFile.toNioPath())
            service.updateAvailableSnapshots(bundles)
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
