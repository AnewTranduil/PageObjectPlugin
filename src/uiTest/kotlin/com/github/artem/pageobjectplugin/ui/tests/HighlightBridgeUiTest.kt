package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.fixtures.SnapshotBrowserFixture
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

    // Approximate line numbers in test-project/page-objects/login.page.ts
    private val GET_BY_TEST_ID_LINE = 4
    private val LOCATOR_LINE = 5
    private val GET_BY_ROLE_LINE = 6
    private val GET_BY_TEXT_LINE = 7
    private val BLANK_LINE = 1  // import line / blank — no locator

    @BeforeEach
    fun setup() {
        openFileInEditor("login.page.ts")
        Thread.sleep(1_000)
        if (!PageMirrorToolWindowFixture.isVisible(robot)) {
            openToolWindow()
        }
        waitFor(Duration.ofSeconds(15)) {
            try {
                val name = PageMirrorToolWindowFixture.find(robot).selectedSnapshotName()
                name.isNotBlank() && !name.contains("No snapshot")
            } catch (_: Exception) { false }
        }
        Thread.sleep(2_000)  // wait for JCEF page to fully load
    }

    /**
     * UT-09: Moving caret to a locator line triggers a highlight overlay in JCEF.
     */
    @Test
    fun `caret on getByTestId line triggers highlight`() {
        goToLine(GET_BY_TEST_ID_LINE)
        // Debounce is 150 ms; give it extra headroom
        Thread.sleep(500)

        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)
        assertTrue(
            browser.isHighlightVisible(),
            "Highlight overlay should appear when caret is on getByTestId line"
        )
    }

    /**
     * UT-10: Caret on a non-locator line clears the highlight.
     */
    @Test
    fun `caret on non locator line clears highlight`() {
        // First activate a highlight
        goToLine(GET_BY_TEST_ID_LINE)
        Thread.sleep(500)

        // Move caret to a non-locator line
        goToLine(BLANK_LINE)
        Thread.sleep(500)

        val browser = SnapshotBrowserFixture.findInsideToolWindow(
            PageMirrorToolWindowFixture.find(robot)
        )
        assertFalse(
            browser.isHighlightVisible(),
            "Highlight should be cleared when caret moves off a locator line"
        )
    }

    /**
     * UT-11: Alt+Shift+H shortcut manually triggers highlighting of the locator at the caret.
     */
    @Test
    fun `alt shift H shortcut triggers highlight`() {
        goToLine(LOCATOR_LINE)
        Thread.sleep(300)

        ideFrame().keyboard {
            hotKey(KeyEvent.VK_ALT, KeyEvent.VK_SHIFT, KeyEvent.VK_H)
        }
        Thread.sleep(500)

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
    fun `all locator types trigger highlight`() {
        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)

        val locatorLines = listOf(
            GET_BY_TEST_ID_LINE to "getByTestId",
            LOCATOR_LINE        to "locator",
            GET_BY_ROLE_LINE    to "getByRole",
            GET_BY_TEXT_LINE    to "getByText",
        )

        for ((line, locatorType) in locatorLines) {
            goToLine(line)
            Thread.sleep(500)
            assertTrue(
                browser.isHighlightVisible(),
                "Highlight should be visible for $locatorType on line $line"
            )
        }
    }
}
