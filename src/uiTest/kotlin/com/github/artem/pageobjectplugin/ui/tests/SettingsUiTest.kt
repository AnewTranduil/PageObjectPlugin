package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorSettingsFixture
import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.fixtures.SnapshotBrowserFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * UI tests: UT-21 to UT-25 — Settings dialog.
 *
 * Tests that the Settings > Tools > Page Mirror panel is present and that
 * changes persist correctly.
 */
@Disabled("CI: Settings dialog components (JSpinner, JTextField, JComboBox) not found — IntelliJ UI DSL wraps them with different class names")
class SettingsUiTest : BaseUiTest() {

    @BeforeEach
    fun setup() {
        // Make sure we have a project open (IDE was started with test-project)
        openFileInEditor("login.page.ts")
        Thread.sleep(1_000)
    }

    @AfterEach
    fun resetSettings() {
        // Close any open dialog first (settings or other)
        try { PageMirrorSettingsFixture.clickCancel(robot) } catch (_: Exception) {}
        Thread.sleep(300)
        // Restore default settings programmatically via IDE API
        try {
            ideFrame().callJs<Boolean>("""
                var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
                var __pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.artem.pageobjectplugin")
                var __plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(__pluginId)
                var __cl = __plugin.getPluginClassLoader()
                var __svcClass = __cl.loadClass("com.github.artem.pageobjectplugin.settings.PageMirrorSettings")
                var settings = project.getService(__svcClass)
                var __stateClass = __cl.loadClass("com.github.artem.pageobjectplugin.settings.PageMirrorSettings" + "\u0024" + "State")
                var defaultState = __stateClass.getDeclaredConstructor().newInstance()
                settings.loadState(defaultState)
                true
            """, runInEdt = true)
        } catch (_: Exception) {
            // Best-effort cleanup
        }
    }

    /**
     * UT-21: Settings dialog opens and shows all four Page Mirror fields.
     */
    @Test
    fun `settings dialog shows all page mirror fields`() {
        val settings = PageMirrorSettingsFixture.open(robot)
        takeScreenshot("settings-dialog-open")

        // Verify all fields are accessible (no exception = field is present)
        val depth = settings.searchDepth()
        val color = settings.highlightColor()
        val style = settings.codeGenStyle()
        val autoReload = settings.isAutoReloadEnabled()

        assertTrue(depth in 1..10, "Search depth should be between 1 and 10, was $depth")
        assertTrue(color.startsWith("#"), "Highlight color should be a hex string, was '$color'")
        assertTrue(
            style == "Property" || style == "Variable",
            "Code gen style should be 'Property' or 'Variable', was '$style'"
        )

        PageMirrorSettingsFixture.clickCancel(robot)
    }

    /**
     * UT-22: Changing search depth persists after reopening the settings dialog.
     */
    @Test
    fun `search depth change persists after ok`() {
        val settings = PageMirrorSettingsFixture.open(robot)
        settings.setSearchDepth(5)
        takeScreenshot("settings-depth-changed")
        PageMirrorSettingsFixture.clickOk(robot)

        // Reopen and verify
        val reopened = PageMirrorSettingsFixture.open(robot)
        val persisted = reopened.searchDepth()
        PageMirrorSettingsFixture.clickCancel(robot)

        assertEquals(5, persisted, "Search depth 5 should persist after saving")
    }

    /**
     * UT-23: Changing the highlight color and clicking Apply takes effect.
     * Indirectly verified by checking the setting persists (JCEF color change
     * requires snapshot to be loaded; confirmed by UT-22 pattern).
     */
    @Test
    fun `highlight color change persists after apply`() {
        val settings = PageMirrorSettingsFixture.open(robot)
        settings.setHighlightColor("#FF0000")
        PageMirrorSettingsFixture.clickApply(robot)

        // Verify without closing dialog
        val colorAfterApply = PageMirrorSettingsFixture.open(robot).highlightColor()
        PageMirrorSettingsFixture.clickCancel(robot)

        assertEquals("#FF0000", colorAfterApply, "Highlight color #FF0000 should persist after Apply")
    }

    /**
     * UT-24: Setting code gen style to "Variable" is saved.
     */
    @Test
    fun `code gen style variable is saved`() {
        val settings = PageMirrorSettingsFixture.open(robot)
        settings.setCodeGenStyle("Variable")
        PageMirrorSettingsFixture.clickOk(robot)

        val persisted = PageMirrorSettingsFixture.open(robot).codeGenStyle()
        PageMirrorSettingsFixture.clickCancel(robot)

        assertEquals("Variable", persisted, "Code gen style 'Variable' should persist")
    }

    /**
     * UT-25: Setting code gen style to "Property" is saved.
     */
    @Test
    fun `code gen style property is saved`() {
        // First set to Variable
        var settings = PageMirrorSettingsFixture.open(robot)
        settings.setCodeGenStyle("Variable")
        PageMirrorSettingsFixture.clickOk(robot)

        // Then set back to Property
        settings = PageMirrorSettingsFixture.open(robot)
        settings.setCodeGenStyle("Property")
        PageMirrorSettingsFixture.clickOk(robot)

        val persisted = PageMirrorSettingsFixture.open(robot).codeGenStyle()
        PageMirrorSettingsFixture.clickCancel(robot)

        assertEquals("Property", persisted, "Code gen style 'Property' should persist")
    }
}
