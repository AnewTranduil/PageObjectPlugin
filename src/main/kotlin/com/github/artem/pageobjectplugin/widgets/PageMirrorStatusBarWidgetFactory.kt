package com.github.artem.pageobjectplugin.widgets

import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Consumer
import java.awt.Component
import java.awt.event.MouseEvent

class PageMirrorStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = "PageMirrorStatus"

    override fun getDisplayName(): String = "Page Mirror"

    override fun createWidget(project: Project): StatusBarWidget = PageMirrorStatusBarWidget(project)

    private class PageMirrorStatusBarWidget(private val project: Project) :
        StatusBarWidget, StatusBarWidget.TextPresentation {

        private var statusBar: StatusBar? = null

        override fun ID(): String = "PageMirrorStatus"

        override fun install(statusBar: StatusBar) {
            this.statusBar = statusBar
            val service = SnapshotService.getInstance(project)
            service.addSnapshotListener {
                statusBar.updateWidget(ID())
            }
        }

        override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

        override fun getText(): String {
            val service = SnapshotService.getInstance(project)
            val bundle = service.currentBundle ?: return "Page Mirror: No snapshot"
            val parent = bundle.htmlPath.parent
            val grandparent = parent.parent
            val name = if (grandparent != null && grandparent.fileName.toString() != ".snapshots") {
                "${grandparent.fileName}/${parent.fileName}"
            } else {
                parent.fileName.toString()
            }
            return "Page Mirror: $name"
        }

        override fun getTooltipText(): String = "Click to open Page Mirror tool window"

        override fun getAlignment(): Float = Component.CENTER_ALIGNMENT

        override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
            ToolWindowManager.getInstance(project).getToolWindow("Page Mirror")?.show()
        }

        override fun dispose() {
            statusBar = null
        }
    }
}
