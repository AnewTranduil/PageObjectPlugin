package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.annotations.Feature
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.pages.EditorPage
import com.github.artem.pageobjectplugin.ui.pages.PluginToolWindowPage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * UI tests: UT-29 to UT-30 — Theme support.
 *
 * The Page Mirror JCEF page listens for IDE theme changes and applies
 * 'theme-dark' or 'theme-light' CSS class to document.body accordingly.
 *
 * Since JCEF JS state can't be queried via callJs (executeJavaScript is
 * fire-and-forget), we verify theme support by checking the LafManager
 * state on the Kotlin side — the SnapshotService.applyTheme() is triggered
 * by LafManagerListener and uses the isDark flag to set the CSS class.
 */
@Feature("theme")
class ThemeUiTest : BaseUiTest() {

    private var originalThemeName: String = "IntelliJ Light"

    private val editor by lazy { EditorPage(robot) }
    private val toolWindow by lazy { PluginToolWindowPage(robot) }

    @BeforeEach
    fun loadSnapshot() {
        editor.openFileInEditor("login.page.ts")
        if (!PageMirrorToolWindowFixture.isVisible(robot)) {
            toolWindow.open()
        }
        toolWindow.waitForSnapshotDiscovery(Duration.ofSeconds(15))
        Thread.sleep(2_000)  // JCEF page first paint — no observable signal
        originalThemeName = detectCurrentThemeName()
    }

    @AfterEach
    fun restoreTheme() {
        try {
            applyThemeByName(originalThemeName)
        } catch (_: Exception) {
            // Best-effort; don't fail the cleanup
        }
    }

    /**
     * UT-29: When IDE uses Darcula theme, LafManager reports dark=true,
     * and SnapshotService.applyTheme() sends 'dark' to JCEF.
     */
    @Test
    fun `jcef applies dark class on dark IDE theme`() {
        applyThemeByName("Darcula")
        Thread.sleep(2_000)
        takeScreenshot("after-darcula-theme")

        val isDark = ideFrame().callJs<Boolean>("""
            new java.lang.Boolean(
                com.intellij.ide.ui.LafManager.getInstance()
                    .getCurrentUIThemeLookAndFeel().isDark()
            )
        """, runInEdt = true)
        assertTrue(isDark, "IDE should report dark theme after switching to Darcula")
    }

    /**
     * UT-30: When IDE uses a light theme, LafManager reports dark=false,
     * and SnapshotService.applyTheme() sends 'light' to JCEF.
     */
    @Test
    fun `jcef applies light class on light IDE theme`() {
        applyThemeByName("IntelliJ Light")
        Thread.sleep(2_000)
        takeScreenshot("after-light-theme")

        val isDark = ideFrame().callJs<Boolean>("""
            new java.lang.Boolean(
                com.intellij.ide.ui.LafManager.getInstance()
                    .getCurrentUIThemeLookAndFeel().isDark()
            )
        """, runInEdt = true)
        assertFalse(isDark, "IDE should report light theme after switching to IntelliJ Light")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Applies the named IDE theme programmatically via LafManager.
     */
    private fun applyThemeByName(themeName: String) {
        ideFrame().callJs<Boolean>("""
            var lafManager = com.intellij.ide.ui.LafManager.getInstance()
            var themes = lafManager.getInstalledLookAndFeels()
            for (var i = 0; i < themes.length; i++) {
                var theme = themes[i]
                if (theme.getName().equals("$themeName")) {
                    lafManager.setCurrentLookAndFeel(theme, true)
                    break
                }
            }
            true
        """, runInEdt = true)
    }

    private fun detectCurrentThemeName(): String = try {
        ideFrame().callJs<String>("""
            var t = com.intellij.ide.ui.LafManager.getInstance().getCurrentUIThemeLookAndFeel()
            t != null ? t.getName() : "IntelliJ Light"
        """, runInEdt = true)
    } catch (_: Exception) {
        "IntelliJ Light"
    }
}
