package com.github.artem.pageobjectplugin.ui

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import com.intellij.remoterobot.utils.keyboard
import com.intellij.remoterobot.utils.waitFor
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import java.awt.event.KeyEvent
import java.net.HttpURLConnection
import java.net.URI
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
        waitFor(Duration.ofMinutes(2)) {
            try {
                ideFrame() != null
            } catch (_: Exception) {
                false
            }
        }
        // Give the IDE a moment to finish indexing
        Thread.sleep(3_000)
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

    // ── Component helpers ─────────────────────────────────────────────────────

    protected fun ideFrame(): CommonContainerFixture =
        robot.find(byXpath("//div[@class='IdeFrameImpl']"), Duration.ofSeconds(10))

    protected fun waitFor(
        timeout: Duration = Duration.ofSeconds(10),
        condition: () -> Boolean,
    ) = com.intellij.remoterobot.utils.waitFor(timeout, condition = condition)

    /**
     * Opens a file in the editor by navigating via Go To File (Ctrl+Shift+N).
     */
    protected fun openFileInEditor(fileName: String) {
        ideFrame().keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_N)
        }
        waitFor(Duration.ofSeconds(5)) {
            robot.findAll<CommonContainerFixture>(
                byXpath("//div[@class='SearchEverywhereUI']")
            ).isNotEmpty()
        }
        robot.find<CommonContainerFixture>(
            byXpath("//div[@class='SearchEverywhereUI']"),
            Duration.ofSeconds(5)
        ).keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
            enterText(fileName)
        }
        Thread.sleep(1_000)
        ideFrame().keyboard { key(KeyEvent.VK_ENTER) }
        Thread.sleep(1_500)
    }

    /**
     * Moves the caret to a specific line number in the currently active editor.
     * Uses Go To Line (Ctrl+G).
     */
    protected fun goToLine(line: Int) {
        ideFrame().keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_G)
        }
        waitFor(Duration.ofSeconds(5)) {
            robot.findAll<CommonContainerFixture>(
                byXpath("//div[@class='JBTextField']")
            ).isNotEmpty()
        }
        robot.findAll<CommonContainerFixture>(
            byXpath("//div[@class='JBTextField']")
        ).firstOrNull()?.keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A)
            enterText(line.toString())
            key(KeyEvent.VK_ENTER)
        }
        Thread.sleep(500)
    }

    /**
     * Opens the Page Mirror tool window via the Actions search (Ctrl+Shift+A).
     */
    protected fun openToolWindow() {
        ideFrame().keyboard {
            hotKey(KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_A)
        }
        Thread.sleep(500)
        robot.findAll<CommonContainerFixture>(
            byXpath("//div[@class='SearchEverywhereUI']")
        ).firstOrNull()?.keyboard {
            enterText("Page Mirror")
        }
        Thread.sleep(500)
        robot.findAll<CommonContainerFixture>(
            byXpath("//div[contains(@text, 'Page Mirror')]")
        ).firstOrNull()?.click()
        Thread.sleep(1_000)
    }
}
