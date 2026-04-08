package com.github.artem.pageobjectplugin.ui.support

import com.intellij.remoterobot.RemoteRobot
import com.intellij.remoterobot.fixtures.CommonContainerFixture
import com.intellij.remoterobot.search.locators.byXpath
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.extension.AfterTestExecutionCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.jupiter.api.extension.TestWatcher
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

/**
 * Replaces ScreenshotOnFailureExtension with a structured per-test trace bundle.
 *
 * On `BeforeEach`:
 *   - Reset the [StepRecorder] thread-local lists
 *   - Start a [CdpConsoleCollector] subscribing to JCEF console events
 *   - Stash the start time + collector in the JUnit store under our namespace
 *
 * On test completion (failed or, when `-PcaptureAllTraces=true`, also passed):
 *   - Create `build/reports/uiTest/traces/<Class>__<method>/`
 *   - Tail `idea.log` from the IPG sandbox (`ui.test.sandbox.dir`)
 *   - Dump the IDE's Swing component tree (best-effort) into `dom.html`
 *   - Flush the CDP buffer into `jcef-console.log`
 *   - Dump `Thread.getAllStackTraces()` via `ideFrame.callJs` into `threads.txt`
 *   - Materialize all pending [StepRecorder.images] under `screenshots/`
 *   - Serialize a [TraceBundle] into `trace.json`
 *
 * Output is written exactly once per test (the final outcome). [TestWatcher]
 * fallbacks cover paths that bypass `afterTestExecution` (aborts, etc).
 */
