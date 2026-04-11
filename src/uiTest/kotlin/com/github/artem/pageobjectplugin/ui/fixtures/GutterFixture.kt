package com.github.artem.pageobjectplugin.ui.fixtures

import com.github.artem.pageobjectplugin.ui.locators.IntelliJLocators
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.data.RemoteComponent
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import java.time.Duration

/**
 * Fixture for the editor gutter component.
 *
 * Provides helpers to check Page Mirror gutter annotations (match-count badges)
 * produced by `SelectorValidationAnnotator`.
 *
 * Gutter icons are NOT separate Swing components — they're painted on a single
 * EditorGutterComponentImpl canvas. We query them programmatically via the
 * editor's markup model using callJs.
 *
 * Important: `SelectorValidationAnnotator` is an `ExternalAnnotator`. It
 * attaches the gutter icon renderer to a `HighlightInfo` object, which is then
 * stored as the `errorStripeTooltip` on the range highlighter created by the
 * daemon — not directly on the highlighter via `setGutterIconRenderer`. So a
 * naive `highlighter.getGutterIconRenderer()` always returns null for
 * annotator-produced badges. The scan below checks both the highlighter's
 * direct renderer AND the wrapped `HighlightInfo`'s renderer.
 */
class GutterFixture(robot: RemoteRobot, component: RemoteComponent) :
    CommonContainerFixture(robot, component) {

    /**
     * Returns all gutter icon renderer tooltip texts from the active editor's
     * markup models. Scans four sources in order:
     *   1. Editor markup model (`editor.getMarkupModel()`) — highlighters added
     *      by the editor directly.
     *   2. Document markup model (`DocumentMarkupModel.forDocument(...)`) —
     *      highlighters added by the daemon / external annotators. This is
     *      where `SelectorValidationAnnotator` output lives.
     *   3. For each highlighter: check `getGutterIconRenderer()` first, then
     *      fall back to `getErrorStripeTooltip()` which holds the wrapping
     *      `HighlightInfo` whose `gutterIconRenderer` is the actual renderer.
     *
     * Page Mirror badges use tooltips like "1 match for ...", "No matches for
     * ...", "3 matches for ...".
     */
    fun allIconTooltips(): List<String> {
        val joined = try {
            callJs<String>(
                """
                var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
                var editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor()
                var result = ""
                var HighlightInfoClass = java.lang.Class.forName("com.intellij.codeInsight.daemon.impl.HighlightInfo")

                function extractRendererTooltip(highlighter) {
                    var renderer = highlighter.getGutterIconRenderer()
                    if (renderer == null) {
                        var stripe = null
                        try { stripe = highlighter.getErrorStripeTooltip() } catch (e) {}
                        if (stripe != null && HighlightInfoClass.isInstance(stripe)) {
                            try { renderer = stripe.getGutterIconRenderer() } catch (e) {}
                        }
                    }
                    if (renderer == null) return null
                    try {
                        var t = renderer.getTooltipText()
                        if (t != null && ("" + t).length > 0) return "" + t
                    } catch (e) {}
                    return null
                }

                function scanModel(model) {
                    if (model == null) return
                    var highlighters = model.getAllHighlighters()
                    for (var i = 0; i < highlighters.length; i++) {
                        var tooltip = extractRendererTooltip(highlighters[i])
                        if (tooltip != null) {
                            if (result.length > 0) result += "|||"
                            result += tooltip
                        }
                    }
                }

                if (editor != null) {
                    scanModel(editor.getMarkupModel())
                    var docModel = com.intellij.openapi.editor.impl.DocumentMarkupModel.forDocument(
                        editor.getDocument(), project, false)
                    scanModel(docModel)
                }
                result
                """.trimIndent(),
                runInEdt = true,
            )
        } catch (_: Exception) {
            ""
        }
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
            robot.find(IntelliJLocators.editorGutter, Duration.ofSeconds(10))
    }
}
