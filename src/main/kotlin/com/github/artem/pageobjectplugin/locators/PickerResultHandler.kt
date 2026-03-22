package com.github.artem.pageobjectplugin.locators

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

class PickerResultHandler(private val project: Project) {

    fun handlePickerResult(jsonString: String) {
        val element = parseElementJson(jsonString) ?: return
        val locatorCode = generateLocator(element)
        val fieldName = generateFieldName(element)

        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return

        val options = listOf(
            "Property: readonly $fieldName = this.page.${locatorCode};",
            "Variable: const $fieldName = page.${locatorCode};",
            "Copy selector"
        )

        val step = object : BaseListPopupStep<String>("Insert Locator", options) {
            override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
                if (selectedValue == null) return FINAL_CHOICE
                when {
                    selectedValue.startsWith("Property:") -> {
                        val code = "readonly $fieldName = this.page.${locatorCode};"
                        insertAtCaret(editor, code)
                    }
                    selectedValue.startsWith("Variable:") -> {
                        val code = "const $fieldName = page.${locatorCode};"
                        insertAtCaret(editor, code)
                    }
                    selectedValue.startsWith("Copy") -> {
                        val selector = extractSelectorValue(locatorCode)
                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                        clipboard.setContents(StringSelection(selector), null)
                    }
                }
                return FINAL_CHOICE
            }
        }

        JBPopupFactory.getInstance()
            .createListPopup(step)
            .showInBestPositionFor(editor)
    }

    private fun insertAtCaret(editor: Editor, text: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            val offset = editor.caretModel.offset
            editor.document.insertString(offset, text)
            editor.caretModel.moveToOffset(offset + text.length)
        }
    }

    private fun extractSelectorValue(locatorCode: String): String {
        val match = Regex("""['"]([^'"]+)['"]""").find(locatorCode)
        return match?.groupValues?.get(1) ?: locatorCode
    }

    data class ElementData(
        val selector: String,
        val tag: String,
        val role: String?,
        val text: String?,
        val attributes: Map<String, String>
    )

    companion object {
        fun parseElementJson(json: String): ElementData? {
            try {
                // Simple JSON parsing without external library
                val selector = extractJsonString(json, "selector") ?: return null
                val tag = extractJsonString(json, "tag") ?: "div"
                val role = extractJsonString(json, "role")
                val text = extractJsonString(json, "text")

                val attributes = mutableMapOf<String, String>()
                val attrMatch = Regex(""""attributes"\s*:\s*\{([^}]*)\}""").find(json)
                if (attrMatch != null) {
                    val attrStr = attrMatch.groupValues[1]
                    Regex(""""(\w[\w-]*)"\s*:\s*"([^"]*)"""").findAll(attrStr).forEach {
                        attributes[it.groupValues[1]] = it.groupValues[2]
                    }
                }

                return ElementData(selector, tag, role, text, attributes)
            } catch (_: Exception) {
                return null
            }
        }

        private fun extractJsonString(json: String, key: String): String? {
            val pattern = Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            val match = pattern.find(json) ?: run {
                // Check for null value
                val nullPattern = Regex(""""$key"\s*:\s*null""")
                if (nullPattern.containsMatchIn(json)) return null
                return null
            }
            return match.groupValues[1]
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\\\", "\\")
        }

        fun generateLocator(element: ElementData): String {
            // Priority order per task spec
            val testId = element.attributes["data-testid"]
            if (testId != null) {
                return "getByTestId('$testId')"
            }

            if (element.role != null && element.text?.isNotBlank() == true) {
                val cleanText = element.text.trim().take(40)
                return "getByRole('${element.role}', { name: '$cleanText' })"
            }

            if (element.text?.isNotBlank() == true && element.text.length <= 40) {
                return "getByText('${element.text.trim()}')"
            }

            val placeholder = element.attributes["placeholder"]
            if (placeholder != null && element.tag == "input") {
                return "getByPlaceholder('$placeholder')"
            }

            return "locator('${element.selector}')"
        }

        fun generateFieldName(element: ElementData): String {
            val baseName = when {
                element.attributes["data-testid"] != null -> element.attributes["data-testid"]!!
                element.text?.isNotBlank() == true -> element.text.trim().take(30)
                element.attributes["placeholder"] != null -> element.attributes["placeholder"]!!
                element.attributes["name"] != null -> element.attributes["name"]!!
                else -> element.tag
            }

            val suffix = when (element.tag) {
                "button" -> "Button"
                "input" -> "Input"
                "select" -> "Select"
                "textarea" -> "Textarea"
                "a" -> "Link"
                else -> if (element.role == "button") "Button"
                        else if (element.role == "link") "Link"
                        else ""
            }

            val cleaned = baseName
                .replace(Regex("[^a-zA-Z0-9\\s]"), "")
                .trim()
                .split(Regex("\\s+"))
                .filter { it.isNotEmpty() }

            if (cleaned.isEmpty()) return "element"

            val camelCase = cleaned.first().lowercase() +
                cleaned.drop(1).joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }

            val name = if (camelCase.endsWith(suffix, ignoreCase = true)) camelCase
                       else camelCase + suffix

            return name.take(30)
        }
    }
}
