package com.longlong.bookmark.action

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.project.DumbAware
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.service.BookmarkService

/**
 * 快速添加书签 Action（无对话框）
 * 支持编辑器右键菜单和行号槽（gutter）右键菜单
 */
class QuickAddBookmarkAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val bookmarkService = BookmarkService.getInstance(project)

        // 如果是从 gutter 点击，获取点击的行号；否则使用光标位置
        val gutterLine = e.getData(EditorGutterComponentEx.LOGICAL_LINE_AT_CURSOR)
        val line = gutterLine ?: editor.caretModel.logicalPosition.line
        
        // 如果是从 gutter 点击，移动光标到对应行
        if (gutterLine != null && gutterLine >= 0) {
            val offset = editor.document.getLineStartOffset(gutterLine)
            editor.caretModel.moveToOffset(offset)
            val lineEndOffset = editor.document.getLineEndOffset(gutterLine)
            editor.selectionModel.setSelection(offset, lineEndOffset)
        }
        
        val existingBookmark = bookmarkService.getBookmarkAt(editor, line)

        if (existingBookmark != null) {
            // 如果已有书签，则删除（带撤销功能）
            val deletedBookmark = bookmarkService.removeBookmark(existingBookmark.id)
            if (deletedBookmark != null) {
                showDeleteNotificationWithUndo(project, deletedBookmark, bookmarkService)
            }
        } else {
            // 添加新书签
            val bookmark = bookmarkService.quickAddBookmark(editor)
            if (bookmark != null) {
                showNotification(project, "${Messages.bookmarkAdded}: ${bookmark.getDisplayName()}", NotificationType.INFORMATION)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor != null
        e.presentation.text = Messages.quickAddBookmark
    }

    private fun showNotification(project: com.intellij.openapi.project.Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("BookmarkPalace")
            .createNotification(content, type)
            .notify(project)
    }
    
    /**
     * 显示带撤销按钮的删除通知
     */
    private fun showDeleteNotificationWithUndo(
        project: com.intellij.openapi.project.Project,
        deletedBookmark: com.longlong.bookmark.model.Bookmark,
        bookmarkService: BookmarkService
    ) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("BookmarkPalace")
            .createNotification(Messages.bookmarkDeleted, NotificationType.INFORMATION)
        
        notification.addAction(NotificationAction.createSimple(Messages.undo) {
            bookmarkService.restoreBookmark(deletedBookmark)
            notification.expire()
            showNotification(project, Messages.bookmarkRestored, NotificationType.INFORMATION)
        })
        
        notification.notify(project)
    }
}
