package com.github.artem.pageobjectplugin.integration

import com.github.artem.pageobjectplugin.locators.LocatorExtractor
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests the integration of LocatorExtractor + SnapshotService.highlightElement.
 * Simulates what CaretHighlightListener does on each caret move, without the debounce timer.
 */
class CaretHighlightFlowTest : BasePlatformTestCase() {

    private val capturedJs = mutableListOf<String>()
    private lateinit var service: SnapshotService

    override fun setUp() {
        super.setUp()
        service = SnapshotService.getInstance(project)
        service.jsExecutor = { code -> capturedJs.add(code) }
    }

    override fun tearDown() {
        service.resetStateForTesting()
        capturedJs.clear()
        super.tearDown()
    }

    fun `test getByTestId line extracts and triggers highlight with type and value`() {
        val line = "  usernameInput = this.page.getByTestId('login-username');"
        val locator = LocatorExtractor.extract(line)!!

        assertEquals("getByTestId", locator.type)

        service.highlightElement(locator.type, locator.value)

        val js = capturedJs.last()
        assertTrue(js.startsWith("window.highlightElement("))
        assertTrue(js.contains("getByTestId"))
        assertTrue(js.contains("login-username"))
    }

    fun `test locator line extracts css selector and triggers highlight`() {
        val line = "  passwordInput = this.page.locator('#password');"
        val locator = LocatorExtractor.extract(line)!!

        assertEquals("locator", locator.type)
        assertEquals("#password", locator.cssSelector)

        service.highlightElement(locator.type, locator.value)

        val js = capturedJs.last()
        assertTrue(js.contains("locator"))
        assertTrue(js.contains("#password"))
    }

    fun `test getByRole line extracts role and triggers highlight`() {
        val line = "  loginButton = this.page.getByRole('button', { name: 'Login' });"
        val locator = LocatorExtractor.extract(line)!!

        assertEquals("getByRole", locator.type)
        assertEquals("button:Login", locator.value)

        service.highlightElement(locator.type, locator.value)

        val js = capturedJs.last()
        assertTrue(js.startsWith("window.highlightElement("))
        assertTrue(js.contains("getByRole"))
        assertTrue(js.contains("button:Login"))
    }

    fun `test getByText line sends type and text value`() {
        val line = "  errorMessage = this.page.getByText('Bad credentials');"
        val locator = LocatorExtractor.extract(line)!!

        assertEquals("getByText", locator.type)
        assertNull(locator.cssSelector)

        service.highlightElement(locator.type, locator.value)

        val js = capturedJs.last()
        assertTrue(js.contains("getByText"))
        assertTrue(js.contains("Bad credentials"))
    }

    fun `test line with no locator results in clearHighlight`() {
        val line = "  // just a comment"
        val locator = LocatorExtractor.extract(line)

        assertNull(locator)

        // Simulate what CaretHighlightListener does when no locator found
        service.clearHighlight()

        assertEquals("window.clearHighlight();", capturedJs.last())
    }

    fun `test chained locators extracts last one`() {
        val line = "page.locator('form').locator('input')"
        val locator = LocatorExtractor.extract(line)!!

        assertEquals("locator", locator.type)
        assertEquals("input", locator.cssSelector)
    }

    fun `test getByPlaceholder line highlights with placeholder selector`() {
        val line = "page.getByPlaceholder('Username')"
        val locator = LocatorExtractor.extract(line)!!

        assertEquals("getByPlaceholder", locator.type)
        assertEquals("[placeholder=\"Username\"]", locator.cssSelector)

        service.highlightElement(locator.type, locator.value)

        val js = capturedJs.last()
        assertTrue(js.contains("getByPlaceholder"))
        assertTrue(js.contains("Username"))
    }
}
