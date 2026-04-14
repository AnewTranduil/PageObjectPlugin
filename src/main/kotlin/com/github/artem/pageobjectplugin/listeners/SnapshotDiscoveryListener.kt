package com.github.artem.pageobjectplugin.listeners

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

        val bundles = scanForBundles(snapshotGroupDir, settings.snapshotSearchDepth)

        val service = SnapshotService.getInstance(project)
        service.updateAvailableSnapshots(bundles)
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

        fun scanForBundles(dir: Path, maxDepth: Int = 3): List<SnapshotBundle> {
            if (!dir.exists() || !dir.isDirectory()) return emptyList()

            val bundles = mutableListOf<SnapshotBundle>()
            val seen = mutableSetOf<Path>()
            scanRecursive(dir, 0, maxDepth, bundles, seen)
            return bundles
        }

        private fun scanRecursive(
            dir: Path,
            depth: Int,
            maxDepth: Int,
            results: MutableList<SnapshotBundle>,
            seen: MutableSet<Path>
        ) {
            if (depth > maxDepth) return
            val realDir = dir.toRealPath()
            if (!seen.add(realDir)) return

            val bundle = SnapshotBundle.fromDirectory(dir)
            if (bundle != null) {
                results.add(bundle)
            }

            if (depth < maxDepth) {
                try {
                    for (child in dir.listDirectoryEntries()) {
                        if (child.isDirectory()) {
                            scanRecursive(child, depth + 1, maxDepth, results, seen)
                        }
                    }
                } catch (_: Exception) {
                    // Ignore permission errors
                }
            }
        }
    }
}
