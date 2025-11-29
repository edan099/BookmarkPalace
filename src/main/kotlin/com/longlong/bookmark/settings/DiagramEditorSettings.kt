package com.longlong.bookmark.settings

import com.intellij.openapi.components.*

/**
 * 导览图编辑器设置
 */
@Service
@State(
    name = "LongLongBookmarkDiagramEditorSettings",
    storages = [Storage("longlong-bookmark-diagram-editor.xml")]
)
class DiagramEditorSettings : PersistentStateComponent<DiagramEditorSettings.State> {

    private var myState = State()

    data class State(
        var useDrawioEditor: Boolean = false, // 默认使用原生 Swing 编辑器
        var drawioUrl: String = "https://embed.diagrams.net/" // Draw.io Embed URL
    )

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var useDrawioEditor: Boolean
        get() = myState.useDrawioEditor
        set(value) {
            myState.useDrawioEditor = value
        }

    var drawioUrl: String
        get() = myState.drawioUrl
        set(value) {
            myState.drawioUrl = value
        }

    companion object {
        fun getInstance(): DiagramEditorSettings {
            return service()
        }
    }
}
