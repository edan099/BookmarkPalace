package com.longlong.bookmark.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.ui.dialog.AddBookmarkDialog

/**
 * 添加书签 Action
 */
class AddBookmarkAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        // 显示添加书签对话框
        val dialog = AddBookmarkDialog(project, editor)
        if (dialog.showAndGet()) {
            val bookmarkService = BookmarkService.getInstance(project)
            bookmarkService.addBookmark(
                editor = editor,
                alias = dialog.getAlias(),
                color = dialog.getColor(),
                tags = dialog.getTags(),
                comment = dialog.getComment()
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor != null
    }
}
