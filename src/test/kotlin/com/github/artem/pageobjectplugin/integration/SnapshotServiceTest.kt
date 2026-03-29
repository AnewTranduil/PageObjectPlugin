package com.github.artem.pageobjectplugin.integration

import com.github.artem.pageobjectplugin.fixtures.SnapshotFixtures
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class SnapshotServiceTest : BasePlatformTestCase() {

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

    fun `test loadSnapshot sets snapshotDocument`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        service.loadSnapshot(bundle)

        assertNotNull(service.snapshotDocument)
        assertEquals(1, service.snapshotDocument!!.select("[data-testid=login-username]").size)
    }

    fun `test loadSnapshot emits window loadSnapshot call`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        service.loadSnapshot(bundle)

        assertTrue(capturedJs.any { it.startsWith("window.loadSnapshot(") })
    }

    fun `test loadSnapshot sets currentBundle`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        service.loadSnapshot(bundle)

        assertEquals(bundle, service.currentBundle)
    }

    fun `test loadSnapshot notifies listeners`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        var listenerFired = false
        service.addSnapshotListener { listenerFired = true }

        service.loadSnapshot(bundle)

        assertTrue(listenerFired)
    }

    fun `test highlightElement emits correct JS`() {
        service.highlightElement("locator", "#username")

        assertTrue(capturedJs.any { it.startsWith("window.highlightElement(") && it.contains("locator") && it.contains("#username") })
    }

    fun `test clearHighlight emits correct JS`() {
        service.clearHighlight()

        assertEquals("window.clearHighlight();", capturedJs.last())
    }

    fun `test updateAvailableSnapshots with no current auto loads first bundle`() {
        val bundleA = SnapshotFixtures.createMinimalSnapshotDir()
        val bundleB = SnapshotFixtures.createMinimalSnapshotDir()

        service.updateAvailableSnapshots(listOf(bundleA, bundleB))

        assertEquals(bundleA, service.currentBundle)
        assertTrue(capturedJs.any { it.startsWith("window.loadSnapshot(") })
    }

    fun `test updateAvailableSnapshots with existing current does not auto load again`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        service.loadSnapshot(bundle)
        val loadCallsBefore = capturedJs.count { it.startsWith("window.loadSnapshot(") }

        service.updateAvailableSnapshots(listOf(bundle))

        val loadCallsAfter = capturedJs.count { it.startsWith("window.loadSnapshot(") }
        assertEquals(loadCallsBefore, loadCallsAfter)
    }

    fun `test loadSnapshot snapshotDocument is set before listeners fire`() {
        val bundle = SnapshotFixtures.createMinimalSnapshotDir()
        var documentAtNotification: org.jsoup.nodes.Document? = null
        service.addSnapshotListener { documentAtNotification = service.snapshotDocument }

        service.loadSnapshot(bundle)

        assertNotNull(documentAtNotification)
    }
}
