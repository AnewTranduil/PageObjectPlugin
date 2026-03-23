package com.github.artem.pageobjectplugin.integration

import com.github.artem.pageobjectplugin.settings.PageMirrorSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SettingsPersistenceTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        // Reset to defaults before each test — settings persist as a project-level service
        PageMirrorSettings.getInstance(project).loadState(PageMirrorSettings.State())
    }

    fun `test default state has expected values`() {
        val settings = PageMirrorSettings.getInstance(project)

        assertEquals(3, settings.state.snapshotSearchDepth)
        assertTrue(settings.state.autoReloadOnChange)
        assertEquals("#3B82F6", settings.state.highlightColor)
        assertEquals("Property", settings.state.codeGenStyle)
    }

    fun `test loadState overrides all fields`() {
        val settings = PageMirrorSettings.getInstance(project)
        settings.loadState(
            PageMirrorSettings.State(
                snapshotSearchDepth = 5,
                autoReloadOnChange = false,
                highlightColor = "#FF0000",
                codeGenStyle = "Variable"
            )
        )

        assertEquals(5, settings.state.snapshotSearchDepth)
        assertFalse(settings.state.autoReloadOnChange)
        assertEquals("#FF0000", settings.state.highlightColor)
        assertEquals("Variable", settings.state.codeGenStyle)
    }

    fun `test getState returns updated values after loadState`() {
        val settings = PageMirrorSettings.getInstance(project)
        settings.loadState(PageMirrorSettings.State(snapshotSearchDepth = 7))

        assertEquals(7, settings.state.snapshotSearchDepth)
    }

    fun `test loadState partial override preserves other defaults`() {
        val settings = PageMirrorSettings.getInstance(project)
        // State is a data class with defaults — only searchDepth overridden
        settings.loadState(PageMirrorSettings.State(snapshotSearchDepth = 2))

        assertEquals(2, settings.state.snapshotSearchDepth)
        assertTrue(settings.state.autoReloadOnChange)        // default preserved
        assertEquals("#3B82F6", settings.state.highlightColor) // default preserved
    }

    fun `test highlightColor round trips correctly`() {
        val settings = PageMirrorSettings.getInstance(project)
        settings.loadState(PageMirrorSettings.State(highlightColor = "#AABBCC"))

        assertEquals("#AABBCC", settings.state.highlightColor)
    }

    fun `test codeGenStyle round trips correctly`() {
        val settings = PageMirrorSettings.getInstance(project)
        settings.loadState(PageMirrorSettings.State(codeGenStyle = "Variable"))

        assertEquals("Variable", settings.state.codeGenStyle)
    }

    fun `test autoReloadOnChange can be set to false`() {
        val settings = PageMirrorSettings.getInstance(project)
        settings.loadState(PageMirrorSettings.State(autoReloadOnChange = false))

        assertFalse(settings.state.autoReloadOnChange)
    }
}
