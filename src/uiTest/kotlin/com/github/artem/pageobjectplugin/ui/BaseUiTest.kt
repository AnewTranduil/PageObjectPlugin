package com.github.artem.pageobjectplugin.ui

import com.github.artem.pageobjectplugin.ui.flows.SnapshotLoadFlow
import com.github.artem.pageobjectplugin.ui.locators.IntelliJLocators
import com.github.artem.pageobjectplugin.ui.support.RetryOnceExtension
import com.github.artem.pageobjectplugin.ui.support.StepRecorder
import com.github.artem.pageobjectplugin.ui.support.TraceBundleExtension
import com.github.artem.pageobjectplugin.ui.support.Wait
import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path
import java.time.Duration

/**
 * Base class for all Page Mirror UI tests. See `CLAUDE.md` "UI Test
 * Conventions" for the layering rules. Prerequisite: `./gradlew
 * runIdeForUiTests` running (IDE on port 8082, packages/test-project open).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(RetryOnceExtension::class)
@ExtendWith(TraceBundleExtension::class)
abstract class BaseUiTest {

    protected val robot: RemoteRobot by lazy { RemoteRobot(robotUrl) }

    protected val testProjectDir: Path =
        Path.of(System.getProperty("ui.test.project.dir", "test-project")).toAbsolutePath()

    private val robotUrl: String =
        System.getProperty("robot-server.url", "http://localhost:8082")

    @BeforeAll
    fun waitForIde() {
        ensureRobotServerReachable()
        SnapshotLoadFlow(robot).loadDefaultLoginSnapshot()
    }

    /**
     * Fast-fail health check: HEAD-poll the robot server every 500ms for up to 30s.
     * If the server never appears, abort the test (treated as skipped, not failed)
     * so running `uiTest` without `runIdeForUiTests` yields a clear "runner missing"
     * signal in CI rather than a 2-minute hang.
     */
    private fun ensureRobotServerReachable() {
        try {
            Wait.pollUntilTrue(
                timeout = Duration.ofSeconds(30),
                interval = Duration.ofMillis(500),
                message = { "robot-server unreachable at $robotUrl" },
            ) {
                val connection = URI(robotUrl).toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 400
                connection.readTimeout = 400
                connection.requestMethod = "HEAD"
                val code = try { connection.responseCode } finally { connection.disconnect() }
                code in 100..599
            }
        } catch (e: AssertionError) {
            Assumptions.abort<Unit>(
                "Robot server not reachable at $robotUrl after 30s " +
                    "(last: ${e.cause?.let { "${it.javaClass.simpleName}: ${it.message}" } ?: "none"}). " +
                    "Is ./gradlew runIdeForUiTests running?"
            )
        }
    }

    /** Locates the IDE frame — kept here because tests use it for keyboard shortcuts. */
    protected fun ideFrame(): CommonContainerFixture =
        robot.find(IntelliJLocators.ideFrame, Duration.ofSeconds(10))

    /** Convenience wrapper around Remote Robot's `waitFor`. */
    protected fun waitFor(
        timeout: Duration = Duration.ofSeconds(10),
        condition: () -> Boolean,
    ) = com.intellij.remoterobot.utils.waitFor(timeout, condition = condition)

    /**
     * Records a marker step. The screenshot is captured by [StepRecorder.step]
     * and materialized into the per-test trace bundle by [TraceBundleExtension].
     */
    protected fun takeScreenshot(label: String) {
        StepRecorder.step(label, robot) { /* marker — capture only */ }
    }
}
