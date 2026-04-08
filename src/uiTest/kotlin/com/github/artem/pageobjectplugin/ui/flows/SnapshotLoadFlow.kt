package com.github.artem.pageobjectplugin.ui.flows

import com.github.artem.pageobjectplugin.ui.pages.EditorPage
import com.github.artem.pageobjectplugin.ui.pages.PluginToolWindowPage
import com.github.artem.pageobjectplugin.ui.support.StepRecorder
import com.intellij.remoterobot.RemoteRobot
import java.time.Duration

/**
 * Multi-page flow that opens the default `login.page.ts` test fixture, kicks
 * the VFS, opens the Page Mirror tool window, and waits until snapshot
 * discovery completes. Used by `BaseUiTest.waitForIde()` and by individual
 * test `@BeforeEach` blocks that need a loaded snapshot.
 */
class SnapshotLoadFlow(private val robot: RemoteRobot) {

    private val editor = EditorPage(robot)
    private val toolWindow = PluginToolWindowPage(robot)

    /**
     * The canonical "test setup" sequence. Idempotent — safe to call multiple
     * times across `@BeforeEach` and the class-level setup.
     */
    fun loadDefaultLoginSnapshot(
        timeout: Duration = Duration.ofSeconds(60),
    ) = StepRecorder.step(
        label = "flow.loadDefaultLoginSnapshot",
        robot = robot,
    ) {
        editor.openFileInEditor("login.page.ts")
        editor.triggerVfsRefresh()
        toolWindow.open()
        toolWindow.waitForSnapshotDiscovery(timeout)
    }
}