class TraceBundleExtension :
    BeforeEachCallback, AfterTestExecutionCallback, TestWatcher {

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val rootDir: Path = Path.of("build/reports/uiTest/traces")

    private val captureAll: Boolean =
        System.getProperty("ui.test.captureAllTraces", "false").toBoolean()

    private val robotUrl: String =
        System.getProperty("robot-server.url", "http://localhost:8082")

    override fun beforeEach(context: ExtensionContext) {
        StepRecorder.reset()
        val store = context.getStore(NS)
        store.put(KEY_STARTED_AT, Instant.now())
        store.put(KEY_STARTED_NANOS, System.nanoTime())
        // CDP collector is best-effort and may noop if port 9222 is not bound yet.
        val cdp = CdpConsoleCollector().also { runCatching { it.start() } }
        store.put(KEY_CDP, cdp)
    }

    override fun afterTestExecution(context: ExtensionContext) {
        val failed = context.executionException.isPresent
        if (!failed && !captureAll) {
            // Release CDP socket and step buffers without writing the bundle.
            (context.getStore(NS).get(KEY_CDP) as? CdpConsoleCollector)?.close()
            StepRecorder.reset()
            return
        }
        writeBundle(context, if (failed) "failed" else "passed")
    }

    override fun testAborted(context: ExtensionContext, cause: Throwable?) {
        if (!bundleWritten(context)) writeBundle(context, "aborted")
    }

    override fun testFailed(context: ExtensionContext, cause: Throwable?) {
        if (!bundleWritten(context)) writeBundle(context, "failed")
    }

    private fun bundleWritten(ctx: ExtensionContext): Boolean =
        ctx.getStore(NS).get(KEY_BUNDLE_WRITTEN) as? Boolean == true

    private fun writeBundle(context: ExtensionContext, status: String) {
        val classSimple = context.testClass.map { it.simpleName }.orElse("Unknown")
        val rawMethod = context.testMethod.map { it.name }.orElse("unknown")
        val method = rawMethod.replace(Regex("[^A-Za-z0-9_]"), "_")
        val dir = rootDir.resolve("${classSimple}__${method}")
        try {
            Files.createDirectories(dir)
            Files.createDirectories(dir.resolve("screenshots"))

            val robot = try {
                RemoteRobot(robotUrl)
            } catch (_: Throwable) {
                null
            }

            val ideaLogRel = tailIdeaLog(dir)
            val domRel = dumpDom(dir, robot)
            val cdp = context.getStore(NS).get(KEY_CDP) as? CdpConsoleCollector
            val jcefRel = if (cdp != null) {
                try {
                    cdp.flushTo(dir.resolve("jcef-console.log"))
                } finally {
                    cdp.close()
                }
                "jcef-console.log"
            } else null
            val threadsRel = dumpThreads(dir, robot)

            // Materialize all pending screenshots that StepRecorder collected.
            StepRecorder.images().forEach { (rel, img) ->
                try {
                    val out = dir.resolve(rel)
                    Files.createDirectories(out.parent)
                    ImageIO.write(img, "png", out.toFile())
                } catch (_: Throwable) {
                    // best-effort
                }
            }

            val startedAt = context.getStore(NS).get(KEY_STARTED_AT) as? Instant ?: Instant.now()
            val startedNanos = (context.getStore(NS).get(KEY_STARTED_NANOS) as? Long) ?: System.nanoTime()
            val durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis()

            val failure = context.executionException.orElse(null)?.let { t ->
                TraceFailure(
                    message = t.message ?: t.javaClass.simpleName,
                    stack = t.stackTraceToString(),
                    file = t.stackTrace.firstOrNull()?.fileName,
                    line = t.stackTrace.firstOrNull()?.lineNumber,
                )
            }

            val flaky = context
                .getStore(RetryOnceExtension.NAMESPACE)
                .get(RetryOnceExtension.FLAKY_KEY) as? Boolean == true

            val bundle = TraceBundle(
                test = TraceTest(
                    className = context.testClass.map { it.name }.orElse("Unknown"),
                    method = method,
                    displayName = context.displayName,
                ),
                startedAt = startedAt.toString(),
                durationMs = durationMs,
                status = status,
                flaky = flaky,
                failure = failure,
                steps = StepRecorder.current(),
                artifacts = TraceArtifacts(
                    ideaLog = ideaLogRel,
                    dom = domRel,
                    jcefConsole = jcefRel,
                    threads = threadsRel,
                ),
            )

            Files.writeString(dir.resolve("trace.json"), json.encodeToString(bundle))
            context.getStore(NS).put(KEY_BUNDLE_WRITTEN, true)
        } catch (t: Throwable) {
            System.err.println("[TraceBundleExtension] failed: ${t.javaClass.simpleName}: ${t.message}")
        } finally {
            StepRecorder.reset()
        }
    }

    // ── Artifact collectors ─────────────────────────────────────────────────

    /**
     * Walks `ui.test.sandbox.dir` for any `idea.log`, copies the last 512KB to
     * `<bundleDir>/idea.log`, returns the relative path on success.
     */
    private fun tailIdeaLog(bundleDir: Path): String? {
        val sandbox = System.getProperty("ui.test.sandbox.dir") ?: return null
        return try {
            val sandboxPath = Path.of(sandbox)
            if (!Files.isDirectory(sandboxPath)) return null
            val log: Path = Files.walk(sandboxPath).use { stream ->
                stream.asSequence()
                    .filter { it.isRegularFile() && it.name == "idea.log" }
                    .firstOrNull()
            } ?: return null
            val out = bundleDir.resolve("idea.log")
            val size = Files.size(log)
            val tailBytes = 512L * 1024L
            if (size <= tailBytes) {
                Files.copy(log, out, StandardCopyOption.REPLACE_EXISTING)
            } else {
                Files.newByteChannel(log).use { ch ->
                    ch.position(size - tailBytes)
                    val buf = java.nio.ByteBuffer.allocate(tailBytes.toInt())
                    while (buf.hasRemaining() && ch.read(buf) > 0) { /* drain */ }
                    Files.write(out, buf.array())
                }
            }
            "idea.log"
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Dumps the Swing component tree of whichever containers are most useful
     * for diagnosing the current failure. Tries in order: any open
     * `DialogRootPane` (dialog failures), the Page Mirror tool window, and
     * finally the whole `IdeFrameImpl`. Each captured container becomes a
     * `<section>` in `dom.html`. Walks the tree printing class name +
     * accessible name + visible text for each component, which is enough to
     * rewrite XPath locators.
     */
    private fun dumpDom(bundleDir: Path, robot: RemoteRobot?): String? {
        if (robot == null) return null
        val walkJs = """
            var sb = new java.lang.StringBuilder()
            function walk(c, depth) {
                var indent = ''
                for (var i = 0; i < depth; i++) indent += '  '
                var name = c.getClass().getSimpleName()
                var an = c.getAccessibleContext() != null ? c.getAccessibleContext().getAccessibleName() : null
                var txt = null
                try { if (c.getText) txt = c.getText() } catch (e) {}
                sb.append(indent).append(name)
                if (an != null) sb.append(' ["').append(an).append('"]')
                if (txt != null && txt != '' && txt.length < 80) sb.append(' text="').append(txt).append('"')
                sb.append('\n')
                if (c.getComponents) {
                    var kids = c.getComponents()
                    for (var k = 0; k < kids.length; k++) walk(kids[k], depth + 1)
                }
            }
            walk(component, 0)
            sb.toString()
        """.trimIndent()

        val targets = listOf(
            "dialog" to byXpath("//div[@class='DialogRootPane' or @class='MyDialog']"),
            "toolwindow" to byXpath("//div[@class='InternalDecoratorImpl' and contains(@accessiblename, 'Page Mirror')]"),
            "ideframe" to byXpath("//div[@class='IdeFrameImpl']"),
        )

        val sections = StringBuilder()
        var captured = 0
        for ((label, locator) in targets) {
            try {
                val container = robot.find<CommonContainerFixture>(locator, Duration.ofMillis(500))
                val dump: String = container.callJs(walkJs, runInEdt = true)
                sections.append("<h3>").append(label).append("</h3>\n<pre>")
                    .append(dump.replace("&", "&amp;").replace("<", "&lt;"))
                    .append("</pre>\n")
                captured++
            } catch (_: Throwable) {
                // section not present — skip
            }
        }
        if (captured == 0) return null
        return try {
            Files.writeString(
                bundleDir.resolve("dom.html"),
                "<!doctype html><meta charset=\"utf-8\"><title>UI dump</title>\n$sections",
            )
            "dom.html"
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Dumps `Thread.getAllStackTraces()` from the IDE JVM into `threads.txt`.
     */
    private fun dumpThreads(bundleDir: Path, robot: RemoteRobot?): String? {
        if (robot == null) return null
        return try {
            val ideFrame = robot.find<CommonContainerFixture>(
                byXpath("//div[@class='IdeFrameImpl']"),
                Duration.ofSeconds(2),
            )
            val dump: String = ideFrame.callJs(
                """
                var sb = new java.lang.StringBuilder()
                var traces = java.lang.Thread.getAllStackTraces()
                var it = traces.entrySet().iterator()
                while (it.hasNext()) {
                    var entry = it.next()
                    var t = entry.getKey()
                    sb.append('\n"').append(t.getName()).append('" tid=').append(t.getId())
                      .append(' state=').append(t.getState().name()).append('\n')
                    var stack = entry.getValue()
                    for (var i = 0; i < stack.length; i++) {
                        sb.append('  at ').append(stack[i].toString()).append('\n')
                    }
                }
                sb.toString()
                """.trimIndent(),
                runInEdt = false,
            )
            Files.writeString(bundleDir.resolve("threads.txt"), dump)
            "threads.txt"
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private val NS: Namespace = Namespace.create(TraceBundleExtension::class.java)
        private const val KEY_STARTED_AT = "startedAt"
        private const val KEY_STARTED_NANOS = "startedNanos"
        private const val KEY_CDP = "cdp"
        private const val KEY_BUNDLE_WRITTEN = "bundleWritten"
    }
}
