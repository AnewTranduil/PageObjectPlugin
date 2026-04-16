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

/**
 * Result of attempting to load a snapshot bundle from a directory.
 *
 * The loader splits three outcomes rather than returning a nullable
 * bundle so the discovery scanner can distinguish "dir isn't a bundle"
 * from "dir IS a bundle but the plugin can't use it because of a
 * version mismatch". The banner in the Page Mirror tool window is
 * driven by [UnsupportedVersion] observations.
 */
sealed class BundleLoadResult {
    data class Loaded(val bundle: SnapshotBundle) : BundleLoadResult()
    /**
     * The directory contains a valid-looking bundle but declares an
     * unsupported `manifest.version`. `declared` is the integer the
     * manifest carried (preserved so the banner can surface the exact
     * value the user hit).
     */
    data class UnsupportedVersion(val dir: Path, val declared: Int) : BundleLoadResult()
    /**
     * The directory is missing or doesn't contain a bundle (no
     * index.html). Nothing to surface.
     */
    object Empty : BundleLoadResult()
}

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
        /**
         * Canonical entry point: classify a directory into a
         * [BundleLoadResult]. Use [fromDirectory] for the legacy
         * nullable-bundle convenience.
         */
        fun load(dir: Path): BundleLoadResult {
            if (!dir.exists() || !dir.isDirectory()) return BundleLoadResult.Empty

            val html = dir.resolve("index.html")
            if (!html.exists()) return BundleLoadResult.Empty

            val manifest = dir.resolve("manifest.json").takeIf { it.exists() }
            if (manifest != null) {
                val declared = readDeclaredVersion(manifest)
                if (declared != null && declared != SUPPORTED_BUNDLE_VERSION) {
                    LOG.warn(
                        "Refusing snapshot bundle at $dir: manifest.version=$declared, " +
                            "plugin supports version=$SUPPORTED_BUNDLE_VERSION. " +
                            "Re-run your snapshot saver to regenerate bundles in the v2 layout."
                    )
                    return BundleLoadResult.UnsupportedVersion(dir, declared)
                }
            }

            val resourcesDir = dir.resolve("resources").takeIf { it.exists() && it.isDirectory() }
            val screenshot = resourcesDir?.let { findScreenshot(it) }

            return BundleLoadResult.Loaded(
                SnapshotBundle(
                    name = dir.fileName.toString(),
                    dir = dir,
                    htmlPath = html,
                    resourcesDir = resourcesDir,
                    screenshotPath = screenshot,
                    manifestPath = manifest,
                )
            )
        }

        /**
         * Backwards-compatible convenience — returns the loaded bundle
         * or null. Rejections and empty dirs both collapse to null, so
         * prefer [load] for new code.
         */
        fun fromDirectory(dir: Path): SnapshotBundle? =
            (load(dir) as? BundleLoadResult.Loaded)?.bundle

        private fun findScreenshot(resourcesDir: Path): Path? {
            val png = resourcesDir.resolve("screenshot.png").takeIf { it.exists() }
            if (png != null) return png
            val webp = resourcesDir.resolve("screenshot.webp").takeIf { it.exists() }
            if (webp != null) return webp
            return null
        }

        /**
         * Parse the manifest for its `version` field. Uses a tight
         * regex on the manifest JSON text to avoid pulling in a JSON
         * library for a single field. Manifest files are small,
         * well-formed, and produced by our own savers, so false-positive
         * risk is negligible.
         *
         * Returns the declared integer version, or `null` when the
         * field is missing / unparseable / the file can't be read. A
         * `null` result is treated permissively by [load] — absent
         * version means "unknown, render anyway".
         */
        private val VERSION_PATTERN = Regex("\"version\"\\s*:\\s*(\\d+)")

        private fun readDeclaredVersion(manifest: Path): Int? {
            val text = try {
                manifest.readText()
            } catch (e: Exception) {
                LOG.warn("Bundle manifest at $manifest could not be read; treating as versionless", e)
                return null
            }
            val match = VERSION_PATTERN.find(text) ?: return null
            return match.groupValues[1].toIntOrNull()
        }
    }
}
