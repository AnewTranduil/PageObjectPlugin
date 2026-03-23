package com.github.artem.pageobjectplugin.integration

import com.github.artem.pageobjectplugin.annotators.SelectorValidationAnnotator
import com.github.artem.pageobjectplugin.fixtures.SnapshotFixtures
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class AnnotatorIntegrationTest : BasePlatformTestCase() {

    private lateinit var service: SnapshotService

    override fun setUp() {
        super.setUp()
        service = SnapshotService.getInstance(project)
        service.jsExecutor = { _ -> }
    }

    override fun tearDown() {
        service.resetStateForTesting()
        super.tearDown()
    }

    private fun loadMinimalSnapshot() {
        service.loadSnapshot(SnapshotFixtures.createMinimalSnapshotDir())
    }

    fun `test collectInformation returns null for non ts file`() {
        loadMinimalSnapshot()
        val file = myFixture.configureByText("Foo.java", "class Foo {}")
        val annotator = SelectorValidationAnnotator()

        assertNull(annotator.collectInformation(file))
    }

    fun `test collectInformation returns null when no snapshot loaded`() {
        // snapshotDocument stays null — returns null regardless of extension
        val file = myFixture.configureByText("test.txt", "page.locator('#foo');")
        val annotator = SelectorValidationAnnotator()

        assertNull(annotator.collectInformation(file))
    }

    fun `test doAnnotate returns non null when snapshot loaded`() {
        loadMinimalSnapshot()
        // doAnnotate does not check file extension — tests annotation logic directly
        val file = myFixture.configureByText("test.txt", "page.locator('#username');")
        val annotator = SelectorValidationAnnotator()

        assertNotNull(annotator.doAnnotate(file))
    }

    fun `test doAnnotate single match selector returns count 1`() {
        loadMinimalSnapshot()
        // MINIMAL_HTML has exactly one element with data-testid="login-username"
        val file = myFixture.configureByText("test.txt", "usernameInput = this.page.getByTestId('login-username');")
        val annotator = SelectorValidationAnnotator()

        val annotations = annotator.doAnnotate(file)!!

        assertEquals(1, annotations.size)
        assertEquals(1, annotations[0].matchCount)
    }

    fun `test doAnnotate zero match selector returns count 0`() {
        loadMinimalSnapshot()
        val file = myFixture.configureByText("test.txt", "x = this.page.locator('.nonexistent-class-xyz');")
        val annotator = SelectorValidationAnnotator()

        val annotations = annotator.doAnnotate(file)!!

        assertEquals(1, annotations.size)
        assertEquals(0, annotations[0].matchCount)
    }

    fun `test doAnnotate multi match selector returns count greater than 1`() {
        loadMinimalSnapshot()
        // MINIMAL_HTML has 2 inputs (username + password)
        val file = myFixture.configureByText("test.txt", "inputs = this.page.locator('input');")
        val annotator = SelectorValidationAnnotator()

        val annotations = annotator.doAnnotate(file)!!

        assertEquals(1, annotations.size)
        assertTrue(annotations[0].matchCount >= 2)
    }

    fun `test doAnnotate comment line not included in annotations`() {
        loadMinimalSnapshot()
        val file = myFixture.configureByText(
            "test.txt",
            "// just a comment\nusernameInput = this.page.getByTestId('login-username');"
        )
        val annotator = SelectorValidationAnnotator()

        val annotations = annotator.doAnnotate(file)!!

        // Only the locator line should produce an annotation, not the comment
        assertEquals(1, annotations.size)
    }

    fun `test doAnnotate multiple locator lines each get annotation`() {
        loadMinimalSnapshot()
        val file = myFixture.configureByText(
            "test.txt",
            "a = this.page.getByTestId('login-username');\nb = this.page.getByTestId('login-password');"
        )
        val annotator = SelectorValidationAnnotator()

        val annotations = annotator.doAnnotate(file)!!

        assertEquals(2, annotations.size)
        assertTrue(annotations.all { it.matchCount == 1 })
    }

    fun `test doAnnotate getByText match`() {
        loadMinimalSnapshot()
        // MINIMAL_HTML has "Bad credentials" text in the flash div
        val file = myFixture.configureByText("test.txt", "err = this.page.getByText('Bad credentials');")
        val annotator = SelectorValidationAnnotator()

        val annotations = annotator.doAnnotate(file)!!

        assertEquals(1, annotations.size)
        assertTrue(annotations[0].matchCount >= 1)
    }
}
