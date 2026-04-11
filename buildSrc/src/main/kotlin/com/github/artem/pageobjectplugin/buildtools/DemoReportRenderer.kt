package com.github.artem.pageobjectplugin.buildtools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * Renders a self-contained Playwright-style trace viewer HTML from a set of
 * Task 13c trace bundle directories. All assets (CSS, JS, screenshots, DOM
 * snapshots) are inlined — the output is a single `index.html` shareable as a
 * CI artifact.
 */
object DemoReportRenderer {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun render(
        bundles: List<Path>,
        outputDir: Path,
        featureTag: String,
        gitSha: String,
        templateDir: Path,
    ): Path {
        Files.createDirectories(outputDir)

        val testsJson = buildJsonArray {
            bundles.forEach { bundleDir ->
                val traceFile = bundleDir.resolve("trace.json")
                if (!Files.exists(traceFile)) return@forEach
                val trace = json.parseToJsonElement(Files.readString(traceFile)) as? JsonObject
                    ?: return@forEach
                add(transformTest(trace, bundleDir))
            }
        }

        val dataBlob = buildJsonObject {
            put("feature", featureTag)
            put("gitSha", gitSha)
            put("tests", testsJson)
        }

        val template = Files.readString(templateDir.resolve("index.html"))
        val styles = Files.readString(templateDir.resolve("styles.css"))
        val app = Files.readString(templateDir.resolve("app.js"))

        val html = template
            .replace("/*__STYLES__*/", styles)
            .replace("/*__APP__*/", app)
            .replace("/*__DATA__*/ null", json.encodeToString(JsonElement.serializer(), dataBlob))

        val out = outputDir.resolve("index.html")
        Files.writeString(out, html)
        return out
    }

    private fun transformTest(trace: JsonObject, bundleDir: Path): JsonElement {
        val testObj = trace["test"] as? JsonObject
        val className = (testObj?.get("className") as? JsonPrimitive)?.content ?: "Unknown"
        val method = (testObj?.get("method") as? JsonPrimitive)?.content ?: "unknown"
        val displayName = (testObj?.get("displayName") as? JsonPrimitive)?.content ?: method
        val feature = (testObj?.get("feature") as? JsonPrimitive)?.contentOrNull()
        val status = (trace["status"] as? JsonPrimitive)?.content ?: "unknown"

        val stepsIn = (trace["steps"] as? JsonArray) ?: emptyList()
        val stepsOut = buildJsonArray {
            stepsIn.forEach { stepEl ->
                val step = stepEl as? JsonObject ?: return@forEach
                val screenshotRel = (step["screenshot"] as? JsonPrimitive)?.contentOrNull()
                val dataUri = screenshotRel?.let { rel ->
                    val p = bundleDir.resolve(rel)
                    if (Files.exists(p)) toDataUri(p, "image/png") else null
                }
                add(buildJsonObject {
                    put("index", (step["index"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0)
                    put("label", (step["label"] as? JsonPrimitive)?.content ?: "")
                    put("durationMs", (step["durationMs"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L)
                    put("error", (step["error"] as? JsonPrimitive)?.contentOrNull())
                    if (dataUri != null) put("screenshotDataUri", dataUri)
                })
            }
        }

        val artifacts = trace["artifacts"] as? JsonObject
        val domRel = (artifacts?.get("dom") as? JsonPrimitive)?.contentOrNull()
        val domDataUri = domRel?.let { rel ->
            val p = bundleDir.resolve(rel)
            if (Files.exists(p)) toDataUri(p, "text/html") else null
        }

        val failureEl = trace["failure"] as? JsonObject

        return buildJsonObject {
            put("className", className)
            put("method", method)
            put("displayName", displayName)
            put("status", status)
            if (feature != null) put("feature", feature)
            put("steps", stepsOut)
            if (failureEl != null) put("failure", failureEl)
            if (domDataUri != null) put("domHtmlDataUri", domDataUri)
        }
    }

    private fun JsonPrimitive.contentOrNull(): String? =
        if (this.content == "null") null else this.content

    private fun toDataUri(path: Path, mime: String): String {
        val bytes = Files.readAllBytes(path)
        return "data:$mime;base64," + Base64.getEncoder().encodeToString(bytes)
    }
}
