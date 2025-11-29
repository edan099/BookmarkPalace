package com.longlong.bookmark.action

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.ui.dialog.AddBookmarkDialog

/**
 * Alt+Enter 菜单中的添加书签意图动作（带对话框）
 */
class AddBookmarkIntention : PsiElementBaseIntentionAction(), IntentionAction {
    
    override fun getFamilyName(): String = "BookmarkPalace"
    
    override fun getText(): String = "📝 ${Messages.addBookmark}..."
    
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return editor != null
    }
    
    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        if (editor == null) return
        
        // 打开添加书签对话框
        val dialog = AddBookmarkDialog(project, editor)
        dialog.show()
    }
    
    override fun startInWriteAction(): Boolean = false
}

/**
 * Alt+Enter 菜单中的快速添加书签意图动作（无对话框）
 */
class QuickAddBookmarkIntention : PsiElementBaseIntentionAction(), IntentionAction {
    
    override fun getFamilyName(): String = "BookmarkPalace"
    
    override fun getText(): String = "⚡ ${Messages.quickAdd}"
    
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        return editor != null
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
