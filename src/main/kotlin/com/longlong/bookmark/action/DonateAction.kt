package com.longlong.bookmark.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.ui.dialog.DonateDialog

/**
 * 打赏与联系 Action
 */
class DonateAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        DonateDialog(e.project).show()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
        e.presentation.text = Messages.donate
    }
}
