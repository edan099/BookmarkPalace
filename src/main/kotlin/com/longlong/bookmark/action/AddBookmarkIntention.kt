package com.longlong.bookmark.action

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.service.BookmarkService

/**
 * Alt+Enter 菜单中的添加书签意图动作
 */
class AddBookmarkIntention : PsiElementBaseIntentionAction(), IntentionAction {
    
    override fun getFamilyName(): String = "BookmarkPalace"
    
    override fun getText(): String = Messages.addBookmark
    
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        // 只要在编辑器中就可用
        return editor != null
    }
    
    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        if (editor == null) return
        
        val bookmarkService = BookmarkService.getInstance(project)
        // 快速添加书签
        bookmarkService.addBookmark(editor)
    }
    
    override fun startInWriteAction(): Boolean = false
}
