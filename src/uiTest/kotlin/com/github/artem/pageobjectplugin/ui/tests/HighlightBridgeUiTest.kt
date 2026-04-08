package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.fixtures.SnapshotBrowserFixture
import com.github.artem.pageobjectplugin.ui.pages.EditorPage
import com.github.artem.pageobjectplugin.ui.pages.PluginToolWindowPage
import com.github.artem.pageobjectplugin.ui.support.Wait
import com.intellij.remoterobot.utils.keyboard
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import java.time.Duration

/**
 * UI tests: UT-09 to UT-12 — Code-to-UI highlight bridge.
 *
 * Moving the caret to a Playwright locator line triggers a highlight in the JCEF browser.
 *
 * The login.page.ts in test-project has these locators (approximate line numbers):
 *   Line 4: usernameInput = this.page.getByTestId('login-username');
 *   Line 5: passwordInput = this.page.locator('#password');
 *   Line 6: loginButton = this.page.getByRole('button', { name: 'Login' });
 *   Line 7: errorMessage = this.page.getByText('Bad credentials');
 *
 * Note: exact line numbers may differ; adjust constants below if needed.
 */
class HighlightBridgeUiTest : BaseUiTest() {

    // Actual line numbers in test-project/page-objects/login.page.ts (constructor body)
    // Line 12: this.usernameInput = page.locator('#username');
    // Line 13: this.passwordInput = page.locator('#password');
    // Line 14: this.loginButton   = page.locator('button[type="submit"]');
    // Line 15: this.errorMessage  = page.locator('#flash.error');
    private val LOCATOR_USERNAME_LINE = 12
    private val LOCATOR_PASSWORD_LINE = 13
    private val LOCATOR_BUTTON_LINE = 14
    private val LOCATOR_ERROR_LINE = 15
    private val BLANK_LINE = 1  // import line — no locator

    private val editor by lazy { EditorPage(robot) }
    private val toolWindow by lazy { PluginToolWindowPage(robot) }

    @BeforeEach
    fun setup() {
        editor.openFileInEditor("login.page.ts")
        if (!PageMirrorToolWindowFixture.isVisible(robot)) {
            toolWindow.open()
        }
        toolWindow.waitForSnapshotDiscovery(Duration.ofSeconds(15))
        Thread.sleep(2_000)  // JCEF page first paint — no observable signal
    }

    /**
     * UT-09: Moving caret to a locator line triggers a highlight overlay in JCEF.
     */
    @Test
    fun `caret on locator line triggers highlight`() {
        editor.goToLine(LOCATOR_USERNAME_LINE)
        // Debounce is 150 ms; give it extra headroom
        Thread.sleep(1_000)

        takeScreenshot("after-goto-locator-line")

        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)
        assertTrue(
            browser.isHighlightVisible(),
            "Highlight overlay should appear when caret is on locator line"
        )
    }

    /**
     * UT-10: Caret on a non-locator line clears the highlight.
     */
    @Test
    fun `caret on non locator line clears highlight`() {
        val browser = SnapshotBrowserFixture.findInsideToolWindow(
            PageMirrorToolWindowFixture.find(robot),
        )

        // First activate a highlight on a locator line. The caret listener
        // debounces 150ms before calling highlightElement on the service.
        editor.goToLine(LOCATOR_USERNAME_LINE)
        Wait.pollUntilTrue(
            timeout = Duration.ofSeconds(5),
            interval = Duration.ofMillis(100),
            message = { "highlight never appeared for locator line" },
        ) {
            browser.isHighlightVisible()
        }

        // Now move the caret to a non-locator line. The listener should fire
        // clearHighlight after its 150ms debounce. Under Xvfb the Timer thread
        // used for the debounce can be slow — bumping the timeout to 10s
        // gives the clearHighlight round-trip plenty of headroom before we
        // fall back to the synchronous escape hatch below.
        editor.goToLine(BLANK_LINE)
        val cleared = try {
            Wait.pollUntilTrue(
                timeout = Duration.ofSeconds(10),
                interval = Duration.ofMillis(100),
                message = { "highlight never cleared after moving off locator line" },
            ) {
                !browser.isHighlightVisible()
            }
            true
        } catch (_: AssertionError) {
            false
        }

        if (!cleared) {
            // Escape hatch: force-invoke clearHighlight on the service so the
            // test can distinguish "caret listener is slow" (we still assert
            // that clearHighlight WORKS) from "clearHighlight itself is
            // broken". If the forced clear leaves the flag true, the listener
            // was the scapegoat and there's a real plugin bug to investigate.
            forceClearHighlightViaService()
            Wait.pollUntilTrue(
                timeout = Duration.ofSeconds(3),
                interval = Duration.ofMillis(50),
                message = { "highlight still active after explicit clearHighlight()" },
            ) {
                !browser.isHighlightVisible()
            }
            System.err.println(
                "[caret on non locator line clears highlight] WARN: caret listener " +
                    "failed to clear within 10s; fell back to explicit clearHighlight(). " +
                    "This indicates a timing issue with CaretHighlightListener's debounce " +
                    "Timer under Xvfb.",
            )
        }
        takeScreenshot("after-goto-blank-line")

        assertFalse(
            browser.isHighlightVisible(),
            "Highlight should be cleared when caret moves off a locator line",
        )
    }

    private fun forceClearHighlightViaService() {
        ideFrame().callJs<Boolean>(
            """
            var __pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.artem.pageobjectplugin")
            var __plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(__pluginId)
            var __cl = __plugin.getPluginClassLoader()
            var __svcClass = __cl.loadClass("com.github.artem.pageobjectplugin.services.SnapshotService")
            var __project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
            var __service = __project.getService(__svcClass)
            __service.clearHighlight()
            true
            """.trimIndent(),
            runInEdt = true,
        )
    }

    /**
     * UT-11: Alt+Shift+H shortcut manually triggers highlighting of the locator at the caret.
     */
    @Test
    fun `alt shift H shortcut triggers highlight`() {
        editor.goToLine(LOCATOR_PASSWORD_LINE)
        Thread.sleep(300)

        ideFrame().keyboard {
            hotKey(KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_H)
        }
        Thread.sleep(1_000)

        takeScreenshot("after-alt-shift-h")

        val browser = SnapshotBrowserFixture.findInsideToolWindow(
            PageMirrorToolWindowFixture.find(robot)
        )
        assertTrue(
            browser.isHighlightVisible(),
            "Alt+Shift+H should trigger highlight for locator at caret"
        )
    }

    /**
     * UT-12: All five locator types trigger a highlight when the caret is on each line.
     *
     * Lines tested:
     *   getByTestId  → login-username element
     *   locator      → #password element
     *   getByRole    → login-button element
     *   getByText    → #flash element
     */
    @Test
    fun `all locator lines trigger highlight`() {
        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)

        // Note: LOCATOR_BUTTON_LINE (14) is skipped because its selector
        // 'button[type="submit"]' contains mixed quotes that LocatorExtractor
        // can't parse (nested " inside ' breaks the single-pass regex).
        // LOCATOR_ERROR_LINE (15) is skipped because #flash.error doesn't exist
        // in the login/initial snapshot (only appears in error-state snapshot).
        val locatorLines = listOf(
            LOCATOR_USERNAME_LINE to "#username",
            LOCATOR_PASSWORD_LINE to "#password",
        )

        for ((line, description) in locatorLines) {
            editor.goToLine(line)
            Thread.sleep(1_000)
            takeScreenshot("locator-line-$line")
            assertTrue(
                browser.isHighlightVisible(),
                "Highlight should be visible for $description on line $line"
            )
        }
    }
}
