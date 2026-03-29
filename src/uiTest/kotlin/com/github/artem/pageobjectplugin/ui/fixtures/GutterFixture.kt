package com.github.artem.pageobjectplugin.ui.fixtures

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import java.time.Duration

/**
 * Fixture for the editor gutter component.
 *
 * Provides helpers to check Page Mirror gutter annotations (match-count badges)
 * produced by [SelectorValidationAnnotator].
 *
 * Gutter icons are NOT separate Swing components — they're painted on a single
 * EditorGutterComponentImpl canvas. We query them programmatically via the
 * editor's markup model using callJs.
 */
class GutterFixture(robot: RemoteRobot, component: RemoteComponent) :
    CommonContainerFixture(robot, component) {

    /**
     * Returns all gutter icon renderer tooltip texts from the active editor's markup model.
     *
     * Page Mirror badges use tooltips like "1 match for ...", "No matches for ...".
     */
    fun allIconTooltips(): List<String> {
        val joined = try {
            callJs<String>("""
                var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
                var editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor()
                var result = ""
                if (editor != null) {
                    // Helper to scan highlighters in a markup model
                    function scanModel(model) {
                        if (model == null) return
                        var highlighters = model.getAllHighlighters()
                        for (var i = 0; i < highlighters.length; i++) {
                            var renderer = highlighters[i].getGutterIconRenderer()
                            if (renderer != null) {
                                var tooltip = renderer.getTooltipText()
                                if (tooltip != null && tooltip.length() > 0) {
                                    if (result.length() > 0) result += "|||"
                                    result += tooltip
                                }
                            }
                        }
                    }
                    // Editor markup model (editor-level highlights)
                    scanModel(editor.getMarkupModel())
                    // Document markup model (annotation/daemon highlights)
                    var docModel = com.intellij.openapi.editor.impl.DocumentMarkupModel.forDocument(
                        editor.getDocument(), project, false)
                    scanModel(docModel)
                }
                result
            """, runInEdt = true)
        } catch (_: Exception) { "" }
        return if (joined.isBlank()) emptyList() else joined.split("|||")
    }

    /**
     * Returns true if any Page Mirror gutter badge is visible with a tooltip
     * containing [text] (e.g. "1 match", "0 matches").
     */
    fun hasBadgeWithTooltip(text: String): Boolean =
        allIconTooltips().any { it.contains(text, ignoreCase = true) }

    companion object {
        fun find(robot: RemoteRobot): GutterFixture =
            robot.find(
                byXpath("//div[@class='EditorGutterComponentImpl']"),
                Duration.ofSeconds(10)
            )
    }
}
