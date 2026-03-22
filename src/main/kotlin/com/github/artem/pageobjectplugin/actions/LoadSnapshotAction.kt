package com.github.artem.pageobjectplugin.actions

import com.github.artem.pageobjectplugin.model.SnapshotBundle
import com.github.artem.pageobjectplugin.services.SnapshotService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.Messages

class LoadSnapshotAction : AnAction("Load Snapshot Directory...") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        descriptor.title = "Select Snapshot Directory"
        descriptor.description = "Choose a directory containing index.html and layout.json"

        val selectedDir = FileChooser.chooseFile(descriptor, project, null) ?: return
        val path = selectedDir.toNioPath()

        val bundle = SnapshotBundle.fromDirectory(path)
        if (bundle == null) {
            Messages.showErrorDialog(
                project,
                "Selected directory must contain index.html and layout.json",
                "Invalid Snapshot Directory"
            )
            return
        }

        SnapshotService.getInstance(project).loadSnapshot(bundle)
    }
}
