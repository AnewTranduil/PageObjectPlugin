package com.github.artem.pageobjectplugin.actions

import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ToggleInspectAction : AnAction("Toggle Inspect Mode") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = SnapshotService.getInstance(project)
        val browser = service.browser ?: return

        service.isInspectModeActive = !service.isInspectModeActive
        browser.cefBrowser.executeJavaScript(
            "window.toggleInspectMode();",
            browser.cefBrowser.url,
            0
        )
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
            SnapshotService.getInstance(project).currentBundle != null
    }
}
