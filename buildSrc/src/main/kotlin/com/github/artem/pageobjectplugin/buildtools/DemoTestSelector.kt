package com.github.artem.pageobjectplugin.buildtools

import java.nio.file.Files
import java.nio.file.Path

/**
 * Selects UI test methods for the `demoReport` task by (1) scanning
 * `src/uiTest/kotlin` for `@Feature("<tag>")` annotations and (2) including
 * test classes whose package matches the package of any changed source file
 * under `src/main/kotlin`.
 *
 * Scanning is done via regex over Kotlin source — no reflection, no compile
 * step. The regex is intentionally conservative; if a test uses `@Feature` in
 * an unusual form (e.g., constant reference) it will not be picked up, and
 * authors should inline the literal tag.
 */
object DemoTestSelector {

    data class Result(val selected: List<String>, val taggedCount: Int)

    private val featureRegex = Regex("""@Feature\(\s*"([^"]+)"\s*\)""")
    private val packageRegex = Regex("""^\s*package\s+([\w.]+)""", RegexOption.MULTILINE)
    private val classRegex = Regex("""class\s+(\w+)""")
    private val funRegex = Regex("""fun\s+(\w+)\s*\(""")

    fun select(projectDir: Path, featureTag: String, changedFiles: List<String>): Result {
        val uiTestRoot = projectDir.resolve("src/uiTest/kotlin")
        if (!Files.isDirectory(uiTestRoot)) return Result(emptyList(), 0)

        val changedPackages: Set<String> = changedFiles
            .filter { it.startsWith("src/main/kotlin/") && it.endsWith(".kt") }
            .mapNotNull { rel ->
                val withoutRoot = rel.removePrefix("src/main/kotlin/")
                val slash = withoutRoot.lastIndexOf('/')
                if (slash <= 0) null else withoutRoot.substring(0, slash).replace('/', '.')
            }
            .toSet()

        val selected = linkedSetOf<String>()
        var taggedCount = 0

        Files.walk(uiTestRoot).use { stream ->
            stream.filter { it.toString().endsWith(".kt") && Files.isRegularFile(it) }.forEach { file ->
                val text = Files.readString(file)
                val pkg = packageRegex.find(text)?.groupValues?.get(1) ?: return@forEach
                val className = classRegex.find(text)?.groupValues?.get(1) ?: return@forEach
                val fqn = "$pkg.$className"

                // Class-level tag
                val classTag = featureRegex.findAll(text.substringBefore("class "))
                    .map { it.groupValues[1] }.firstOrNull()

                // Walk function declarations; match per-method tag.
                // Look back only to the nearest brace boundary (end of prior
                // function body or opening of the class) so annotations on
                // neighbouring functions are not picked up.
                val funcTags = mutableMapOf<String, String?>()
                val funcPositions = funRegex.findAll(text)
                    .map { it.groupValues[1] to it.range.first }
                    .toList()
                funcPositions.forEach { (name, pos) ->
                    val lastClose = text.lastIndexOf('}', pos - 1)
                    val lastOpen = text.lastIndexOf('{', pos - 1)
                    val regionStart = maxOf(lastClose, lastOpen, 0)
                    val lookBack = text.substring(regionStart, pos)
                    val mTag = featureRegex.find(lookBack)?.groupValues?.get(1)
                    funcTags[name] = mTag ?: classTag
                }

                funcTags.forEach { (fn, tag) ->
                    if (tag == featureTag) {
                        selected.add("$fqn.$fn")
                        taggedCount++
                    }
                }

                if (changedPackages.any { pkg == it || pkg.startsWith("$it.") }) {
                    selected.add(fqn)
                }
            }
        }

        return Result(selected.toList(), taggedCount)
    }
}
