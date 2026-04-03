package com.github.artem.pageobjectplugin.listeners

import com.github.artem.pageobjectplugin.locators.LocatorExtractor
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.github.artem.pageobjectplugin.settings.PageMirrorSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import java.util.Timer
import java.util.TimerTask

class CaretHighlightListener(private val project: Project) : CaretListener, Disposable {

    private var debounceTimer: Timer? = null
    private val debounceDelayMs = 150L
    private var registered = false

    fun register() {
        if (registered) return
        EditorFactory.getInstance().eventMulticaster.addCaretListener(this, this)
        registered = true
    }

    override fun caretPositionChanged(event: CaretEvent) {
        val editor = event.editor
        val editorProject = editor.project ?: return
        if (editorProject != project) return

        val document = editor.document
        val file = FileDocumentManager.getInstance().getFile(document) ?: return
        if (!PageMirrorSettings.getInstance(project).isSupportedFile(file.name)) return

        val offset = event.caret?.offset ?: return
        val lineNumber = document.getLineNumber(offset)
        val lineStart = document.getLineStartOffset(lineNumber)
        val lineEnd = document.getLineEndOffset(lineNumber)
        val lineText = document.getText(com.intellij.openapi.util.TextRange(lineStart, lineEnd))

        debounceTimer?.cancel()
        debounceTimer = Timer("CaretHighlight-debounce", true).apply {
            schedule(object : TimerTask() {
                override fun run() {
                    val service = SnapshotService.getInstance(project)
                    if (service.isHighlightAllActive) return

                    val locator = LocatorExtractor.extract(lineText)
                    if (locator != null) {
                        service.highlightElement(locator.type, locator.value)
                    } else {
                        service.clearHighlight()
                    }
                }
            }, debounceDelayMs)
        }
    }

    override fun dispose() {
        debounceTimer?.cancel()
        debounceTimer = null
        registered = false
    }
}
