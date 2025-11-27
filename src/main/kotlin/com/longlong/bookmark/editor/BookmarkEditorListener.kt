package com.longlong.bookmark.editor

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.longlong.bookmark.service.BookmarkService

/**
 * 编辑器工厂监听器 - 在编辑器创建/关闭时管理书签
 */
class BookmarkEditorListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return
        val document = editor.document
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return

        // 编辑器打开时，刷新该文件的书签状态
        val bookmarkService = BookmarkService.getInstance(project)
        bookmarkService.refreshBookmarks()
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        val project = editor.project ?: return

        // 编辑器关闭时，保存书签状态
        val bookmarkService = BookmarkService.getInstance(project)
        bookmarkService.refreshBookmarks()
    }
}
