package com.longlong.bookmark.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.ui.diagram.DiagramEditorProvider

/**
 * 打开导览图 Action
 */
class OpenDiagramAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        DiagramEditorProvider.openDiagramSelector(project)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
        e.presentation.text = Messages.openDiagram
    }
}
