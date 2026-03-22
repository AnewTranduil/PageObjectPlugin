package com.github.artem.pageobjectplugin.services

import com.github.artem.pageobjectplugin.locators.PickerResultHandler
import com.github.artem.pageobjectplugin.model.SnapshotBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import kotlin.io.path.readText

@Service(Service.Level.PROJECT)
class SnapshotService(private val project: Project) {

    var browser: JBCefBrowser? = null
        set(value) {
            field = value
            if (value != null) {
                setupJsQuery(value)
            }
        }

    var currentBundle: SnapshotBundle? = null
        private set

    var availableSnapshots: List<SnapshotBundle> = emptyList()
        private set

    var snapshotDocument: Document? = null
        private set

    private var jsQuery: JBCefJSQuery? = null
    private val snapshotListeners = mutableListOf<() -> Unit>()

    fun addSnapshotListener(listener: () -> Unit) {
        snapshotListeners.add(listener)
    }

    fun updateAvailableSnapshots(bundles: List<SnapshotBundle>) {
        availableSnapshots = bundles
        snapshotListeners.forEach { it() }

        if (currentBundle == null && bundles.isNotEmpty()) {
            loadSnapshot(bundles.first())
        }
    }

    fun loadSnapshot(bundle: SnapshotBundle) {
        val browser = this.browser ?: return
        currentBundle = bundle

        val html = bundle.htmlPath.readText()
        val layout = bundle.layoutPath.readText()

        // Parse HTML with Jsoup for gutter validation
        snapshotDocument = Jsoup.parse(html)

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

    private fun setupJsQuery(browser: JBCefBrowser) {
        val query = JBCefJSQuery.create(browser)
        query.addHandler { jsonString ->
            ApplicationManager.getApplication().invokeLater {
                PickerResultHandler(project).handlePickerResult(jsonString)
            }
            JBCefJSQuery.Response("")
        }
        jsQuery = query

        // Inject the query bridge after page loads
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    val injection = query.inject("json")
                    cefBrowser?.executeJavaScript(
                        "window.__pickerCallback = function(json) { $injection };",
                        cefBrowser.url,
                        0
                    )
                }
            }
        }, browser.cefBrowser)
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
