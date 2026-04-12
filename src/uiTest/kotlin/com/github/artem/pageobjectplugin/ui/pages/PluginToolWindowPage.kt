package com.github.artem.pageobjectplugin.ui.pages

import com.github.artem.pageobjectplugin.ui.fixtures.PageMirrorToolWindowFixture
import com.github.artem.pageobjectplugin.ui.fixtures.SnapshotBrowserFixture
import com.github.artem.pageobjectplugin.ui.locators.IntelliJLocators
import com.github.artem.pageobjectplugin.ui.support.StepRecorder
import com.github.artem.pageobjectplugin.ui.support.Wait
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import java.time.Duration

/**
 * Page object for the Page Mirror tool window. Composes the existing
 * [PageMirrorToolWindowFixture] and [SnapshotBrowserFixture] so tests
 * never touch fixtures directly.
 */
class PluginToolWindowPage(private val robot: RemoteRobot) {

    private fun ideFrame(): CommonContainerFixture =
        robot.find(IntelliJLocators.ideFrame, Duration.ofSeconds(10))

    private fun fixture(): PageMirrorToolWindowFixture =
        PageMirrorToolWindowFixture.find(robot)

    /**
     * Programmatically opens the Page Mirror tool window via ToolWindowManager
     * and waits until it becomes visible. Idempotent.
     */
    fun open(): PluginToolWindowPage = StepRecorder.step(
        label = "openToolWindow(Page Mirror)",
        robot = robot,
    ) {
        ideFrame().callJs<Boolean>("""
            var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
            var tw = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Page Mirror")
            if (tw != null) {
                tw.show()
            }
            true
        """, runInEdt = true)
        Wait.pollUntilTrue(
            timeout = Duration.ofSeconds(10),
            interval = Duration.ofMillis(100),
            message = { "Page Mirror tool window did not become visible" },
        ) {
            PageMirrorToolWindowFixture.isVisible(robot)
        }
        this
    }

    fun isVisible(): Boolean = PageMirrorToolWindowFixture.isVisible(robot)

    fun selectedSnapshotName(): String = fixture().selectedSnapshotName()

    fun allSnapshotNames(): List<String> = fixture().allSnapshotNames()

    fun selectSnapshot(partialName: String): PluginToolWindowPage = StepRecorder.step(
        label = "selectSnapshot($partialName)",
        robot = robot,
    ) {
        fixture().selectSnapshot(partialName)
        this
    }

    fun refresh(): PluginToolWindowPage = StepRecorder.step(
        label = "refreshSnapshotList",
        robot = robot,
    ) {
        fixture().refreshButton.click()
        this
    }

    fun isBrowserVisible(): Boolean = try {
        fixture().isBrowserVisible()
    } catch (_: Exception) { false }

    fun isSnapshotLoaded(): Boolean = try {
        SnapshotBrowserFixture.findInsideToolWindow(fixture()).isSnapshotLoaded()
    } catch (_: Exception) { false }

    /**
     * Polls the snapshot combo box until it reports a non-empty selection
     * (other than "No snapshots"). Used by `SnapshotLoadFlow` after VFS refresh.
     */
    fun waitForSnapshotDiscovery(timeout: Duration = Duration.ofSeconds(30)): PluginToolWindowPage =
        StepRecorder.step(
            label = "waitForSnapshotDiscovery",
            robot = robot,
        ) {
            Wait.pollUntilTrue(
                timeout = timeout,
                interval = Duration.ofMillis(200),
                message = { "snapshot combo never populated" },
            ) {
                try {
                    val name = fixture().selectedSnapshotName()
                    name.isNotBlank() && !name.contains("No snapshot")
                } catch (_: Exception) {
                    false
                }
            }
            this
        }

    /** Direct access to the underlying fixture for the small set of legacy tests
     *  that still need it during the 13d transition. New tests should not call this. */
    fun fixtureForLegacyAccess(): PageMirrorToolWindowFixture = fixture()

    /** Returns the JCEF browser fixture inside this tool window. */
    fun browser(): SnapshotBrowserFixture =
        SnapshotBrowserFixture.findInsideToolWindow(fixture())

    /**
     * Clicks the "Show All" toolbar button. Toggles highlight-all mode
     * (on first click: highlights every locator in the active page
     * object; second click: clears all highlights).
     */
    fun clickShowAll(): PluginToolWindowPage = StepRecorder.step(
        label = "clickShowAll",
        robot = robot,
    ) {
        fixture().showAllButton.click()
        this
    }
}
