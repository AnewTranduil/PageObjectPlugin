package com.github.artem.pageobjectplugin.ui.support

import com.intellij.remoterobot.RemoteRobot
import java.awt.image.BufferedImage
import java.time.Duration
import java.time.Instant

/**
 * Thread-local collector for test steps and their screenshots. Pages from
 * Task 13d call [step] for each logical action; the test extension flushes
 * the collected entries into `trace.json` and writes any captured screenshots
 * under `<bundle>/screenshots/`.
 *
 * Thread-local because JUnit 5 may run tests in parallel — each test thread
 * gets its own list. [reset] is called from `BeforeEach` and again at the
 * end of `writeBundle` to release memory between tests.
 */
object StepRecorder {

    private val entriesTl = ThreadLocal.withInitial { mutableListOf<StepEntry>() }
    private val imagesTl = ThreadLocal.withInitial { mutableListOf<Pair<String, BufferedImage>>() }

    /** Returns a snapshot copy of the current thread's recorded entries. */
    fun current(): List<StepEntry> = entriesTl.get().toList()

    /** Returns a snapshot copy of the current thread's pending screenshot images. */
    fun images(): List<Pair<String, BufferedImage>> = imagesTl.get().toList()

    /** Clears entries + images for the current thread. */
    fun reset() {
        entriesTl.get().clear()
        imagesTl.get().clear()
    }

    /**
     * Records a single step. Captures a screenshot via [robot] (best-effort)
     * after [block] completes — including on failure, before rethrowing.
     *
     * @param label short human-readable description, e.g. "select snapshot 'login'"
     * @param robot optional RemoteRobot used to grab a screenshot; null skips capture
     * @param block the action to perform; its result is returned to the caller
     */
    fun <T> step(label: String, robot: RemoteRobot? = null, block: () -> T): T {
        val entries = entriesTl.get()
        val images = imagesTl.get()
        val index = entries.size + 1
        val startNanos = System.nanoTime()
        val startedAt = Instant.now()
        var error: Throwable? = null
        try {
            return block()
        } catch (t: Throwable) {
            error = t
            throw t
        } finally {
            val durationMs = Duration.ofNanos(System.nanoTime() - startNanos).toMillis()
            val safeLabel = label.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(60)
            val screenshotRel = if (robot != null) {
                try {
                    val img = robot.getScreenshot()
                    val rel = "screenshots/step-${"%03d".format(index)}-$safeLabel.png"
                    images += rel to img
                    rel
                } catch (_: Throwable) {
                    null
                }
            } else null
            entries += StepEntry(
                index = index,
                label = label,
                at = startedAt.toString(),
                durationMs = durationMs,
                screenshot = screenshotRel,
                error = error?.let { "${it.javaClass.simpleName}: ${it.message}" },
            )
        }
    }
}
