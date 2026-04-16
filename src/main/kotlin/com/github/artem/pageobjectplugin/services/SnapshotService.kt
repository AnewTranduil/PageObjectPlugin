package com.github.artem.pageobjectplugin.services

import com.github.artem.pageobjectplugin.listeners.ScanResult
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
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private val LOG = logger<SnapshotService>()

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

    var isHighlightActive: Boolean = false
        private set

    var isInspectModeActive: Boolean = false

    var isHighlightAllActive: Boolean = false

    /**
     * Mirrors the most recent outdated-bundle banner state emitted to
     * JCEF. Queryable from UI tests via the plugin classloader bridge
     * the same way `isHighlightAllActive` is — keeps assertions off
     * the JS side so we don't need a JCEF DOM probe.
     */
    var isOutdatedBannerActive: Boolean = false
        private set

    private var jsQuery: JBCefJSQuery? = null
    private val snapshotListeners = mutableListOf<() -> Unit>()
    private var onPageReadyCallback: (() -> Unit)? = null

    /** Seam for testing: replace with a capturing lambda to verify JS calls without JCEF. */
    internal var jsExecutor: (code: String) -> Unit = { code ->
        browser?.cefBrowser?.executeJavaScript(code, browser?.cefBrowser?.url ?: "", 0)
    }

    fun addSnapshotListener(listener: () -> Unit) {
        snapshotListeners.add(listener)
    }

    fun onPageReady(callback: () -> Unit) {
        onPageReadyCallback = callback
    }

    internal fun clearSnapshotListeners() {
        snapshotListeners.clear()
    }

    internal fun resetStateForTesting() {
        currentBundle = null
        snapshotDocument = null
        availableSnapshots = emptyList()
        snapshotListeners.clear()
        isHighlightAllActive = false
        isOutdatedBannerActive = false
    }

    /**
     * UI-test seam: drive the outdated-bundle banner directly, without
     * touching [availableSnapshots] or [currentBundle]. The routing
     * from `ScanResult.rejected` into this banner state is covered by
     * `SnapshotServiceTest`; this seam exists only so
     * `OutdatedBundleBannerUiTest` can exercise the JCEF rendering
     * path under a real sandboxed IDE without polluting snapshot
     * state that the next UI-test class's `@BeforeAll` depends on.
     *
     * Public, not `internal`, because the Remote Robot JS bridge
     * looks the method up by its plain JVM name — `internal` would
     * mangle it into `…ForTesting$production_sources_for_module_<hash>`
     * and break the bridge lookup.
     */
    fun simulateRejectedBundlesForTesting(declaredVersions: List<Int>) {
        if (declaredVersions.isEmpty()) {
            jsExecutor("window.hideOutdatedBanner();")
            isOutdatedBannerActive = false
            return
        }
        val versionsJson = declaredVersions.distinct().sorted().joinToString(",", "[", "]")
        val count = declaredVersions.size
        jsExecutor("window.showOutdatedBanner({count:$count,versions:$versionsJson});")
        isOutdatedBannerActive = true
    }

    /**
     * UI-test seam: hide the outdated-bundle banner. Public for the
     * same JS-bridge reason as [simulateRejectedBundlesForTesting].
     */
    fun simulateCleanScanForTesting() {
        jsExecutor("window.hideOutdatedBanner();")
        isOutdatedBannerActive = false
    }

    /**
     * Canonical entry point used by the discovery listener. Receives
     * both the loaded bundles and the rejected-bundle records so the
     * tool window can drive the outdated-bundle banner.
     */
    fun updateAvailableSnapshots(result: ScanResult) {
        val bundles = result.loaded
        LOG.info(
            "updateAvailableSnapshots: ${bundles.size} loaded, " +
                "${result.rejected.size} rejected"
        )
        bundles.forEach { LOG.info("  bundle: ${it.htmlPath}") }
        result.rejected.forEach {
            LOG.info("  rejected: ${it.dir} (declared v${it.declaredVersion})")
        }
        availableSnapshots = bundles
        snapshotListeners.forEach { it() }

        // Drive the outdated-bundle banner in the tool window. The
        // banner is a pure function of the latest scan — it appears
        // when any bundle under the scanned directory declared an
        // unsupported version, and disappears as soon as the next scan
        // is clean (e.g. after the user regenerates bundles).
        if (result.rejected.isNotEmpty()) {
            val versions = result.rejected
                .map { it.declaredVersion }
                .distinct()
                .sorted()
                .joinToString(",", "[", "]")
            val count = result.rejected.size
            jsExecutor("window.showOutdatedBanner({count:$count,versions:$versions});")
            isOutdatedBannerActive = true
        } else {
            jsExecutor("window.hideOutdatedBanner();")
            isOutdatedBannerActive = false
        }

        if (bundles.isEmpty()) {
            clearSnapshot()
        } else if (currentBundle == null) {
            LOG.info("No current bundle, auto-loading first: ${bundles.first().htmlPath}")
            loadSnapshot(bundles.first())
        }
    }

    /**
     * Backwards-compatible convenience for callers that don't carry
     * rejection information. Delegates to the [ScanResult] overload
     * with an empty rejection list.
     */
    fun updateAvailableSnapshots(bundles: List<SnapshotBundle>) {
        updateAvailableSnapshots(ScanResult(bundles, emptyList()))
    }

    fun clearSnapshot() {
        LOG.info("clearSnapshot: resetting to empty state")
        currentBundle = null
        snapshotDocument = null
        jsExecutor("window.clearSnapshot();")
        restartAnnotations()
    }

    fun loadSnapshot(bundle: SnapshotBundle) {
        LOG.info("loadSnapshot: ${bundle.htmlPath}")
        currentBundle = bundle

        try {
            // Load the v2 bundle HTML with sidecar CSS inlined. The
            // plugin renders via `iframe.srcdoc = html`, which has an
            // `about:srcdoc` base URL and cannot resolve relative
            // `resources/*.css` references on its own.
            val html = SnapshotHtmlResolver.loadResolved(bundle.htmlPath, bundle.resourcesDir)
            LOG.info("Read HTML (${html.length} chars))")

            // Parse HTML with Jsoup for gutter validation
            snapshotDocument = Jsoup.parse(html)

            val escapedHtml = escapeForJs(html)

            LOG.info("Executing window.loadSnapshot via jsExecutor, browser=${browser != null}, cefBrowser=${browser?.cefBrowser != null}")
            jsExecutor("window.loadSnapshot($escapedHtml);")

            // Apply theme and highlight color to the loaded page
            applyTheme()
            applyHighlightColor()
            LOG.info("loadSnapshot complete")
        } catch (e: Exception) {
            LOG.error("loadSnapshot failed", e)
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

    fun highlightElement(type: String, value: String) {
        val escapedType = escapeForJs(type)
        val escapedValue = escapeForJs(value)
        jsExecutor("window.highlightElement($escapedType, $escapedValue);")
        isHighlightActive = true
    }

    fun clearHighlight() {
        jsExecutor("window.clearHighlight();")
        isHighlightActive = false
        isHighlightAllActive = false
    }

    fun highlightAllLocators(locators: List<com.github.artem.pageobjectplugin.locators.ExtractedLocator>) {
        val json = locators.joinToString(",", "[", "]") { loc ->
            """{"type":${escapeForJs(loc.type)},"value":${escapeForJs(loc.value)}}"""
        }
        jsExecutor("window.highlightAll($json);")
        isHighlightAllActive = true
        isHighlightActive = true
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
        LOG.info("setupJsQuery: registering JBCefJSQuery and load handler")
        val query = JBCefJSQuery.create(browser as JBCefBrowserBase)
        query.addHandler { jsonString ->
            ApplicationManager.getApplication().invokeLater {
                PickerResultHandler(project).handlePickerResult(jsonString)
            }
            JBCefJSQuery.Response("")
        }
        jsQuery = query

        // Inject the query bridge after page loads, then notify that the page is ready
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                LOG.info("onLoadEnd: url=${cefBrowser?.url}, isMain=${frame?.isMain}, status=$httpStatusCode")
                if (frame?.isMain == true) {
                    val injection = query.inject("json")
                    cefBrowser?.executeJavaScript(
                        "window.__pickerCallback = function(json) { $injection };",
                        cefBrowser.url,
                        0
                    )
                    LOG.info("onLoadEnd: JS bridge injected, invoking onPageReadyCallback (registered=${onPageReadyCallback != null})")
                    onPageReadyCallback?.invoke()
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
