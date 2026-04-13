package com.github.artem.pageobjectplugin.model

import com.intellij.openapi.diagnostic.logger
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.readText

private val LOG = logger<SnapshotBundle>()

/**
 * Supported snapshot bundle format version. The plugin refuses to load
 * any bundle that declares a different version in its manifest.json,
 * with a clear error in the log. See `docs/snapshot-bundle-spec.md`.
 */
const val SUPPORTED_BUNDLE_VERSION = 2

data class SnapshotBundle(
    val name: String,
    /** Absolute path to the snapshot directory. */
    val dir: Path,
    val htmlPath: Path,
    /** Absolute path to the bundle's `resources/` subdirectory, or null if absent. */
    val resourcesDir: Path?,
    /** Screenshot under `resources/`, or null if none was produced. */
    val screenshotPath: Path?,
    val manifestPath: Path?,
) {
    companion object {
        fun fromDirectory(dir: Path): SnapshotBundle? {
            if (!dir.exists() || !dir.isDirectory()) return null

            val html = dir.resolve("index.html")
            if (!html.exists()) return null

            val manifest = dir.resolve("manifest.json").takeIf { it.exists() }
            if (manifest != null && !isSupportedVersion(manifest, dir)) {
                return null
            }

            val resourcesDir = dir.resolve("resources").takeIf { it.exists() && it.isDirectory() }
            val screenshot = resourcesDir?.let { findScreenshot(it) }

            return SnapshotBundle(
                name = dir.fileName.toString(),
                dir = dir,
                htmlPath = html,
                resourcesDir = resourcesDir,
                screenshotPath = screenshot,
                manifestPath = manifest,
            )
        }

        private fun findScreenshot(resourcesDir: Path): Path? {
            val png = resourcesDir.resolve("screenshot.png").takeIf { it.exists() }
            if (png != null) return png
            val webp = resourcesDir.resolve("screenshot.webp").takeIf { it.exists() }
            if (webp != null) return webp
            return null
        }

        /**
         * Parse the manifest for its `version` field and verify it
         * matches [SUPPORTED_BUNDLE_VERSION]. Unparseable or missing
         * version is treated permissively — the plugin still has enough
         * info from index.html alone to render. Only a mismatched
         * integer version is fatal.
         *
         * Uses a tight regex on the manifest JSON text to avoid pulling
         * in a JSON library for a single field. Manifest files are
         * small, well-formed, and produced by our own savers, so
         * false-positive risk is negligible.
         */
        private val VERSION_PATTERN = Regex("\"version\"\\s*:\\s*(\\d+)")

        private fun isSupportedVersion(manifest: Path, dir: Path): Boolean {
            val text = try {
                manifest.readText()
            } catch (e: Exception) {
                LOG.warn("Bundle manifest at $manifest could not be read; treating as versionless", e)
                return true
            }
            val match = VERSION_PATTERN.find(text)
            if (match == null) {
                // Absent "version" field — treat as versionless, acceptable.
                return true
            }
            val declared = match.groupValues[1].toIntOrNull() ?: return true
            if (declared == SUPPORTED_BUNDLE_VERSION) {
                return true
            }
            LOG.warn(
                "Refusing snapshot bundle at $dir: manifest.version=$declared, " +
                    "plugin supports version=$SUPPORTED_BUNDLE_VERSION. " +
                    "Re-run your snapshot saver to regenerate bundles in the v2 layout."
            )
            return false
        }
    }
}
