package com.github.artem.pageobjectplugin.ui

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher
import java.nio.file.Files
import java.nio.file.Path

/**
 * JUnit 5 extension that automatically captures a screenshot of the IDE
 * when a test fails. Screenshots are saved to build/reports/tests/uiTest/screenshots/
 * with the naming pattern: ClassName_methodName_FAILED_timestamp.png
 *
 * Register on test classes via @ExtendWith(ScreenshotOnFailureExtension::class)
 * or inherit from BaseUiTest which registers it automatically.
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

            val client = OkHttpClient()
            val request = Request.Builder()
                .url("$robotUrl/screenshot")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    Files.write(filePath, response.body!!.bytes())
                    System.err.println("[screenshot] $status: $filePath")
                } else {
                    System.err.println("[screenshot] Server returned ${response.code} for $className.$methodName")
                }
            }
        } catch (e: Exception) {
            System.err.println("[screenshot] Failed to capture for ${context.displayName}: ${e.message}")
        }
    }
}
