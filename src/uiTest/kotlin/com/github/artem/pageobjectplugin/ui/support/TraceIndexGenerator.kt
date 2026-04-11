package com.github.artem.pageobjectplugin.ui.support

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.system.exitProcess

/**
 * Scans `build/reports/uiTest/traces/<Class>__<method>/` directories, reads
 * each `trace.json`, and emits a single `index.html` at the traces root that
 * tabulates every trace with a link into its bundle.
 *
 * Standalone entry point — invoked by a dedicated Gradle task
 * (`generateTraceIndex`) which is wired to `finalizedBy` the `uiTest` task
 * so the index stays in sync with every run.
 *
 * Usage:
 *   java -cp <uiTest runtime classpath> \
 *     com.github.artem.pageobjectplugin.ui.support.TraceIndexGeneratorKt \
 *     [tracesRoot]
 */
object TraceIndexGenerator {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Renders an `index.html` inside [tracesRoot]. Returns the number of
     * bundles found; zero means the directory doesn't exist or contains no
     * `trace.json` files (still writes an empty-state page so the upload
     * never 404s).
     */
    fun generate(tracesRoot: Path): Int {
        Files.createDirectories(tracesRoot)
        val bundles = if (tracesRoot.isDirectory()) {
            tracesRoot.listDirectoryEntries()
                .filter { it.isDirectory() }
                .mapNotNull { dir ->
                    val trace = dir.resolve("trace.json")
                    if (!trace.exists() || !trace.isRegularFile()) return@mapNotNull null
                    runCatching { json.decodeFromString<TraceBundle>(trace.readText()) }
                        .getOrNull()
                        ?.let { dir.name to it }
                }
                .sortedWith(
                    compareByDescending<Pair<String, TraceBundle>> { statusRank(it.second.status) }
                        .thenBy { it.second.test.className }
                        .thenBy { it.second.test.method }
                )
        } else {
            emptyList()
        }

        val html = renderHtml(bundles)
        Files.writeString(tracesRoot.resolve("index.html"), html)
        return bundles.size
    }

    private fun statusRank(status: String): Int = when (status) {
        "failed" -> 3
        "aborted" -> 2
        "passed" -> 1
        else -> 0
    }

    private fun renderHtml(bundles: List<Pair<String, TraceBundle>>): String {
        val totals = bundles.groupingBy { it.second.status }.eachCount()
        val flakyCount = bundles.count { it.second.flaky }
        val sb = StringBuilder()
        sb.append(
            """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <title>Page Mirror — UI test traces</title>
              <style>
                body { font: 14px -apple-system, BlinkMacSystemFont, sans-serif; margin: 2rem; color: #222; }
                h1 { margin-top: 0; }
                .summary { margin-bottom: 1rem; color: #555; }
                .badge { display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: 600; }
                .badge.passed { background: #e6f4ea; color: #137333; }
                .badge.failed { background: #fce8e6; color: #a50e0e; }
                .badge.aborted { background: #fef7e0; color: #b06000; }
                .badge.flaky { background: #fff3cd; color: #856404; margin-left: 6px; }
                table { border-collapse: collapse; width: 100%; }
                th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #eee; vertical-align: top; }
                th { background: #f6f8fa; font-weight: 600; }
                tr:hover td { background: #fafbfc; }
                td.method { font-family: SFMono-Regular, Menlo, monospace; font-size: 13px; }
                td.duration { text-align: right; font-variant-numeric: tabular-nums; color: #555; }
                td.artifacts a { margin-right: 8px; font-size: 12px; }
                .empty { padding: 2rem; background: #f6f8fa; border-radius: 6px; text-align: center; color: #555; }
                details { margin-top: 4px; }
                details summary { cursor: pointer; color: #0969da; font-size: 12px; }
                pre.failure { margin: 6px 0 0; padding: 8px; background: #fff5f5; border-left: 3px solid #a50e0e; font-size: 12px; white-space: pre-wrap; }
              </style>
            </head>
            <body>
              <h1>Page Mirror — UI test traces</h1>
              <p class="summary">
            """.trimIndent(),
        )
        sb.append(bundles.size).append(" bundle(s)")
        if (totals.isNotEmpty()) {
            sb.append(" &middot; ")
            sb.append(
                totals.entries.joinToString(" &middot; ") { (status, count) ->
                    "<span class=\"badge $status\">$status</span> $count"
                },
            )
        }
        if (flakyCount > 0) sb.append(" &middot; <span class=\"badge flaky\">flaky</span> ").append(flakyCount)
        sb.append("</p>\n")

        if (bundles.isEmpty()) {
            sb.append("<div class=\"empty\">No trace bundles were produced. ")
                .append("Pass <code>-PcaptureAllTraces=true</code> to capture passing tests.</div>\n")
        } else {
            sb.append("<table>\n<thead><tr>")
                .append("<th>Status</th><th>Class</th><th>Method</th>")
                .append("<th>Duration</th><th>Steps</th><th>Artifacts</th>")
                .append("</tr></thead>\n<tbody>\n")
            for ((dirName, bundle) in bundles) {
                val safeDir = escape(dirName)
                val classSimple = bundle.test.className.substringAfterLast('.')
                val method = escape(bundle.test.method)
                val duration = "${bundle.durationMs} ms"
                val flakyMark = if (bundle.flaky) "<span class=\"badge flaky\">flaky</span>" else ""
                sb.append("<tr>")
                    .append("<td><span class=\"badge ").append(bundle.status).append("\">")
                    .append(bundle.status).append("</span>").append(flakyMark).append("</td>")
                    .append("<td>").append(escape(classSimple)).append("</td>")
                    .append("<td class=\"method\">").append(method)
                if (bundle.failure != null) {
                    sb.append("<details><summary>failure</summary><pre class=\"failure\">")
                        .append(escape(bundle.failure.message))
                        .append("\n\n")
                        .append(escape(bundle.failure.stack.take(2000)))
                        .append("</pre></details>")
                }
                sb.append("</td>")
                    .append("<td class=\"duration\">").append(duration).append("</td>")
                    .append("<td>").append(bundle.steps.size).append("</td>")
                    .append("<td class=\"artifacts\">")
                    .append("<a href=\"").append(safeDir).append("/trace.json\">trace.json</a>")
                bundle.artifacts.ideaLog?.let {
                    sb.append("<a href=\"").append(safeDir).append("/").append(escape(it)).append("\">idea.log</a>")
                }
                bundle.artifacts.dom?.let {
                    sb.append("<a href=\"").append(safeDir).append("/").append(escape(it)).append("\">dom</a>")
                }
                bundle.artifacts.jcefConsole?.let {
                    sb.append("<a href=\"").append(safeDir).append("/").append(escape(it)).append("\">jcef</a>")
                }
                bundle.artifacts.threads?.let {
                    sb.append("<a href=\"").append(safeDir).append("/").append(escape(it)).append("\">threads</a>")
                }
                sb.append("</td></tr>\n")
            }
            sb.append("</tbody></table>\n")
        }
        sb.append("</body></html>\n")
        return sb.toString()
    }

    private fun escape(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

/** Entry point for the Gradle `generateTraceIndex` task. */
fun main(args: Array<String>) {
    val root = Path.of(args.firstOrNull() ?: "build/reports/uiTest/traces")
    val count = TraceIndexGenerator.generate(root)
    println("[TraceIndexGenerator] wrote ${root.resolve("index.html")} ($count bundles)")
    exitProcess(0)
}
