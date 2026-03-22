package com.github.artem.pageobjectplugin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(name = "PageMirrorSettings", storages = [Storage("pageMirror.xml")])
class PageMirrorSettings : PersistentStateComponent<PageMirrorSettings.State> {

    data class State(
        var snapshotSearchDepth: Int = 3,
        var autoReloadOnChange: Boolean = true,
        var highlightColor: String = "#3B82F6",
        var codeGenStyle: String = "Property"
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(project: Project): PageMirrorSettings = project.service()
    }
}
