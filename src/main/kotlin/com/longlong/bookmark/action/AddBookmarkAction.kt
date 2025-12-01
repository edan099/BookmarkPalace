package com.longlong.bookmark.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.project.DumbAware
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.ui.dialog.AddBookmarkDialog

/**
 * 添加书签 Action
 * 支持编辑器右键菜单和行号槽（gutter）右键菜单
 */
class AddBookmarkAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        // 如果是从 gutter 点击，先移动光标到对应行
        moveCaretToGutterLine(e, editor)

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
        e.presentation.text = Messages.addBookmark
    }
    
    companion object {
        /**
         * 如果是从 gutter 点击，移动光标到点击的行
         */
        fun moveCaretToGutterLine(e: AnActionEvent, editor: Editor) {
            // 尝试获取 gutter 点击的行号
            val gutterLine = e.getData(EditorGutterComponentEx.LOGICAL_LINE_AT_CURSOR)
            if (gutterLine != null && gutterLine >= 0) {
                // 移动光标到该行开头
                val offset = editor.document.getLineStartOffset(gutterLine)
                editor.caretModel.moveToOffset(offset)
                // 选中整行
                val lineEndOffset = editor.document.getLineEndOffset(gutterLine)
                editor.selectionModel.setSelection(offset, lineEndOffset)
            }
        }
    }
}
