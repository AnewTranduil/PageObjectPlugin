package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.fixtures.SnapshotBrowserFixture
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import java.time.Duration

/**
 * UI tests: UT-29 to UT-30 — Theme support.
 *
 * The Page Mirror JCEF page listens for IDE theme changes and applies
 * 'dark' or 'light' CSS class to document.body accordingly.
 */
class ThemeUiTest : BaseUiTest() {

    private var originalTheme: String = "IntelliJ Light"

    @BeforeEach
    fun loadSnapshot() {
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
        Thread.sleep(2_000)
        originalTheme = detectCurrentTheme()
    }

    @AfterEach
    fun restoreTheme() {
        try {
            applyTheme(originalTheme)
        } catch (_: Exception) {
            // Best-effort; don't fail the cleanup
        }
    }

    /**
     * UT-29: JCEF page body has 'dark' class when IDE uses a dark theme.
     */
    @Test
    fun `jcef applies dark class on dark IDE theme`() {
        applyTheme("Darcula")
        Thread.sleep(2_000)

        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)

        val isDark = browser.callJs<Boolean>(
            "component.getCefBrowser().executeJavaScript(" +
                "\"document.body.classList.contains('dark')\", '', 0); false"
        )
        assertTrue(isDark, "Body should have 'dark' CSS class when Darcula theme is active")
    }

    /**
     * UT-30: JCEF page body does NOT have 'dark' class when IDE uses a light theme.
     */
    @Test
    fun `jcef applies light class on light IDE theme`() {
        applyTheme("IntelliJ Light")
        Thread.sleep(2_000)

        val toolWindow = PageMirrorToolWindowFixture.find(robot)
        val browser = SnapshotBrowserFixture.findInsideToolWindow(toolWindow)

        val isDark = browser.callJs<Boolean>(
            "component.getCefBrowser().executeJavaScript(" +
                "\"document.body.classList.contains('dark')\", '', 0); false"
        )
        assertFalse(isDark, "Body should NOT have 'dark' CSS class when light theme is active")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Applies the named IDE theme via Settings > Appearance & Behavior > Appearance.
     */
    private fun applyTheme(themeName: String) {
        // Open Settings (Ctrl+Alt+S)
        ideFrame().keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_ALT, KeyEvent.VK_S)
        }
        waitFor(Duration.ofSeconds(10)) {
            robot.findAll<CommonContainerFixture>(byXpath("//div[@class='DialogRootPane']")).isNotEmpty()
        }

        val dialog = robot.find<CommonContainerFixture>(
            byXpath("//div[@class='DialogRootPane']"),
            Duration.ofSeconds(10)
        )

        // Search for Appearance in settings
        dialog.findAll<CommonContainerFixture>(
            byXpath(".//div[@class='SearchTextField']")
        ).firstOrNull()?.keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
            enterText("Appearance")
        }
        Thread.sleep(500)

        // Select "Appearance" tree node (not "Appearance & Behavior")
        dialog.findAll<ComponentFixture>(
            byXpath(".//div[@class='MyTreePath' and @text='Appearance']")
        ).firstOrNull()?.click()
        Thread.sleep(500)

        // Find the Theme combo box
        val themeCombo = dialog.findAll<CommonContainerFixture>(
            byXpath(".//div[@class='ComboBox' and @accessiblename='Theme:']")
        ).firstOrNull() ?: dialog.findAll<CommonContainerFixture>(
            byXpath(".//div[@class='ComboBox']")
        ).firstOrNull()

        themeCombo?.let { combo ->
            combo.click()
            waitFor(Duration.ofSeconds(5)) {
                robot.findAll<CommonContainerFixture>(byXpath("//div[@class='JList']")).isNotEmpty()
            }
            robot.find<CommonContainerFixture>(
                byXpath("//div[@class='JList']"),
                Duration.ofSeconds(5)
            ).findAll<ComponentFixture>(
                byXpath(".//div[contains(@text, '$themeName')]")
            ).firstOrNull()?.click()
        }

        Thread.sleep(300)
        dialog.findAll<ComponentFixture>(
            byXpath(".//div[@text='OK']")
        ).firstOrNull()?.click()
        Thread.sleep(500)
    }

    private fun detectCurrentTheme(): String = try {
        ideFrame().callJs<String>(
            "com.intellij.ide.ui.LafManager.getInstance().getCurrentUIThemeLookAndFeel()?.getName() ?: 'IntelliJ Light'"
        )
    } catch (_: Exception) {
        "IntelliJ Light"
    }
}
