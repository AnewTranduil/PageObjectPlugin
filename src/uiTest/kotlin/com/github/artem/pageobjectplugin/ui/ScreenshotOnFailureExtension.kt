package com.github.artem.pageobjectplugin.ui

import com.intellij.remoterobot.RemoteRobot
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

/**
 * JUnit 5 extension that automatically captures a screenshot of the IDE
 * when a test fails. Screenshots are saved to build/screenshots/uiTest/
 * with the naming pattern: ClassName_methodName_FAILED_timestamp.png
 *
 * Uses Remote Robot's getScreenshot() API for full-screen capture.
 * Registered on BaseUiTest via @ExtendWith.
 */
class ScreenshotOnFailureExtension : TestWatcher {

    private val screenshotDir: Path = Path.of("build/screenshots/uiTest")
    private val robotUrl: String =
        System.getProperty("robot-server.url", "http://localhost:8082")

    override fun testFailed(context: ExtensionContext, cause: Throwable?) {
        captureScreenshot(context, "FAILED")
    }

    override fun testAborted(context: ExtensionContext, cause: Throwable?) {
        captureScreenshot(context, "ABORTED")
    }

    private fun captureScreenshot(context: ExtensionContext, status: String) {
        try {
            Files.createDirectories(screenshotDir)
            val className = context.testClass.map { it.simpleName }.orElse("Unknown")
            val methodName = context.testMethod.map { it.name }.orElse("unknown")
                .replace(' ', '_').replace(Regex("[^a-zA-Z0-9_]"), "")
            val timestamp = System.currentTimeMillis()
            val fileName = "${className}_${methodName}_${status}_$timestamp.png"
            val filePath = screenshotDir.resolve(fileName)

            val robot = RemoteRobot(robotUrl)
            val image = robot.getScreenshot()
            ImageIO.write(image, "png", filePath.toFile())
            System.err.println("[screenshot] $status: $filePath")
        } catch (e: Exception) {
            System.err.println("[screenshot] Failed to capture for ${context.displayName}: ${e.message}")
        }
    }
}
