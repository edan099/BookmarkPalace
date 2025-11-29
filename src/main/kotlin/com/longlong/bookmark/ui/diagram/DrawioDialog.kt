package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.longlong.bookmark.model.Diagram
import com.longlong.bookmark.service.DiagramService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Draw.io 导览图对话框
 * 在独立窗口中打开 Draw.io 编辑器
 */
class DrawioDialog(
    private val project: Project,
    private val diagram: Diagram
) : DialogWrapper(project, true) {

    private val virtualFile = LightVirtualFile("${diagram.id}.lldiagram", DiagramFileType, "")
    private var editor: DrawioJcefEditor? = null
    private val contentPanel = JPanel(BorderLayout())

    init {
        title = "📊 ${diagram.name}"
        setSize(1400, 900)
        isModal = false  // 非模态对话框
        init()
    }

    override fun createCenterPanel(): JComponent {
        contentPanel.preferredSize = Dimension(1380, 850)
        return contentPanel
    }
    
    override fun show() {
        super.show()
        // 对话框显示后再创建编辑器
        SwingUtilities.invokeLater {
            editor = DrawioJcefEditor(project, virtualFile)
            contentPanel.add(editor!!.component, BorderLayout.CENTER)
            contentPanel.revalidate()
            contentPanel.repaint()
        }
    }

    override fun createActions() = arrayOf(cancelAction)

    override fun dispose() {
        editor?.dispose()
        super.dispose()
    }
}
