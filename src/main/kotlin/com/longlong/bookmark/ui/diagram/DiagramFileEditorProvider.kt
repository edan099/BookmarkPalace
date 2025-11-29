package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.openapi.diagnostic.Logger
import com.longlong.bookmark.settings.DiagramEditorSettings

/**
 * 导览图文件编辑器提供者
 * 支持两种编辑器：原生 Swing 和 Draw.io jCEF
 * 支持编辑模式（.lldiagram）和查看模式（.lldiagramview）
 */
class DiagramFileEditorProvider : FileEditorProvider, DumbAware {

    private val logger = Logger.getInstance(DiagramFileEditorProvider::class.java)

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.extension == "lldiagram" || file.extension == "lldiagramview"
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val settings = DiagramEditorSettings.getInstance()
        val isViewOnly = file.extension == "lldiagramview"
        
        // 检查是否启用 Draw.io 且 jCEF 可用
        val jcefSupported = JBCefApp.isSupported()
        val useDrawio = settings.useDrawioEditor && jcefSupported
        
        logger.debug("=== BookmarkPalace Diagram Editor ===")
        logger.debug("useDrawioEditor setting: ${settings.useDrawioEditor}")
        logger.debug("jCEF supported: $jcefSupported")
        logger.debug("View only mode: $isViewOnly")
        logger.debug("Will use: ${if (useDrawio) "DrawioJcefEditor" else "DiagramFileEditor (Swing)"}")
        
        return if (useDrawio) {
            logger.debug("Creating DrawioJcefEditor (viewOnly=$isViewOnly)...")
            DrawioJcefEditor(project, file, isViewOnly)
        } else {
            logger.debug("Creating DiagramFileEditor (Swing)...")
            DiagramFileEditor(project, file)
        }
    }

    override fun getEditorTypeId(): String = "longlong-diagram-editor"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}
