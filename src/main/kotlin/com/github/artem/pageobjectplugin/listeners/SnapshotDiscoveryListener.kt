package com.github.artem.pageobjectplugin.listeners

import com.github.artem.pageobjectplugin.model.BundleLoadResult
import com.github.artem.pageobjectplugin.model.SnapshotBundle
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.github.artem.pageobjectplugin.settings.PageMirrorSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

/**
 * One snapshot bundle directory the scanner found but the plugin
 * refused to load (usually because `manifest.version` is not v2).
 * Surfaced via [ScanResult.rejected] so the Page Mirror tool window
 * can warn the user instead of silently showing nothing.
 */
data class RejectedBundle(val dir: Path, val declaredVersion: Int)

/**
 * Result of a snapshot-directory scan. `loaded` is what the tool
 * window renders; `rejected` drives the outdated-bundle banner.
 */
data class ScanResult(
    val loaded: List<SnapshotBundle>,
    val rejected: List<RejectedBundle>,
) {
    companion object {
        val EMPTY = ScanResult(emptyList(), emptyList())
    }
}

class SnapshotDiscoveryListener(private val project: Project) : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        discoverForFile(file)
    }

    /**
     * Fires when the active editor tab changes (user clicks a different
     * tab, or a test calls `openFile` on an already-open file).
     * `fileOpened` does NOT fire for already-open tabs, so without this
     * handler switching from `dashboard.page.ts` to `login.page.ts`
     * would leave the dashboard snapshot loaded — a product bug.
     */
    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        discoverForFile(file)
    }

    private fun discoverForFile(file: VirtualFile) {
        val settingsInstance = PageMirrorSettings.getInstance(project)
        if (!settingsInstance.isSupportedFile(file.name)) return

        val settings = settingsInstance.state
        val pageName = extractPageName(file.name, settings.pageObjectPattern) ?: return

        val projectRoot = project.basePath?.let { Path.of(it) } ?: return
        val snapshotGroupDir = projectRoot.resolve(settings.snapshotsRoot).resolve(pageName)

        val scan = scanBundles(snapshotGroupDir, settings.snapshotSearchDepth)

        val service = SnapshotService.getInstance(project)
        service.updateAvailableSnapshots(scan)
    }

    companion object {
        fun extractPageName(filename: String, pattern: String): String? {
            return try {
                val regex = Regex(pattern)
                val match = regex.matchEntire(filename) ?: return null
                match.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Canonical scan — returns both loaded and rejected bundles.
         * The Page Mirror banner reads `rejected` to warn the user
         * their v1 snapshots are on disk but un-loadable.
         */
        fun scanBundles(dir: Path, maxDepth: Int = 3): ScanResult {
            if (!dir.exists() || !dir.isDirectory()) return ScanResult.EMPTY

            val loaded = mutableListOf<SnapshotBundle>()
            val rejected = mutableListOf<RejectedBundle>()
            val seen = mutableSetOf<Path>()
            scanRecursive(dir, 0, maxDepth, loaded, rejected, seen)
            return ScanResult(loaded, rejected)
        }

        /**
         * Backwards-compatible convenience — returns only the loaded
         * bundles, preserving the existing public signature. Prefer
         * [scanBundles] for new call sites that care about rejected
         * bundles.
         */
        fun scanForBundles(dir: Path, maxDepth: Int = 3): List<SnapshotBundle> =
            scanBundles(dir, maxDepth).loaded

        private fun scanRecursive(
            dir: Path,
            depth: Int,
            maxDepth: Int,
            loaded: MutableList<SnapshotBundle>,
            rejected: MutableList<RejectedBundle>,
            seen: MutableSet<Path>,
        ) {
            if (depth > maxDepth) return
            val realDir = dir.toRealPath()
            if (!seen.add(realDir)) return

            when (val result = SnapshotBundle.load(dir)) {
                is BundleLoadResult.Loaded -> loaded.add(result.bundle)
                is BundleLoadResult.UnsupportedVersion ->
                    rejected.add(RejectedBundle(result.dir, result.declared))
                BundleLoadResult.Empty -> { /* not a bundle dir */ }
            }

            if (depth < maxDepth) {
                try {
                    for (child in dir.listDirectoryEntries()) {
                        if (child.isDirectory()) {
                            scanRecursive(child, depth + 1, maxDepth, loaded, rejected, seen)
                        }
                    }
                } catch (_: Exception) {
                    // Ignore permission errors
                }
            }
        }
    }
}
