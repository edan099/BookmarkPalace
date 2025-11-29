package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.longlong.bookmark.service.DiagramService

/**
 * 自定义导览图文件的标签页标题
 * 将 UUID 文件名显示为图表名称
 * 支持编辑模式（.lldiagram）和查看模式（.lldiagramview）
 */
class DiagramTabTitleProvider : EditorTabTitleProvider {
    
    override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
        // 处理 .lldiagram 和 .lldiagramview 文件
        val isViewMode = file.extension == "lldiagramview"
        val isEditMode = file.extension == "lldiagram"
        
        if (!isViewMode && !isEditMode) {
            return null
        }
        
        // 从文件名提取 diagram ID
        val diagramId = file.nameWithoutExtension
        
        // 获取 diagram 名称
        val diagramService = DiagramService.getInstance(project)
        val diagram = diagramService.getDiagram(diagramId)
        
        val icon = if (isViewMode) "📖" else "✏️"
        val modeSuffix = if (isViewMode) " (查看)" else ""
        
        return if (diagram != null) {
            "$icon ${diagram.name}$modeSuffix"
        } else {
            "$icon 导览图$modeSuffix"
        }
    }
}
