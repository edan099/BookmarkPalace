package com.longlong.bookmark.service

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.longlong.bookmark.i18n.Messages as I18nMessages
import com.longlong.bookmark.ui.diagram.DiagramFileEditor
import com.longlong.bookmark.ui.diagram.DrawioJcefEditor

/**
 * 导览图编辑器管理监听器
 * 当用户切换离开导览图时检查是否需要保存
 */
class DiagramEditorManagerListener(private val project: Project) : FileEditorManagerListener {
    
    /**
     * 当用户切换文件时，检查之前的文件是否需要保存
     */
    override fun selectionChanged(event: FileEditorManagerEvent) {
        val oldFile = event.oldFile ?: return
        
        // 检查是否是导览图文件
        if (!oldFile.name.endsWith(".lldiagram") && !oldFile.name.endsWith(".lldiagramview")) {
            return
        }
        
        // 检查老文件的编辑器是否需要保存
        checkAndPromptSave(oldFile)
    }
    
    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        // fileClosed 在关闭后调用
        // 不过我们可以在这里再次检查，确保没有漏掉
    }
    
    private fun checkAndPromptSave(file: VirtualFile) {
        val source = FileEditorManager.getInstance(project)
        val editors = source.getEditors(file)
        
        for (editor in editors) {
            val needsSave = when (editor) {
                is DiagramFileEditor -> editor.isModified
                is DrawioJcefEditor -> editor.isModified
                else -> false
            }
            
            if (needsSave) {
                // 弹出保存对话框
                val title = if (I18nMessages.isEnglish()) "Save Diagram?" else "保存导览图？"
                val message = if (I18nMessages.isEnglish()) {
                    "The diagram '${file.nameWithoutExtension}' has been modified.\nDo you want to save the changes?"
                } else {
                    "导览图 '${file.nameWithoutExtension}' 已修改。\n是否保存更改？"
                }
                val yes = if (I18nMessages.isEnglish()) "Save" else "保存"
                val no = if (I18nMessages.isEnglish()) "Don't Save" else "不保存"
                
                val result = Messages.showYesNoDialog(
                    project,
                    message,
                    title,
                    yes,
                    no,
                    null
                )
                
                if (result == Messages.YES) {
                    when (editor) {
                        is DiagramFileEditor -> editor.save()
                        is DrawioJcefEditor -> editor.save()
                    }
                }
                
                // 只处理第一个需要保存的编辑器
                break
            }
        }
    }
}
