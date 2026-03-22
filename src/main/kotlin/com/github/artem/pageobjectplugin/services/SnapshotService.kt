package com.github.artem.pageobjectplugin.services

import com.github.artem.pageobjectplugin.model.SnapshotBundle
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import kotlin.io.path.readText

@Service(Service.Level.PROJECT)
class SnapshotService(private val project: Project) {

    var browser: JBCefBrowser? = null
    var currentBundle: SnapshotBundle? = null
        private set

    var availableSnapshots: List<SnapshotBundle> = emptyList()
        private set

    private val snapshotListeners = mutableListOf<() -> Unit>()

    fun addSnapshotListener(listener: () -> Unit) {
        snapshotListeners.add(listener)
    }

    fun updateAvailableSnapshots(bundles: List<SnapshotBundle>) {
        availableSnapshots = bundles
        snapshotListeners.forEach { it() }

        // Auto-load the first snapshot if none is currently loaded
        if (currentBundle == null && bundles.isNotEmpty()) {
            loadSnapshot(bundles.first())
        }
    }

    fun loadSnapshot(bundle: SnapshotBundle) {
        val browser = this.browser ?: return
        currentBundle = bundle

        val html = bundle.htmlPath.readText()
        val layout = bundle.layoutPath.readText()

        val escapedHtml = escapeForJs(html)
        val escapedLayout = escapeForJs(layout)

        browser.cefBrowser.executeJavaScript(
            "window.loadSnapshot($escapedHtml, $escapedLayout);",
            browser.cefBrowser.url,
            0
        )

        snapshotListeners.forEach { it() }
    }

    fun highlightElement(selector: String) {
        val browser = this.browser ?: return
        val escapedSelector = escapeForJs(selector)
        browser.cefBrowser.executeJavaScript(
            "window.highlightElement($escapedSelector);",
            browser.cefBrowser.url,
            0
        )
    }

    fun clearHighlight() {
        val browser = this.browser ?: return
        browser.cefBrowser.executeJavaScript(
            "window.clearHighlight();",
            browser.cefBrowser.url,
            0
        )
    }

    private fun escapeForJs(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
            .replace("<", "\\x3c")
            .replace(">", "\\x3e")
        return "\"$escaped\""
    }

    companion object {
        fun getInstance(project: Project): SnapshotService = project.service()
    }
}
