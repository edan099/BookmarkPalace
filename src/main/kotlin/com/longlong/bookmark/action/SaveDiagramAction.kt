package com.longlong.bookmark.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.ui.diagram.DiagramFileEditor
import com.longlong.bookmark.ui.diagram.DrawioJcefEditor

/**
 * 保存导览图 Action - 响应 Command+S
 */
class SaveDiagramAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        
        // 检查是否是导览图文件
        if (!file.name.endsWith(".lldiagram") && !file.name.endsWith(".lldiagramview")) {
            return
        }
        
        // 获取当前编辑器并保存
        val editors = FileEditorManager.getInstance(project).getEditors(file)
        editors.forEach { editor ->
            when (editor) {
                is DiagramFileEditor -> editor.save()
                is DrawioJcefEditor -> editor.save()
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val isDiagramFile = file?.name?.let { 
            it.endsWith(".lldiagram") || it.endsWith(".lldiagramview") 
        } ?: false
        
        e.presentation.isEnabledAndVisible = isDiagramFile
        e.presentation.text = Messages.save
    }
}
