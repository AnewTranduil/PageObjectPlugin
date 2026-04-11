package com.github.artem.pageobjectplugin.buildtools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class DemoReportRendererTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `renders self-contained HTML with test data`(@TempDir tmp: Path) {
        val bundle = tmp.resolve("traces/SampleUiTest__logs_in")
        Files.createDirectories(bundle.resolve("screenshots"))
        bundle.resolve("screenshots/step-1.png").writeText("fake-png-bytes")
        bundle.resolve("trace.json").writeText(
            """
            {
              "version":1,
              "test":{"className":"SampleUiTest","method":"logs_in","displayName":"logs in","feature":"smoke"},
              "startedAt":"2026-04-09T00:00:00Z","durationMs":1234,"status":"passed","flaky":false,
              "steps":[{"index":0,"label":"click login","at":"2026-04-09T00:00:00Z","durationMs":42,
                        "screenshot":"screenshots/step-1.png","error":null}],
              "artifacts":{"ideaLog":null,"dom":null,"jcefConsole":null,"threads":null}
            }
            """.trimIndent(),
        )

        val templateDir = tmp.resolve("template").also { Files.createDirectories(it) }
        templateDir.resolve("index.html").writeText(
            "<html><style>/*__STYLES__*/</style><script>window.__TRACE_DATA__ = /*__DATA__*/ null;</script>" +
                "<script>/*__APP__*/</script></html>",
        )
        templateDir.resolve("styles.css").writeText("body{}")
        templateDir.resolve("app.js").writeText("console.log('ok');")

        val out = tmp.resolve("out")
        val result = DemoReportRenderer.render(
            bundles = listOf(bundle),
            outputDir = out,
            featureTag = "smoke",
            gitSha = "deadbeef",
            templateDir = templateDir,
        )

        assertTrue(Files.exists(result))
        val html = Files.readString(result)
        assertTrue(html.contains("click login"), "step label must be present")
        assertTrue(html.contains("smoke"), "feature tag must be present")
        assertTrue(html.contains("deadbeef"), "git sha must be present")
        assertTrue(html.contains("data:image/png;base64,"), "screenshot must be base64-inlined")
        assertTrue(html.contains("console.log('ok');"), "app.js must be inlined")
        assertFalse(html.contains("/*__DATA__*/"), "placeholder must be substituted")
        assertFalse(html.contains("/*__STYLES__*/"), "styles placeholder must be substituted")
        assertFalse(html.contains("/*__APP__*/"), "app placeholder must be substituted")
    }

    @Test
    fun `embeds per-test feature so the viewer can group tests`(@TempDir tmp: Path) {
        val templateDir = writeMinimalTemplate(tmp)

        val happy = writeBundle(
            tmp.resolve("traces/LoginUiTest__happy"),
            className = "LoginUiTest",
            method = "happy_path",
            displayName = "happy path",
            feature = "login",
            status = "passed",
        )
        val negative = writeBundle(
            tmp.resolve("traces/LoginUiTest__bad"),
            className = "LoginUiTest",
            method = "bad_password",
            displayName = "bad password",
            feature = "login",
            status = "failed",
        )
        val other = writeBundle(
            tmp.resolve("traces/SearchUiTest__suggest"),
            className = "SearchUiTest",
            method = "suggests_results",
            displayName = "suggests results",
            feature = "search",
            status = "passed",
        )

        val out = tmp.resolve("out")
        val result = DemoReportRenderer.render(
            bundles = listOf(happy, negative, other),
            outputDir = out,
            featureTag = "all",
            gitSha = "cafef00d",
            templateDir = templateDir,
        )

        val payload = extractTraceData(Files.readString(result))
        val tests = payload["tests"] as JsonArray
        assertEquals(3, tests.size)

        val byMethod = tests.associateBy { (it as JsonObject)["method"]!!.let { p -> (p as JsonPrimitive).content } }
        assertEquals("login", featureOf(byMethod["happy_path"]!!))
        assertEquals("login", featureOf(byMethod["bad_password"]!!))
        assertEquals("search", featureOf(byMethod["suggests_results"]!!))
    }

    @Test
    fun `per-test feature is omitted when the trace has none`(@TempDir tmp: Path) {
        val templateDir = writeMinimalTemplate(tmp)
        val bundle = writeBundle(
            tmp.resolve("traces/UntaggedUiTest__runs"),
            className = "UntaggedUiTest",
            method = "runs",
            displayName = "runs",
            feature = null,
            status = "passed",
        )

        val out = tmp.resolve("out")
        val result = DemoReportRenderer.render(
            bundles = listOf(bundle),
            outputDir = out,
            featureTag = "all",
            gitSha = "abcdef",
            templateDir = templateDir,
        )

        val payload = extractTraceData(Files.readString(result))
        val test = (payload["tests"] as JsonArray).single() as JsonObject
        assertFalse(test.containsKey("feature"), "feature must be omitted when not tagged")
    }

    private fun featureOf(el: kotlinx.serialization.json.JsonElement): String? {
        val obj = el as JsonObject
        return (obj["feature"] as? JsonPrimitive)?.content
    }

    private fun extractTraceData(html: String): JsonObject {
        val marker = "window.__TRACE_DATA__ = "
        val start = html.indexOf(marker)
        assertTrue(start >= 0, "trace data assignment must be present")
        val from = start + marker.length
        val end = html.indexOf(";</script>", from)
        assertTrue(end > from, "trace data terminator must be present")
        val raw = html.substring(from, end).trim()
        val parsed = json.parseToJsonElement(raw)
        assertNotNull(parsed)
        return parsed as JsonObject
    }

    private fun writeMinimalTemplate(tmp: Path): Path {
        val templateDir = tmp.resolve("template").also { Files.createDirectories(it) }
        templateDir.resolve("index.html").writeText(
            "<html><style>/*__STYLES__*/</style><script>window.__TRACE_DATA__ = /*__DATA__*/ null;</script>" +
                "<script>/*__APP__*/</script></html>",
        )
        templateDir.resolve("styles.css").writeText("body{}")
        templateDir.resolve("app.js").writeText("console.log('ok');")
        return templateDir
    }

    private fun writeBundle(
        dir: Path,
        className: String,
        method: String,
        displayName: String,
        feature: String?,
        status: String,
    ): Path {
        Files.createDirectories(dir)
        val featureField = if (feature == null) "null" else "\"$feature\""
        dir.resolve("trace.json").writeText(
            """
            {
              "version":1,
              "test":{"className":"$className","method":"$method","displayName":"$displayName","feature":$featureField},
              "startedAt":"2026-04-09T00:00:00Z","durationMs":10,"status":"$status","flaky":false,
              "steps":[{"index":0,"label":"do thing","at":"2026-04-09T00:00:00Z","durationMs":5,
                        "screenshot":null,"error":null}],
              "artifacts":{"ideaLog":null,"dom":null,"jcefConsole":null,"threads":null}
            }
            """.trimIndent(),
        )
        return dir
    }
}
