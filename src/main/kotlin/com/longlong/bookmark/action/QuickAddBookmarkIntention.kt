package com.longlong.bookmark.action

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.service.BookmarkService

/**
 * Alt+Enter 菜单中的快速添加书签意图动作（无对话框）
 */
class QuickAddBookmarkIntention : PsiElementBaseIntentionAction(), IntentionAction {
    
    override fun getFamilyName(): String = "BookmarkPalace"
    
    override fun getText(): String = "⚡ ${Messages.quickAdd}"
    
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        // 在任何可编辑的文件中都可用
        return editor != null && element.isValid
    }
    
    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        if (editor == null) return
        
        val bookmarkService = BookmarkService.getInstance(project)
        val line = editor.caretModel.logicalPosition.line
        val existingBookmark = bookmarkService.getBookmarkAt(editor, line)
        
        if (existingBookmark != null) {
            // 如果已有书签，则删除
            bookmarkService.removeBookmark(existingBookmark.id)
        } else {
            // 添加新书签
            bookmarkService.quickAddBookmark(editor)
        }
    }
    
    override fun startInWriteAction(): Boolean = false
}
