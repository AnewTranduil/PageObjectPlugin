package com.github.artem.pageobjectplugin.ui.pages

import com.github.artem.pageobjectplugin.ui.locators.IntelliJLocators
import com.github.artem.pageobjectplugin.ui.support.StepRecorder
import com.github.artem.pageobjectplugin.ui.support.Wait
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import java.time.Duration

/**
 * Page object for the IntelliJ editor / project file system.
 *
 * Absorbs the file/caret/VFS helpers that previously lived on `BaseUiTest`.
 * Each public method is wrapped in `StepRecorder.step` so it shows up as a
 * timestamped step in the per-test trace bundle.
 */
class EditorPage(private val robot: RemoteRobot) {

    private val getProjectJs = """
        var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
    """.trimIndent()

    private fun ideFrame(): CommonContainerFixture =
        robot.find(IntelliJLocators.ideFrame, Duration.ofSeconds(10))

    /**
     * Opens [fileName] (any descendant of the project base dir) in the editor
     * and waits until a text editor is selected.
     */
    fun openFileInEditor(fileName: String): EditorPage = StepRecorder.step(
        label = "openFileInEditor($fileName)",
        robot = robot,
    ) {
        ideFrame().callJs<Boolean>("""
            $getProjectJs

            var baseDir = project.getBaseDir()
            function findFile(dir, name) {
                var children = dir.getChildren()
                for (var i = 0; i < children.length; i++) {
                    if (children[i].getName() == name) return children[i]
                    if (children[i].isDirectory()) {
                        var result = findFile(children[i], name)
                        if (result != null) return result
                    }
                }
                return null
            }

            var file = findFile(baseDir, "$fileName")
            if (file != null) {
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(file, true)
            }
            true
        """, runInEdt = true)
        Wait.pollUntilTrue(
            timeout = Duration.ofSeconds(10),
            interval = Duration.ofMillis(100),
            message = { "no editor selected after openFile($fileName)" },
        ) {
            ideFrame().callJs(
                """
                $getProjectJs
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor() != null
                """.trimIndent(),
                runInEdt = true,
            )
        }
        this
    }

    /**
     * Moves the caret to [line] (1-based) in the active editor and waits for
     * the caret position to actually update.
     */
    fun goToLine(line: Int): EditorPage = StepRecorder.step(
        label = "goToLine($line)",
        robot = robot,
    ) {
        ideFrame().callJs<Boolean>("""
            $getProjectJs

            var editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor()
            if (editor != null) {
                var lineIndex = $line - 1
                var offset = editor.getDocument().getLineStartOffset(lineIndex)
                editor.getCaretModel().moveToOffset(offset)
                editor.getScrollingModel().scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
            }
            true
        """, runInEdt = true)
        Wait.pollUntilTrue(
            timeout = Duration.ofSeconds(3),
            interval = Duration.ofMillis(50),
            message = { "caret never reached line $line" },
        ) {
            ideFrame().callJs(
                """
                $getProjectJs
                var editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor()
                editor != null && editor.getCaretModel().getLogicalPosition().line == ${line - 1}
                """.trimIndent(),
                runInEdt = true,
            )
        }
        this
    }

    /**
     * Forces a VFS refresh so externally-placed snapshot files become visible
     * to the IDE. Critical in CI where files exist on disk but VFS hasn't
     * picked them up yet.
     */
    fun triggerVfsRefresh(): EditorPage = StepRecorder.step(
        label = "triggerVfsRefresh",
        robot = robot,
    ) {
        try {
            ideFrame().callJs<Boolean>("""
                com.intellij.openapi.vfs.VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
                true
            """, runInEdt = true)
            // TODO(13b): VFS refresh has no completion signal — short bounded wait, then
            // the snapshot-discovery poll downstream confirms files are visible.
            Thread.sleep(200)
        } catch (e: Exception) {
            System.err.println("[EditorPage.triggerVfsRefresh] failed: ${e.message}")
        }
        this
    }

    /**
     * Returns the full text of the currently selected editor (used by tests
     * that assert on inserted locator content).
     */
    fun activeEditorText(): String = StepRecorder.step(
        label = "activeEditorText",
        robot = robot,
    ) {
        ideFrame().callJs(
            """
            $getProjectJs
            var editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor()
            editor != null ? editor.getDocument().getText() : ""
            """.trimIndent(),
            runInEdt = true,
        )
    }
}
