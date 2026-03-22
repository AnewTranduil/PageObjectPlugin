package com.github.artem.pageobjectplugin.listeners

import com.github.artem.pageobjectplugin.model.SnapshotBundle
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.github.artem.pageobjectplugin.settings.PageMirrorSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.Timer
import java.util.TimerTask

class SnapshotWatcher(private val project: Project) : Disposable {

    private var debounceTimer: Timer? = null
    private val debounceDelayMs = 500L

    fun start() {
        project.messageBus.connect(this).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val hasSnapshotChange = events.any { event ->
                        val path = event.path
                        path.contains(".snapshots")
                    }
                    val settings = PageMirrorSettings.getInstance(project)
                    if (hasSnapshotChange && settings.state.autoReloadOnChange) {
                        scheduleReload()
                    }
                }
            }
        )
    }

    private fun scheduleReload() {
        debounceTimer?.cancel()
        debounceTimer = Timer("SnapshotWatcher-debounce", true).apply {
            schedule(object : TimerTask() {
                override fun run() {
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater

                        VirtualFileManager.getInstance().asyncRefresh {
                            val service = SnapshotService.getInstance(project)
                            val currentBundle = service.currentBundle ?: return@asyncRefresh
                            val refreshed = SnapshotBundle.fromDirectory(currentBundle.htmlPath.parent)
                            if (refreshed != null) {
                                service.loadSnapshot(refreshed)
                                NotificationGroupManager.getInstance()
                                    .getNotificationGroup("Page Mirror")
                                    .createNotification(
                                        "Snapshot updated: ${refreshed.name}",
                                        NotificationType.INFORMATION
                                    )
                                    .notify(project)
                            }
                        }
                    }
                }
            }, debounceDelayMs)
        }
    }

    override fun dispose() {
        debounceTimer?.cancel()
        debounceTimer = null
    }
}
