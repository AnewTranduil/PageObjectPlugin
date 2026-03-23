package com.github.artem.pageobjectplugin.services

import com.github.artem.pageobjectplugin.locators.PickerResultHandler
import com.github.artem.pageobjectplugin.model.SnapshotBundle
import com.github.artem.pageobjectplugin.settings.PageMirrorSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
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
                setupThemeListener()
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

    /** Seam for testing: replace with a capturing lambda to verify JS calls without JCEF. */
    internal var jsExecutor: (code: String) -> Unit = { code ->
        browser?.cefBrowser?.executeJavaScript(code, browser?.cefBrowser?.url ?: "", 0)
    }

    fun addSnapshotListener(listener: () -> Unit) {
        snapshotListeners.add(listener)
    }

    internal fun clearSnapshotListeners() {
        snapshotListeners.clear()
    }

    internal fun resetStateForTesting() {
        currentBundle = null
        snapshotDocument = null
        availableSnapshots = emptyList()
        snapshotListeners.clear()
    }

    fun updateAvailableSnapshots(bundles: List<SnapshotBundle>) {
        availableSnapshots = bundles
        snapshotListeners.forEach { it() }

        if (currentBundle == null && bundles.isNotEmpty()) {
            loadSnapshot(bundles.first())
        }
    }

    fun loadSnapshot(bundle: SnapshotBundle) {
        currentBundle = bundle

        try {
            val html = bundle.htmlPath.readText()
            val layout = bundle.layoutPath.readText()

            // Parse HTML with Jsoup for gutter validation
            snapshotDocument = Jsoup.parse(html)

            val escapedHtml = escapeForJs(html)
            val escapedLayout = escapeForJs(layout)

            jsExecutor("window.loadSnapshot($escapedHtml, $escapedLayout);")

            // Apply theme and highlight color to the loaded page
            applyTheme()
            applyHighlightColor()
        } catch (e: Exception) {
            try {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Page Mirror")
                    .createNotification(
                        "Failed to load snapshot: ${e.message}",
                        NotificationType.ERROR
                    )
                    .notify(project)
            } catch (_: Exception) { }
        }

        snapshotListeners.forEach { it() }

        // Restart annotations so gutter badges update with new snapshot data
        restartAnnotations()
    }

    private fun restartAnnotations() {
        ApplicationManager.getApplication().invokeLater {
            val analyzer = DaemonCodeAnalyzer.getInstance(project)
            val editorManager = FileEditorManager.getInstance(project)
            for (file in editorManager.openFiles) {
                val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(file) ?: continue
                analyzer.restart(psiFile)
            }
        }
    }

    fun highlightElement(selector: String) {
        val escapedSelector = escapeForJs(selector)
        jsExecutor("window.highlightElement($escapedSelector);")
    }

    fun clearHighlight() {
        jsExecutor("window.clearHighlight();")
    }

    fun applyTheme() {
        val isDark = LafManager.getInstance().currentUIThemeLookAndFeel?.isDark ?: false
        val theme = if (isDark) "dark" else "light"
        jsExecutor("window.setTheme && window.setTheme('$theme');")
    }

    fun applyHighlightColor() {
        val color = PageMirrorSettings.getInstance(project).state.highlightColor
        jsExecutor("window.setHighlightColor && window.setHighlightColor('$color');")
    }

    private fun setupThemeListener() {
        ApplicationManager.getApplication().messageBus.connect().subscribe(
            LafManagerListener.TOPIC,
            LafManagerListener { applyTheme() }
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
