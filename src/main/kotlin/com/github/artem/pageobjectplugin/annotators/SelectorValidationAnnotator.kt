package com.github.artem.pageobjectplugin.annotators

import com.github.artem.pageobjectplugin.locators.ExtractedLocator
import com.github.artem.pageobjectplugin.locators.LocatorExtractor
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jsoup.nodes.Document
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

data class SelectorAnnotation(
    val lineNumber: Int,
    val lineStartOffset: Int,
    val lineEndOffset: Int,
    val locator: ExtractedLocator,
    val matchCount: Int
)

class SelectorValidationAnnotator : ExternalAnnotator<PsiFile, List<SelectorAnnotation>>() {

    override fun collectInformation(file: PsiFile): PsiFile? {
        if (!file.name.endsWith(".ts") && !file.name.endsWith(".tsx")) return null
        val project = file.project
        val service = SnapshotService.getInstance(project)
        if (service.snapshotDocument == null) return null
        return file
    }

    override fun doAnnotate(file: PsiFile?): List<SelectorAnnotation>? {
        if (file == null) return null

        val project = file.project
        val service = SnapshotService.getInstance(project)
        val document = service.snapshotDocument ?: return null
        val psiDocument = file.viewProvider.document ?: return null

        val annotations = mutableListOf<SelectorAnnotation>()

        for (lineNumber in 0 until psiDocument.lineCount) {
            val lineStart = psiDocument.getLineStartOffset(lineNumber)
            val lineEnd = psiDocument.getLineEndOffset(lineNumber)
            val lineText = psiDocument.getText(TextRange(lineStart, lineEnd))

            val locator = LocatorExtractor.extract(lineText) ?: continue
            val matchCount = countMatches(document, locator)

            annotations.add(
                SelectorAnnotation(
                    lineNumber = lineNumber,
                    lineStartOffset = lineStart,
                    lineEndOffset = lineEnd,
                    locator = locator,
                    matchCount = matchCount
                )
            )
        }

        return annotations
    }

    override fun apply(file: PsiFile, annotations: List<SelectorAnnotation>?, holder: AnnotationHolder) {
        if (annotations == null) return

        for (annotation in annotations) {
            val range = TextRange(annotation.lineStartOffset, annotation.lineEndOffset)
            val count = annotation.matchCount

            val severity = when {
                count == 0 -> HighlightSeverity.WARNING
                count == 1 -> HighlightSeverity.INFORMATION
                else -> HighlightSeverity.WEAK_WARNING
            }

            val message = when {
                count == 0 -> "Selector matches 0 elements"
                count == 1 -> "Selector matches 1 element"
                else -> "Selector matches $count elements (ambiguous)"
            }

            holder.newAnnotation(severity, message)
                .range(range)
                .gutterIconRenderer(MatchCountGutterRenderer(count, annotation.locator))
                .create()
        }
    }

    private fun countMatches(document: Document, locator: ExtractedLocator): Int {
        return try {
            when (locator.type) {
                "locator" -> {
                    val selector = locator.value
                    document.select(selector).size
                }
                "getByTestId" -> {
                    document.select("[data-testid=\"${locator.value}\"]").size
                }
                "getByRole" -> {
                    val parts = locator.value.split(":", limit = 2)
                    val role = parts[0]
                    val name = parts.getOrNull(1)
                    val elements = document.select("[role=\"$role\"]")
                    if (name != null) {
                        elements.count { it.text().contains(name, ignoreCase = true) }
                    } else {
                        elements.size
                    }
                }
                "getByText" -> {
                    val text = locator.value
                    document.allElements.count {
                        it.ownText().contains(text, ignoreCase = true)
                    }
                }
                "getByPlaceholder" -> {
                    document.select("[placeholder=\"${locator.value}\"]").size
                }
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }

    private class MatchCountGutterRenderer(
        private val count: Int,
        private val locator: ExtractedLocator
    ) : GutterIconRenderer() {

        override fun getIcon(): Icon = MatchCountIcon(count)

        override fun getTooltipText(): String = when {
            count == 0 -> "No matches for ${locator.type}('${locator.value}')"
            count == 1 -> "1 match for ${locator.type}('${locator.value}')"
            else -> "$count matches for ${locator.type}('${locator.value}')"
        }

        override fun equals(other: Any?): Boolean {
            if (other !is MatchCountGutterRenderer) return false
            return count == other.count && locator == other.locator
        }

        override fun hashCode(): Int = 31 * count + locator.hashCode()
    }

    private class MatchCountIcon(private val count: Int) : Icon {
        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            val color = when {
                count == 0 -> Color(239, 68, 68)    // red
                count == 1 -> Color(34, 197, 94)    // green
                else -> Color(234, 179, 8)          // yellow
            }

            g2.color = color
            g2.fillOval(x + 1, y + 1, 14, 14)

            g2.color = Color.WHITE
            g2.font = g2.font.deriveFont(9f)
            val text = if (count > 9) "9+" else count.toString()
            val fm = g2.fontMetrics
            val tx = x + (16 - fm.stringWidth(text)) / 2
            val ty = y + (16 + fm.ascent - fm.descent) / 2
            g2.drawString(text, tx, ty)

            g2.dispose()
        }

        override fun getIconWidth(): Int = 16
        override fun getIconHeight(): Int = 16
    }
}
