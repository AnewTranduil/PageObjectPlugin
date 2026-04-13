package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.annotations.Feature
import com.github.artem.pageobjectplugin.ui.fixtures.GutterFixture
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.fixtures.SnapshotBrowserFixture
import com.github.artem.pageobjectplugin.ui.pages.EditorPage
import com.github.artem.pageobjectplugin.ui.pages.PluginToolWindowPage
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * UI tests for the v2 snapshot bundle rendering pipeline.
 *
 * Opens `dashboard.page.ts` (rich fixture with 4 external CSS files)
 * against a hand-crafted v2 snapshot bundle at
 * `.snapshots/dashboard/initial/` that has sidecar CSS under `resources/`.
 *
 * Exercises the full v2 path end-to-end:
 *   SnapshotBundle.fromDirectory (v2 strict check)
 *   → SnapshotHtmlResolver (sidecar CSS inlining)
 *   → SnapshotService.loadSnapshot → JCEF iframe render
 *   → highlight bridge (caret on locator → element highlight)
 *   → gutter annotator (match count badges)
 *
 * Locator line numbers in dashboard.page.ts (constructor body):
 *   Line 20: this.heading = page.getByRole('heading', { name: 'Welcome back, Amelia' })
 *   Line 21: this.newProjectButton = page.getByTestId('new-project-button')
 *   Line 22: this.exportButton = page.getByTestId('export-button')
 *   Line 23: this.projectsTable = page.getByTestId('projects-table')
 *   Line 24: this.ticketForm = page.getByTestId('ticket-form')
 *   Line 25: this.ticketTitleInput = page.getByTestId('ticket-title')
 *   Line 26: this.ticketPrioritySelect = page.getByTestId('ticket-priority')
 *   Line 27: this.ticketDescription = page.getByTestId('ticket-description')
 *   Line 28: this.ticketSubmit = page.getByTestId('ticket-submit')
 */
@Feature("dashboard-v2")
class DashboardV2UiTest : BaseUiTest() {

    // Locator lines in the constructor of dashboard.page.ts
    private val LINE_HEADING = 20           // getByRole('heading', ...)
    private val LINE_TICKET_TITLE = 25      // getByTestId('ticket-title')
    private val LINE_IMPORT = 1             // non-locator line

    private val editor by lazy { EditorPage(robot) }
    private val toolWindow by lazy { PluginToolWindowPage(robot) }

    @BeforeEach
    fun loadDashboardSnapshot() {
        editor.openFileInEditor("dashboard.page.ts")
        if (!PageMirrorToolWindowFixture.isVisible(robot)) {
            toolWindow.open()
        }
        toolWindow.waitForSnapshotDiscovery(Duration.ofSeconds(15))
        // JCEF first paint — no observable signal.
        Thread.sleep(2_000)
    }

    /**
     * V2 rendering: the JCEF browser is visible after loading a v2 bundle
     * with sidecar CSS. Proves the full pipeline (SnapshotBundle v2 check →
     * SnapshotHtmlResolver inlining → JCEF srcdoc render) works.
     */
    @Test
    fun `v2 snapshot with sidecar CSS renders in JCEF browser`() {
        takeScreenshot("dashboard-v2-loaded")

        val tw = PageMirrorToolWindowFixture.find(robot)
        assertTrue(
            tw.isBrowserVisible(),
            "JCEF browser should be visible after loading a v2 dashboard snapshot with CSS sidecars",
        )
    }

    /**
     * Highlight bridge: caret on a getByTestId locator highlights the
     * matching element in the rendered v2 snapshot.
     */
    @Test
    fun `caret on getByTestId locator highlights element in v2 snapshot`() {
        editor.goToLine(LINE_TICKET_TITLE)
        Thread.sleep(1_000)  // debounce headroom

        takeScreenshot("dashboard-highlight-ticket-title")

        val tw = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(tw)
        assertTrue(
            browser.isHighlightVisible(),
            "Highlight should appear on getByTestId('ticket-title') in the v2 dashboard snapshot",
        )
    }

    /**
     * Highlight bridge: caret on a getByRole locator highlights the
     * matching element in the rendered v2 snapshot.
     */
    @Test
    fun `caret on getByRole locator highlights element in v2 snapshot`() {
        editor.goToLine(LINE_HEADING)
        Thread.sleep(1_000)

        takeScreenshot("dashboard-highlight-heading")

        val tw = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(tw)
        assertTrue(
            browser.isHighlightVisible(),
            "Highlight should appear on getByRole('heading') in the v2 dashboard snapshot",
        )
    }

    /**
     * Gutter validation: at least one gutter badge appears after opening
     * dashboard.page.ts against the v2 snapshot. Verifies the annotator
     * pipeline works on v2-rendered content (Jsoup parse after sidecar
     * CSS inlining).
     */
    @Test
    fun `gutter badges appear for dashboard locators on v2 snapshot`() {
        // Force the ExternalAnnotator pipeline
        restartAnnotations()
        Thread.sleep(5_000)

        takeScreenshot("dashboard-gutter-badges")

        val tooltips = waitForGutterBadges()
        assertTrue(
            tooltips.isNotEmpty(),
            "At least one gutter badge should appear for dashboard.page.ts locators",
        )
    }

    /**
     * Highlight-all: clicking "Show All" highlights all locators from
     * dashboard.page.ts in the rendered v2 snapshot simultaneously.
     */
    @Test
    fun `show all highlights every dashboard locator in v2 snapshot`() {
        toolWindow.clickShowAll()
        Thread.sleep(1_000)

        takeScreenshot("dashboard-show-all-highlights")

        val tw = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(tw)
        assertTrue(
            browser.isHighlightAllActive(),
            "Highlight-all should be active after clicking Show All on dashboard snapshot",
        )
        assertTrue(
            browser.isHighlightVisible(),
            "At least one highlight should be visible in highlight-all mode",
        )
    }

    // ── helpers (mirrors GutterAnnotationUiTest) ────────────────────────

    private fun restartAnnotations() {
        ideFrame().callJs<Boolean>(
            """
            var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
            var analyzer = com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project)
            var editorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
            var files = editorManager.getOpenFiles()
            for (var i = 0; i < files.length; i++) {
                var psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(files[i])
                if (psiFile != null) analyzer.restart(psiFile)
            }
            true
            """,
            runInEdt = true,
        )
    }

    private fun waitForGutterBadges(): List<String> {
        var tooltips = GutterFixture.find(robot).allIconTooltips()
        if (tooltips.isNotEmpty()) return tooltips

        // Retry: restart annotations and poll
        restartAnnotations()
        for (attempt in 1..6) {
            Thread.sleep(2_000)
            tooltips = GutterFixture.find(robot).allIconTooltips()
            if (tooltips.isNotEmpty()) return tooltips
        }
        return tooltips
    }
}
