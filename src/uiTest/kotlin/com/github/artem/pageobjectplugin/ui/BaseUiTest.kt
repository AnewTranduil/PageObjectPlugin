package com.github.artem.pageobjectplugin.ui

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.fixtures.ComponentFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.waitFor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/**
 * Base class for all Page Mirror UI tests.
 *
 * Prerequisites:
 *   - `./gradlew runIdeForUiTests` is running (IDE on port 8082 with robot-server-plugin)
 *   - The IDE was started with test-project/ open (configured in build.gradle.kts)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(ScreenshotOnFailureExtension::class)
abstract class BaseUiTest {

    protected val robot: RemoteRobot by lazy {
        RemoteRobot(robotUrl)
    }

    protected val testProjectDir: Path =
        Path.of(System.getProperty("ui.test.project.dir", "test-project")).toAbsolutePath()

    private val robotUrl: String =
        System.getProperty("robot-server.url", "http://localhost:8082")

    @BeforeAll
    fun waitForIde() {
        ensureRobotServerReachable()

        // Wait until the main IDE frame is visible (project was passed on startup)
        waitFor(Duration.ofSeconds(20)) {
            try {
                ideFrame() != null
            } catch (e: Exception) {
                System.err.println("[waitForIde] ideFrame() failed: ${e.javaClass.simpleName}: ${e.message}")
                false
            }
        }

        // Bring IDE to front — the window may be minimized/iconified
        bringIdeToFront()

        // Give the IDE a moment to finish indexing (longer in CI)
        Thread.sleep(5_000)

        // Ensure the Page Mirror tool window is open before any tests run
        openToolWindow()
        waitFor(Duration.ofSeconds(30)) {
            try {
                robot.find<CommonContainerFixture>(
                    byXpath("//div[@class='InternalDecoratorImpl' and contains(@accessiblename, 'Page Mirror')]"),
                    Duration.ofSeconds(5)
                )
                true
            } catch (_: Exception) {
                System.err.println("[waitForIde] Page Mirror tool window not yet visible, retrying...")
                openToolWindow()
                false
            }
        }

        // Take a diagnostic screenshot of the IDE state
        takeScreenshot("after-tool-window-open")

        // Check if JCEF is available (determines tool window content)
        val jcefAvailable = try {
            ideFrame().callJs<Boolean>("""
                com.intellij.ui.jcef.JBCefApp.isSupported()
            """, runInEdt = true)
        } catch (e: Exception) {
            System.err.println("[waitForIde] JCEF check failed: ${e.message}")
            false
        }
        System.err.println("[waitForIde] JCEF available: $jcefAvailable")

        if (!jcefAvailable) {
            System.err.println("[waitForIde] JCEF not supported — tool window will show fallback label, skipping snapshot discovery")
            takeScreenshot("jcef-not-available")
            Assumptions.abort<Unit>(
                "JCEF is not supported in this CI environment. " +
                    "UI tests requiring the browser component cannot run."
            )
        }

        // Open a .ts file to trigger snapshot discovery, then wait for it to complete
        openFileInEditor("login.page.ts")
        triggerVfsRefresh()
        waitFor(Duration.ofSeconds(60)) {
            try {
                val tw = robot.find<CommonContainerFixture>(
                    byXpath("//div[@class='InternalDecoratorImpl' and contains(@accessiblename, 'Page Mirror')]"),
                    Duration.ofSeconds(5)
                )
                val combo = tw.find<ComponentFixture>(byXpath(".//div[@class='JComboBox']"), Duration.ofSeconds(3))
                val selected: String = combo.callJs("component.getSelectedItem() != null ? '' + component.getSelectedItem() : ''")
                val ready = selected.isNotBlank() && !selected.contains("No snapshots")
                if (!ready) {
                    System.err.println("[waitForIde] Snapshot combo not ready yet: '$selected'")
                }
                ready
            } catch (e: Exception) {
                System.err.println("[waitForIde] Snapshot discovery check failed: ${e.message}")
                false
            }
        }
        System.err.println("[waitForIde] Snapshot discovery complete, tests can proceed")
    }

    private fun ensureRobotServerReachable() {
        val maxAttempts = 5
        val delayMs = 2_000L
        for (attempt in 1..maxAttempts) {
            try {
                val connection = URI(robotUrl).toURL()
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                connection.requestMethod = "GET"
                connection.responseCode
                connection.disconnect()
                return
            } catch (_: Exception) {
                if (attempt == maxAttempts) {
                    Assumptions.abort<Unit>(
                        "Robot server not reachable at $robotUrl after $maxAttempts attempts. " +
                            "Is ./gradlew runIdeForUiTests running?"
                    )
                }
                Thread.sleep(delayMs)
            }
        }
    }

    // ── Screenshot helper ─────────────────────────────────────────────────────

    private val screenshotDir: Path = Path.of("build/screenshots/uiTest")

    /**
     * Captures a screenshot of the IDE window and saves it as a PNG.
     * Uses the Remote Robot server's /screenshot HTTP endpoint.
     * [label] is a short descriptive tag (e.g. "after-snapshot-load").
     */
    protected fun takeScreenshot(label: String) {
        try {
            Files.createDirectories(screenshotDir)
            val className = this::class.simpleName ?: "Unknown"
            val timestamp = System.currentTimeMillis()
            val fileName = "${className}_${label}_$timestamp.png"
            val filePath = screenshotDir.resolve(fileName)

            val client = OkHttpClient()
            val request = Request.Builder()
                .url("$robotUrl/screenshot")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    Files.write(filePath, response.body!!.bytes())
                    println("[screenshot] Saved: $filePath")
                } else {
                    System.err.println("[screenshot] Server returned ${response.code} for '$label'")
                }
            }
        } catch (e: Exception) {
            System.err.println("[screenshot] Failed to capture '$label': ${e.message}")
        }
    }

    // ── Component helpers ─────────────────────────────────────────────────────

    protected fun ideFrame(): CommonContainerFixture =
        robot.find(byXpath("//div[@class='IdeFrameImpl']"), Duration.ofSeconds(10))

    protected fun waitFor(
        timeout: Duration = Duration.ofSeconds(10),
        condition: () -> Boolean,
    ) = com.intellij.remoterobot.utils.waitFor(timeout, condition = condition)

    /**
     * Helper: JS snippet to get the open project (used in all callJs helpers).
     */
    private val getProjectJs = """
        var project = com.intellij.openapi.project.ProjectManager.getInstance().getOpenProjects()[0]
    """.trimIndent()

    /**
     * Brings the IDE window to front and deiconifies it if minimized.
     */
    private fun bringIdeToFront() {
        try {
            ideFrame().callJs<Boolean>("""
                var frame = component
                var state = frame.getExtendedState()
                if ((state & java.awt.Frame.ICONIFIED) != 0) {
                    frame.setExtendedState(state & ~java.awt.Frame.ICONIFIED)
                }
                frame.toFront()
                frame.requestFocus()
                true
            """, runInEdt = true)
            Thread.sleep(500)
        } catch (e: Exception) {
            System.err.println("[bringIdeToFront] failed: ${e.message}")
        }
    }

    /**
     * Opens a file in the editor programmatically using the IDE API.
     */
    protected fun openFileInEditor(fileName: String) {
        ideFrame().callJs<Boolean>("""
            $getProjectJs

            var baseDir = project.getBaseDir()
            function findFile(dir, name) {
                var children = dir.getChildren()
                for (var i = 0; i < children.length; i++) {
                    if (children[i].getName() == name) return children[i]
                    if (children[i].isDirectory()) {
                        var result = findFile(children[i], name)
                        if (result != null) return result
                    }
                }
                return null
            }

            var file = findFile(baseDir, "$fileName")
            if (file != null) {
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(file, true)
            }
            true
        """, runInEdt = true)
        Thread.sleep(2_000)
    }

    /**
     * Moves the caret to a specific line number in the currently active editor.
     */
    protected fun goToLine(line: Int) {
        ideFrame().callJs<Boolean>("""
            $getProjectJs

            var editor = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).getSelectedTextEditor()
            if (editor != null) {
                var lineIndex = $line - 1
                var offset = editor.getDocument().getLineStartOffset(lineIndex)
                editor.getCaretModel().moveToOffset(offset)
                editor.getScrollingModel().scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
            }
            true
        """, runInEdt = true)
        Thread.sleep(500)
    }

    /**
     * Forces a VFS refresh so externally-placed snapshot files become visible to the IDE.
     * Critical in CI where files exist on disk but VFS hasn't picked them up yet.
     */
    protected fun triggerVfsRefresh() {
        try {
            ideFrame().callJs<Boolean>("""
                com.intellij.openapi.vfs.VirtualFileManager.getInstance().refreshWithoutFileWatcher(true)
                true
            """, runInEdt = true)
            Thread.sleep(2_000)
        } catch (e: Exception) {
            System.err.println("[triggerVfsRefresh] failed: ${e.message}")
        }
    }

    /**
     * Opens the Page Mirror tool window programmatically.
     */
    protected fun openToolWindow() {
        ideFrame().callJs<Boolean>("""
            $getProjectJs

            var tw = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Page Mirror")
            if (tw != null) {
                tw.show()
            }
            true
        """, runInEdt = true)
        Thread.sleep(1_000)
    }
}
