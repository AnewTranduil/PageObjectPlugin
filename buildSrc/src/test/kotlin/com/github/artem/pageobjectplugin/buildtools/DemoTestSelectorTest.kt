package com.github.artem.pageobjectplugin.buildtools

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class DemoTestSelectorTest {

    private fun seed(root: Path) {
        val uiTestDir = root.resolve("src/uiTest/kotlin/com/example/foo")
        Files.createDirectories(uiTestDir)
        uiTestDir.resolve("AlphaUiTest.kt").writeText(
            """
            package com.example.foo
            import com.github.artem.pageobjectplugin.ui.annotations.Feature
            class AlphaUiTest {
                @Feature("smoke") fun happy() {}
                @Feature("smoke") fun negative() {}
                fun untagged() {}
            }
            """.trimIndent(),
        )
        val uiTestDir2 = root.resolve("src/uiTest/kotlin/com/example/bar")
        Files.createDirectories(uiTestDir2)
        uiTestDir2.resolve("BetaUiTest.kt").writeText(
            """
            package com.example.bar
            class BetaUiTest { fun logsIn() {} }
            """.trimIndent(),
        )
        Files.createDirectories(root.resolve("src/main/kotlin/com/example/bar"))
    }

    @Test
    fun `tag-matched tests are selected and counted`(@TempDir root: Path) {
        seed(root)
        val result = DemoTestSelector.select(
            projectDir = root,
            featureTag = "smoke",
            changedFiles = emptyList(),
        )
        assertEquals(2, result.taggedCount)
        assertTrue(result.selected.any { it.endsWith("AlphaUiTest.happy") })
        assertTrue(result.selected.any { it.endsWith("AlphaUiTest.negative") })
    }

    @Test
    fun `changed files select tests by package heuristic`(@TempDir root: Path) {
        seed(root)
        val result = DemoTestSelector.select(
            projectDir = root,
            featureTag = "smoke",
            changedFiles = listOf("src/main/kotlin/com/example/bar/Thing.kt"),
        )
        assertTrue(result.selected.any { it.contains("BetaUiTest") })
        assertTrue(result.selected.any { it.contains("AlphaUiTest") })
    }

    @Test
    fun `insufficient tagged scenarios surfaces in count`(@TempDir root: Path) {
        val ui = root.resolve("src/uiTest/kotlin/com/example/solo")
        Files.createDirectories(ui)
        ui.resolve("SoloUiTest.kt").writeText(
            """
            package com.example.solo
            import com.github.artem.pageobjectplugin.ui.annotations.Feature
            class SoloUiTest { @Feature("lone") fun only() {} }
            """.trimIndent(),
        )
        val result = DemoTestSelector.select(
            projectDir = root,
            featureTag = "lone",
            changedFiles = emptyList(),
        )
        assertEquals(1, result.taggedCount)
    }
}
