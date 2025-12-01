package com.longlong.bookmark.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.longlong.bookmark.i18n.Messages

/**
 * 书签工具窗口工厂
 * 包含两个 Tab：书签列表和导览图（类似 Maven 工具窗口）
 */
class BookmarkToolWindowFactory : ToolWindowFactory, DumbAware {
    
    companion object {
        // 保存 Content 引用以便更新标题
        private var bookmarkContent: Content? = null
        private var diagramContent: Content? = null
        
        /**
         * 更新 Tab 标题（语言切换时调用）
         */
        fun updateTabTitles() {
            bookmarkContent?.displayName = Messages.tabBookmarks
            diagramContent?.displayName = Messages.tabDiagrams
        }
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        
        // Tab 1: 书签列表
        val bookmarkPanel = BookmarkToolWindowPanel(project)
        bookmarkContent = contentFactory.createContent(
            bookmarkPanel, 
            Messages.tabBookmarks, 
            false
        ).apply {
            isCloseable = false
        }
        toolWindow.contentManager.addContent(bookmarkContent!!)
        
        // Tab 2: 导览图
        val diagramPanel = DiagramToolWindowPanel(project)
        diagramContent = contentFactory.createContent(
            diagramPanel, 
            Messages.tabDiagrams, 
            false
        ).apply {
            isCloseable = false
        }
        toolWindow.contentManager.addContent(diagramContent!!)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
