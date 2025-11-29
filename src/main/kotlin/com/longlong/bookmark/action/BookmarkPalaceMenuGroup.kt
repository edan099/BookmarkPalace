package com.longlong.bookmark.action

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.longlong.bookmark.i18n.Messages

/**
 * BookmarkPalace 主菜单组 - 支持动态语言切换
 */
class BookmarkPalaceMenuGroup : DefaultActionGroup() {
    
    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.text = Messages.pluginName
        e.presentation.description = if (Messages.isEnglish()) {
            "BookmarkPalace - Code bookmark management tool"
        } else {
            "BookmarkPalace - 代码书签管理工具"
        }
    }
}
