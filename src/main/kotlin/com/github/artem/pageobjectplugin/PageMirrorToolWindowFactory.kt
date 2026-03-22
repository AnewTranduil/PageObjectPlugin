package com.github.artem.pageobjectplugin

import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.components.JBLabel
import com.intellij.ui.content.ContentFactory
import javax.swing.SwingConstants

class PageMirrorToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = if (JBCefApp.isSupported()) {
            val browser = JBCefBrowser()
            val htmlUrl = javaClass.getResource("/html/page-mirror.html")
            if (htmlUrl != null) {
                browser.loadURL(htmlUrl.toExternalForm())
            } else {
                browser.loadHTML("<html><body style='background:#1e1e1e;color:#ccc;font-family:monospace;padding:20px;'>page-mirror.html not found</body></html>")
            }
            SnapshotService.getInstance(project).browser = browser
            ContentFactory.getInstance().createContent(browser.component, "", false)
        } else {
            val label = JBLabel("JCEF is not supported in this environment", SwingConstants.CENTER)
            ContentFactory.getInstance().createContent(label, "", false)
        }
        toolWindow.contentManager.addContent(content)
    }
}
