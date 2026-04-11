package com.github.artem.pageobjectplugin.ui.tests

import com.github.artem.pageobjectplugin.ui.BaseUiTest
import com.github.artem.pageobjectplugin.ui.annotations.Feature
import com.github.artem.pageobjectplugin.ui.flows.SettingsChangeFlow
import com.github.artem.pageobjectplugin.ui.pages.EditorPage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * UI tests: UT-21 to UT-25 — Settings dialog.
 *
 * Reference example for [SettingsChangeFlow] + [com.github.artem.pageobjectplugin.ui.pages.PluginSettingsPage]
 * composition. Enabled after `PageMirrorConfigurable` was given explicit
 * accessible names on its four testable fields (see
 * `main/...settings/PageMirrorConfigurable.kt`) and the settings locators in
 * [com.github.artem.pageobjectplugin.ui.locators.PageMirrorLocators] were
 * rewritten to match those accessible names instead of relying on UI DSL
 * class-name quirks (JBIntSpinner vs JSpinner, etc.).
 */
@Feature("settings")
class SettingsUiTest : BaseUiTest() {

    private val editor by lazy { EditorPage(robot) }
    private val settings by lazy { SettingsChangeFlow(robot) }

    @BeforeEach
    fun ensureProjectOpen() {
        editor.openFileInEditor("login.page.ts")
    }

    @AfterEach
    fun resetSettings() {
        // Restore default settings programmatically via IDE API. Cancels any
        // open dialog first via SettingsChangeFlow.withSettings's exception
        // path on a no-op block — but since this is a fast restore call we
        // do it directly via callJs for robustness.
        try {
            ideFrame().callJs<Boolean>("""
                var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
                var __pluginId = com.intellij.openapi.extensions.PluginId.getId("com.github.artem.pageobjectplugin")
                var __plugin = com.intellij.ide.plugins.PluginManagerCore.getPlugin(__pluginId)
                var __cl = __plugin.getPluginClassLoader()
                var __svcClass = __cl.loadClass("com.github.artem.pageobjectplugin.settings.PageMirrorSettings")
                var serviceInstance = project.getService(__svcClass)
                var __stateClass = __cl.loadClass("com.github.artem.pageobjectplugin.settings.PageMirrorSettings" + "\u0024" + "State")
                var defaultState = __stateClass.getDeclaredConstructor().newInstance()
                serviceInstance.loadState(defaultState)
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
        settings.withSettings { page ->
            takeScreenshot("settings-dialog-open")

            val depth = page.searchDepth()
            val color = page.highlightColor()
            val style = page.codeGenStyle()
            page.isAutoReloadEnabled()  // smoke check — value not asserted

            assertTrue(depth in 1..10, "Search depth should be between 1 and 10, was $depth")
            assertTrue(color.startsWith("#"), "Highlight color should be a hex string, was '$color'")
            assertTrue(
                style == "Property" || style == "Variable",
                "Code gen style should be 'Property' or 'Variable', was '$style'",
            )

            page.clickCancel()
        }
    }

    /**
     * UT-22: Changing search depth persists after reopening the settings dialog.
     */
    @Test
    fun `search depth change persists after ok`() {
        settings.setSearchDepth(5)

        var persisted = -1
        settings.withSettings { page ->
            persisted = page.searchDepth()
            page.clickCancel()
        }
        assertEquals(5, persisted, "Search depth 5 should persist after saving")
    }

    /**
     * UT-23: Changing the highlight color and clicking Apply takes effect.
     */
    @Test
    fun `highlight color change persists after apply`() {
        settings.withSettings { page ->
            page.setHighlightColor("#FF0000")
            page.clickApply()
            page.clickCancel()
        }

        var colorAfterApply = ""
        settings.withSettings { page ->
            colorAfterApply = page.highlightColor()
            page.clickCancel()
        }
        assertEquals("#FF0000", colorAfterApply, "Highlight color #FF0000 should persist after Apply")
    }

    /**
     * UT-24: Setting code gen style to "Variable" is saved.
     */
    @Test
    fun `code gen style variable is saved`() {
        settings.setCodeGenStyle("Variable")

        var persisted = ""
        settings.withSettings { page ->
            persisted = page.codeGenStyle()
            page.clickCancel()
        }
        assertEquals("Variable", persisted, "Code gen style 'Variable' should persist")
    }

    /**
     * UT-25: Setting code gen style to "Property" is saved.
     */
    @Test
    fun `code gen style property is saved`() {
        settings.setCodeGenStyle("Variable")
        settings.setCodeGenStyle("Property")

        var persisted = ""
        settings.withSettings { page ->
            persisted = page.codeGenStyle()
            page.clickCancel()
        }
        assertEquals("Property", persisted, "Code gen style 'Property' should persist")
    }
}
