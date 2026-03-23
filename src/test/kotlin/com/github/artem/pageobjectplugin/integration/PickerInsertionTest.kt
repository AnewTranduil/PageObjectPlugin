package com.github.artem.pageobjectplugin.integration

import com.github.artem.pageobjectplugin.locators.PickerResultHandler
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class PickerInsertionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.configureByText("Test.txt", "")
    }

    // --- parseElementJson ---

    fun `test parseElementJson returns element data for valid json`() {
        val json = """{"selector":"#username","tag":"input","role":"textbox","text":"Username","attributes":{"data-testid":"login-username"}}"""
        val element = PickerResultHandler.parseElementJson(json)

        assertNotNull(element)
        assertEquals("#username", element!!.selector)
        assertEquals("input", element.tag)
        assertEquals("textbox", element.role)
        assertEquals("Username", element.text)
        assertEquals("login-username", element.attributes["data-testid"])
    }

    fun `test parseElementJson returns null when selector missing`() {
        val json = """{"tag":"input","role":"textbox","text":"Username","attributes":{}}"""
        assertNull(PickerResultHandler.parseElementJson(json))
    }

    fun `test parseElementJson handles null role`() {
        val json = """{"selector":"#foo","tag":"div","role":null,"text":null,"attributes":{}}"""
        val element = PickerResultHandler.parseElementJson(json)

        assertNotNull(element)
        assertNull(element!!.role)
    }

    // --- generateLocator ---

    fun `test generateLocator prefers getByTestId when data-testid present`() {
        val element = PickerResultHandler.ElementData(
            selector = "#username",
            tag = "input",
            role = "textbox",
            text = "Username",
            attributes = mapOf("data-testid" to "login-username")
        )
        assertEquals("getByTestId('login-username')", PickerResultHandler.generateLocator(element))
    }

    fun `test generateLocator uses getByRole when role and text present and no testId`() {
        val element = PickerResultHandler.ElementData(
            selector = "button",
            tag = "button",
            role = "button",
            text = "Login",
            attributes = emptyMap()
        )
        assertEquals("getByRole('button', { name: 'Login' })", PickerResultHandler.generateLocator(element))
    }

    fun `test generateLocator uses getByText for short text without role`() {
        val element = PickerResultHandler.ElementData(
            selector = "p",
            tag = "p",
            role = null,
            text = "Submit",
            attributes = emptyMap()
        )
        assertEquals("getByText('Submit')", PickerResultHandler.generateLocator(element))
    }

    fun `test generateLocator uses getByPlaceholder for input with placeholder`() {
        val element = PickerResultHandler.ElementData(
            selector = "#pass",
            tag = "input",
            role = null,
            text = null,
            attributes = mapOf("placeholder" to "Password")
        )
        assertEquals("getByPlaceholder('Password')", PickerResultHandler.generateLocator(element))
    }

    fun `test generateLocator falls back to css selector`() {
        val element = PickerResultHandler.ElementData(
            selector = "#some-unique-id",
            tag = "div",
            role = null,
            text = null,
            attributes = emptyMap()
        )
        assertEquals("locator('#some-unique-id')", PickerResultHandler.generateLocator(element))
    }

    fun `test generateLocator falls back to css when text is too long`() {
        val element = PickerResultHandler.ElementData(
            selector = "#long",
            tag = "p",
            role = null,
            text = "a".repeat(41),
            attributes = emptyMap()
        )
        assertTrue(PickerResultHandler.generateLocator(element).startsWith("locator("))
    }

    // --- generateFieldName ---

    fun `test generateFieldName uses testId with tag suffix`() {
        val element = PickerResultHandler.ElementData(
            selector = "#username",
            tag = "input",
            role = null,
            text = null,
            attributes = mapOf("data-testid" to "login-username")
        )
        assertEquals("loginUsernameInput", PickerResultHandler.generateFieldName(element))
    }

    fun `test generateFieldName uses text with button suffix`() {
        val element = PickerResultHandler.ElementData(
            selector = "button",
            tag = "button",
            role = null,
            text = "Sign Up",
            attributes = emptyMap()
        )
        val name = PickerResultHandler.generateFieldName(element)
        assertTrue(name.contains("Button", ignoreCase = true))
        assertTrue(name.startsWith("sign") || name.startsWith("Sign"))
    }

    fun `test generateFieldName returns element for empty base name`() {
        val element = PickerResultHandler.ElementData(
            selector = "#x",
            tag = "div",
            role = null,
            text = null,
            attributes = emptyMap()
        )
        // Falls back to tag name "div" → cleaned to "div"
        val name = PickerResultHandler.generateFieldName(element)
        assertTrue(name.isNotEmpty())
    }

    // --- Editor insertion ---

    fun `test property code inserted at caret`() {
        val element = PickerResultHandler.ElementData(
            selector = "#username",
            tag = "input",
            role = "textbox",
            text = "Username",
            attributes = mapOf("data-testid" to "login-username")
        )
        val locatorCode = PickerResultHandler.generateLocator(element)
        val fieldName = PickerResultHandler.generateFieldName(element)
        val code = "readonly $fieldName = this.page.$locatorCode;"

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(myFixture.editor.caretModel.offset, code)
        }

        val text = myFixture.editor.document.text
        assertTrue(text.contains("readonly"))
        assertTrue(text.contains("loginUsernameInput"))
        assertTrue(text.contains("getByTestId('login-username')"))
    }

    fun `test variable code inserted at caret`() {
        val element = PickerResultHandler.ElementData(
            selector = "button",
            tag = "button",
            role = "button",
            text = "Login",
            attributes = emptyMap()
        )
        val locatorCode = PickerResultHandler.generateLocator(element)
        val fieldName = PickerResultHandler.generateFieldName(element)
        val code = "const $fieldName = page.$locatorCode;"

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(myFixture.editor.caretModel.offset, code)
        }

        val text = myFixture.editor.document.text
        assertTrue(text.contains("const"))
        assertTrue(text.contains("getByRole('button', { name: 'Login' })"))
    }

    fun `test insertion is undoable`() {
        val code = "readonly testField = this.page.locator('#foo');"

        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.insertString(0, code)
        }
        assertFalse(myFixture.editor.document.text.isEmpty())

        myFixture.performEditorAction("\$Undo")

        assertTrue(myFixture.editor.document.text.isEmpty())
    }
}
