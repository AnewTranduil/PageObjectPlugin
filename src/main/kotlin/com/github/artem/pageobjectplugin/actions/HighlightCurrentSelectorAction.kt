package com.github.artem.pageobjectplugin.actions

import com.github.artem.pageobjectplugin.locators.LocatorExtractor
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.TextRange

class HighlightCurrentSelectorAction : AnAction("Highlight Current Selector") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val service = SnapshotService.getInstance(project)

        val document = editor.document
        val caret = editor.caretModel.primaryCaret
        val lineNumber = caret.logicalPosition.line
        if (lineNumber >= document.lineCount) return

        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(TextRange(lineStart, lineEnd))

        val locator = LocatorExtractor.extract(lineText)
        if (locator != null) {
            service.highlightElement(locator.type, locator.value)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null &&
            SnapshotService.getInstance(project).currentBundle != null
    }
}
