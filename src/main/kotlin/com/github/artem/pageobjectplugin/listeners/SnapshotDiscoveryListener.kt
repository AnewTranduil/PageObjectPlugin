package com.github.artem.pageobjectplugin.listeners

import com.github.artem.pageobjectplugin.model.SnapshotBundle
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

class SnapshotDiscoveryListener(private val project: Project) : FileEditorManagerListener {

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        if (!isTypeScriptFile(file)) return

        val filePath = file.toNioPath()
        val snapshotBundles = discoverSnapshots(filePath)

        val service = SnapshotService.getInstance(project)
        service.updateAvailableSnapshots(snapshotBundles)
    }

    private fun isTypeScriptFile(file: VirtualFile): Boolean {
        val name = file.name
        return name.endsWith(".ts") || name.endsWith(".tsx")
    }

    companion object {
        fun discoverSnapshots(filePath: Path): List<SnapshotBundle> {
            val searchDirs = listOfNotNull(
                filePath.parent,
                filePath.parent?.parent
            )

            val bundles = mutableListOf<SnapshotBundle>()
            val seen = mutableSetOf<Path>()

            for (dir in searchDirs) {
                val snapshotsDir = dir.resolve(".snapshots")
                if (!snapshotsDir.exists() || !snapshotsDir.isDirectory()) continue

                scanForBundles(snapshotsDir, 0, 2, bundles, seen)
            }

            return bundles
        }

        private fun scanForBundles(
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
                            scanForBundles(child, depth + 1, maxDepth, results, seen)
                        }
                    }
                } catch (_: Exception) {
                    // Ignore permission errors
                }
            }
        }
    }
}
