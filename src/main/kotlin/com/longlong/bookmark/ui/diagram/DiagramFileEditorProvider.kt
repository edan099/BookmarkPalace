package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.openapi.diagnostic.Logger

/**
 * 导览图文件编辑器提供者
 * 使用 Draw.io jCEF 编辑器
 * 支持编辑模式（.lldiagram）和查看模式（.lldiagramview）
 */
class DiagramFileEditorProvider : FileEditorProvider, DumbAware {

    private val logger = Logger.getInstance(DiagramFileEditorProvider::class.java)

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.extension == "lldiagram" || file.extension == "lldiagramview"
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val isViewOnly = file.extension == "lldiagramview"
        
        logger.debug("=== BookmarkPalace Diagram Editor ===")
        logger.debug("jCEF supported: ${JBCefApp.isSupported()}")
        logger.debug("View only mode: $isViewOnly")
        logger.debug("Creating DrawioJcefEditor...")
        
        return DrawioJcefEditor(project, file, isViewOnly)
    }

    override fun getEditorTypeId(): String = "longlong-diagram-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
