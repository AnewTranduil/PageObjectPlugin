package com.github.artem.pageobjectplugin.buildtools

import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses Surefire-format JUnit XML files (`TEST-*.xml`) into [TestEntry]
 * records. Both JUnit4 and JUnit5 emit this shape via Gradle's built-in
 * test reporter, so one parser handles both.
 *
 * The parser does NOT distinguish flaky from passed: that information is
 * not in the XML and comes from the trace.json augmenter for uiTest. For
 * unit tests, "flaky" is always 0.
 */
object JUnitXmlParser {

    /**
     * @param dir directory containing one or more `TEST-*.xml` files; may
     *            also be missing or empty (returns an empty list).
     */
    fun parse(dir: File): List<TestEntry> {
        if (!dir.isDirectory) return emptyList()
        val xmlFiles = dir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("TEST-") && it.extension == "xml" }
            .toList()
        if (xmlFiles.isEmpty()) return emptyList()

        val factory = DocumentBuilderFactory.newInstance().apply {
            // Defensive: disable external entity resolution.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isNamespaceAware = false
            isValidating = false
        }
        val builder = factory.newDocumentBuilder()

        return xmlFiles.flatMap { file ->
            val doc = builder.parse(file)
            val testcases = doc.getElementsByTagName("testcase")
            buildList(testcases.length) {
                for (i in 0 until testcases.length) {
                    add(parseTestcase(testcases.item(i) as Element))
                }
            }
        }
    }

    private fun parseTestcase(el: Element): TestEntry {
        val classname = el.getAttribute("classname").ifEmpty { "Unknown" }
        val name = el.getAttribute("name").ifEmpty { "unknown" }
        val durationMs = (el.getAttribute("time").toDoubleOrNull() ?: 0.0)
            .let { (it * 1000).toLong() }

        val failure = el.firstChildElement("failure") ?: el.firstChildElement("error")
        val skipped = el.firstChildElement("skipped")

        val status = when {
            failure != null -> "failed"
            skipped != null -> "skipped"
            else -> "passed"
        }

        val message = failure?.getAttribute("message")?.takeIf { it.isNotBlank() }
        val stack = failure?.textContent.orEmpty()
        val (file, line) = extractFirstFrame(classname, stack)

        return TestEntry(
            name = "$classname.$name",
            status = status,
            durationMs = durationMs,
            file = file,
            line = line,
            failureMessage = message,
            tracePath = null,
        )
    }

    /**
     * Walk the failure stack top-down looking for the first frame whose
     * source file matches the test class. We return `Foo.kt:42` so the
     * Markdown emitter can render `at Foo.kt:42` directly. Prefers a
     * frame from the test class itself; falls back to the first frame
     * with a source location.
     *
     * Stack frame examples:
     *   at com.github.artem.pageobjectplugin.FooTest.bar(FooTest.kt:42)
     *   at com.github.artem.pageobjectplugin.FooTest.bar(FooTest.java:42)
     */
    private fun extractFirstFrame(classname: String, stack: String): Pair<String?, Int?> {
        if (stack.isBlank()) return null to null
        val classFrameRegex = Regex(
            """at\s+\Q$classname\E\.\S+\(([^:)]+):(\d+)\)"""
        )
        val frameRegex = Regex("""at\s+\S+\(([^:)]+):(\d+)\)""")
        val match = classFrameRegex.find(stack)
            ?: frameRegex.find(stack)
            ?: return null to null
        return match.groupValues[1] to match.groupValues[2].toIntOrNull()
    }

    private fun Element.firstChildElement(tag: String): Element? {
        val list: NodeList = childNodes
        for (i in 0 until list.length) {
            val n = list.item(i)
            if (n.nodeType == Node.ELEMENT_NODE && (n as Element).tagName == tag) {
                return n
            }
        }
        return null
    }
}
