package com.github.artem.pageobjectplugin.model

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

data class SnapshotBundle(
    val name: String,
    val htmlPath: Path,
    val screenshotPath: Path?,
    val manifestPath: Path?
) {
    companion object {
        fun fromDirectory(dir: Path): SnapshotBundle? {
            if (!dir.exists() || !dir.isDirectory()) return null

            val html = dir.resolve("index.html")
            if (!html.exists()) return null

            val screenshot = dir.resolve("screenshot.png").takeIf { it.exists() }
                ?: dir.resolve("screenshot.webp").takeIf { it.exists() }
            val manifest = dir.resolve("manifest.json").takeIf { it.exists() }

            return SnapshotBundle(
                name = dir.fileName.toString(),
                htmlPath = html,
                screenshotPath = screenshot,
                manifestPath = manifest
            )
        }
    }
}
