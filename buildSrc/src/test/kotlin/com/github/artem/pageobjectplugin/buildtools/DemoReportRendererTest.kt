package com.github.artem.pageobjectplugin.buildtools

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class DemoReportRendererTest {

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
}
