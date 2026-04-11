package com.github.artem.pageobjectplugin.buildtools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Parses the Playwright `json` reporter output (default location
 * `packages/playwright-snapshot-saver/test-results/results.json`) into
 * [TestEntry] records. Schema reference:
 * https://playwright.dev/docs/test-reporters#json-reporter
 *
 * Top-level shape:
 * ```
 * {
 *   "suites": [
 *     {
 *       "title": "login.spec.ts",
 *       "file": "tests/login.spec.ts",
 *       "specs": [
 *         {
 *           "title": "logs in",
 *           "line": 12,
 *           "tests": [
 *             {
 *               "results": [
 *                 { "status": "passed" | "failed" | "skipped", "duration": 1234, "error": {...} }
 *               ]
 *             }
 *           ]
 *         }
 *       ],
 *       "suites": [ ...nested... ]
 *     }
 *   ]
 * }
 * ```
 */
object PlaywrightJsonParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(file: File): List<TestEntry> {
        if (!file.isFile) return emptyList()
        val root = json.parseToJsonElement(file.readText()).jsonObject
        val suites = root["suites"]?.jsonArrayOrNull() ?: return emptyList()
        val out = mutableListOf<TestEntry>()
        suites.forEach { walkSuite(it.jsonObject, parentFile = null, out = out) }
        return out
    }

    private fun walkSuite(suite: JsonObject, parentFile: String?, out: MutableList<TestEntry>) {
        val suiteFile = suite["file"]?.jsonPrimitive?.contentOrNull ?: parentFile

        suite["specs"]?.jsonArrayOrNull()?.forEach { specEl ->
            val spec = specEl.jsonObject
            val title = spec["title"]?.jsonPrimitive?.contentOrNull ?: "unknown"
            val line = spec["line"]?.jsonPrimitive?.intOrNull
            val testNodes = spec["tests"]?.jsonArrayOrNull().orEmpty()

            // Each `tests[]` entry holds a list of `results[]` (one per retry).
            // We collapse retries: if any retry failed and a later one passed,
            // mark "flaky"; otherwise use the final result.
            testNodes.forEach { tNode ->
                val results = tNode.jsonObject["results"]?.jsonArrayOrNull().orEmpty()
                if (results.isEmpty()) return@forEach
                val statuses = results.map { it.jsonObject["status"]?.jsonPrimitive?.contentOrNull }
                val finalStatus = statuses.lastOrNull()
                val flaky = statuses.size > 1 && statuses.dropLast(1).any { it == "failed" } && finalStatus == "passed"
                val mappedStatus = when {
                    flaky -> "flaky"
                    finalStatus == "passed" -> "passed"
                    finalStatus == "failed" || finalStatus == "timedOut" -> "failed"
                    finalStatus == "skipped" -> "skipped"
                    else -> "failed"
                }
                val durationMs = results.sumOf { it.jsonObject["duration"]?.jsonPrimitive?.doubleOrNull?.toLong() ?: 0L }
                val failingResult = results.lastOrNull { it.jsonObject["status"]?.jsonPrimitive?.contentOrNull in setOf("failed", "timedOut") }
                val errorMessage = failingResult?.jsonObject?.get("error")?.jsonObject?.get("message")
                    ?.jsonPrimitive?.contentOrNull
                    ?.let { stripAnsi(it).lines().firstOrNull()?.trim() }

                out.add(
                    TestEntry(
                        name = if (suiteFile != null) "$suiteFile > $title" else title,
                        status = mappedStatus,
                        durationMs = durationMs,
                        file = suiteFile,
                        line = line,
                        failureMessage = errorMessage,
                        tracePath = null,
                    )
                )
            }
        }

        suite["suites"]?.jsonArrayOrNull()?.forEach { walkSuite(it.jsonObject, suiteFile, out) }
    }

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = (this as? JsonArray) ?: try {
        jsonArray
    } catch (_: IllegalArgumentException) {
        null
    }

    private val ansiRegex = Regex("\u001B\\[[;\\d]*m")
    private fun stripAnsi(s: String) = ansiRegex.replace(s, "")
}
