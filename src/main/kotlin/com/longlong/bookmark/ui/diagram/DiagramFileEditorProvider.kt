package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * 导览图文件编辑器提供者
 */
class DiagramFileEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.extension == "lldiagram"
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return DiagramFileEditor(project, file)
    }

    override fun getEditorTypeId(): String = "longlong-diagram-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
