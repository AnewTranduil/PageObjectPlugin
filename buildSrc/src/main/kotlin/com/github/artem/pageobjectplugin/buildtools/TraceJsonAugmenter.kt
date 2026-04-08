package com.github.artem.pageobjectplugin.buildtools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Reads `trace.json` files written by `TraceBundleExtension` and merges
 * the data into matching uiTest [TestEntry]s. Specifically:
 *
 * - Sets `status = "flaky"` when `trace.json.flaky == true` and the test
 *   eventually passed (RetryOnceExtension semantics).
 * - Fills `tracePath` so the Markdown emitter can render
 *   `Trace: build/reports/uiTest/traces/<dir>/`.
 * - Backfills `file` / `line` from `trace.json.failure` when the JUnit XML
 *   stack frame did not yield them.
 *
 * The matching key is `<ClassSimpleName>__<method-with-non-word-replaced-by-_>`,
 * which is exactly the directory naming rule from
 * `TraceBundleExtension.writeBundle` (TraceBundleExtension.kt:91-95).
 */
object TraceJsonAugmenter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * @param tracesRoot the directory passed to `TraceBundleExtension`,
     *                   e.g. `build/reports/uiTest/traces/`.
     * @param entries the parsed uiTest entries to mutate.
     * @param relativizeBase the directory that will be the parent of the
     *                       trace bundle in the final report layout (used
     *                       to compute the `tracePath` field). Typically
     *                       the project root or the report root.
     */
    fun augment(
        tracesRoot: File,
        entries: List<TestEntry>,
        relativizeBase: File,
    ): List<TestEntry> {
        if (!tracesRoot.isDirectory) return entries
        val byKey = entries.associateBy { entryKey(it.name) }
        val updated = entries.toMutableList()
        val updatedIndex = entries.withIndex().associate { (i, e) -> entryKey(e.name) to i }

        tracesRoot.listFiles()?.forEach { bundleDir ->
            if (!bundleDir.isDirectory) return@forEach
            val key = bundleDir.name
            val entryIndex = updatedIndex[key] ?: return@forEach
            val original = byKey[key] ?: return@forEach
            val traceJson = bundleDir.resolve("trace.json")
            val tracePath = relativize(relativizeBase, bundleDir).trimEnd('/') + "/"

            val merged = if (traceJson.isFile) {
                val obj = runCatching { json.parseToJsonElement(traceJson.readText()).jsonObject }.getOrNull()
                if (obj == null) {
                    original.copy(tracePath = tracePath)
                } else {
                    val flaky = obj["flaky"]?.jsonPrimitive?.booleanOrNull == true
                    val failure = obj["failure"]?.jsonObject
                    val newStatus = if (flaky && original.status == "passed") "flaky" else original.status
                    val newFile = original.file ?: failure?.get("file")?.jsonPrimitive?.contentOrNull
                    val newLine = original.line ?: failure?.get("line")?.jsonPrimitive?.intOrNull
                    val newMessage = original.failureMessage ?: failure?.get("message")?.jsonPrimitive?.contentOrNull
                    original.copy(
                        status = newStatus,
                        file = newFile,
                        line = newLine,
                        failureMessage = newMessage,
                        tracePath = tracePath,
                    )
                }
            } else {
                original.copy(tracePath = tracePath)
            }
            updated[entryIndex] = merged
        }
        return updated
    }

    /**
     * Mirrors `TraceBundleExtension.writeBundle`:
     *   classSimple + "__" + method.replace(Regex("[^A-Za-z0-9_]"), "_")
     */
    private fun entryKey(name: String): String {
        // name is "FQCN.method" — strip to simple class.
        val lastDot = name.lastIndexOf('.')
        if (lastDot < 0) return name
        val fqcn = name.substring(0, lastDot)
        val method = name.substring(lastDot + 1)
        val simple = fqcn.substringAfterLast('.')
        val safeMethod = method.replace(Regex("[^A-Za-z0-9_]"), "_")
        return "${simple}__${safeMethod}"
    }

    private fun relativize(base: File, target: File): String {
        val basePath = base.absoluteFile.toPath()
        val targetPath = target.absoluteFile.toPath()
        return runCatching { basePath.relativize(targetPath).toString() }
            .getOrDefault(target.absolutePath)
            .replace('\\', '/')
    }
}
