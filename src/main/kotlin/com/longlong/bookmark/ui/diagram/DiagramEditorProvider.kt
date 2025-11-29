package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.components.JBList
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.model.Bookmark
import com.longlong.bookmark.model.Diagram
import com.longlong.bookmark.model.DiagramType
import com.longlong.bookmark.service.DiagramService
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

/**
 * 导览图编辑器提供者
 */
object DiagramEditorProvider {

    /**
     * 打开导览图选择器
     */
    fun openDiagramSelector(project: Project) {
        val dialog = DiagramSelectorDialog(project)
        dialog.show()
    }

    /**
     * 打开导览图编辑器（对话框模式）
     * 使用 Draw.io 编辑器
     */
    fun openDiagramEditor(project: Project, diagram: Diagram) {
        val dialog = DrawioDialog(project, diagram)
        dialog.show()
    }

    /**
     * 在编辑器Tab中打开导览图（支持分栏）
     * @param viewOnly 是否为只读查看模式
     */
    fun openDiagramInEditor(project: Project, diagram: Diagram, viewOnly: Boolean = false) {
        // 使用不同的扩展名区分编辑模式和查看模式
        val ext = if (viewOnly) "lldiagramview" else "lldiagram"
        val virtualFile = LightVirtualFile("${diagram.id}.$ext", DiagramFileType, "")
        FileEditorManager.getInstance(project).openFile(virtualFile, true)
    }

    /**
     * 添加书签到导览图
     */
    fun addBookmarkToDiagram(project: Project, bookmark: Bookmark) {
        val diagramService = DiagramService.getInstance(project)
        val diagrams = diagramService.getAllDiagrams()

        if (diagrams.isEmpty()) {
            val newDiagram = diagramService.createDiagram("主流程", DiagramType.MAIN_FLOW)
            diagramService.addBookmarkToDiagram(newDiagram.id, bookmark)
            return
        }

        if (diagrams.size == 1) {
            diagramService.addBookmarkToDiagram(diagrams.first().id, bookmark)
            return
        }

        // 显示选择列表
        val options = diagrams.map { it.name }.toTypedArray()
        val selected = JOptionPane.showInputDialog(
            null,
            "选择导览图",
            "添加到导览图",
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        )

        if (selected != null) {
            val diagram = diagrams.find { it.name == selected }
            if (diagram != null) {
                diagramService.addBookmarkToDiagram(diagram.id, bookmark)
            }
        }
    }

    /**
     * 创建新导览图
     */
    fun createNewDiagram(project: Project): Diagram? {
        val dialog = CreateDiagramDialog(project)
        if (dialog.showAndGet()) {
            val diagramService = DiagramService.getInstance(project)
            return diagramService.createDiagram(
                name = dialog.getDiagramName(),
                type = dialog.getDiagramType(),
                description = dialog.getDescription()
            )
        }
        return null
    }
}

/**
 * 导览图选择对话框
 */
class DiagramSelectorDialog(private val project: Project) : DialogWrapper(project) {
    
    private val diagramService = DiagramService.getInstance(project)
    private val listModel = DefaultListModel<Diagram>()
    private val diagramList = JBList(listModel)

    init {
        title = Messages.diagrams
        setSize(500, 400)
        refreshList()
        init()
    }

    private fun refreshList() {
        listModel.clear()
        diagramService.getAllDiagrams().forEach { listModel.addElement(it) }
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(8, 8))
        
        diagramList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, 
                isSelected: Boolean, cellHasFocus: Boolean): java.awt.Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                (value as? Diagram)?.let {
                    text = "📊 ${it.name}"
                }
                return this
            }
        }
        
        diagramList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedDiagram(inEditor = true, viewOnly = false)  // 双击默认在编辑器中打开（编辑模式）
                }
            }
        })
        
        mainPanel.add(JScrollPane(diagramList), BorderLayout.CENTER)
        
        // 按钮面板 - 第一行：创建和打开操作
        val buttonPanel = JPanel()
        buttonPanel.layout = BoxLayout(buttonPanel, BoxLayout.Y_AXIS)
        
        val row1 = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        
        row1.add(JButton(Messages.newDiagram).apply {
            addActionListener {
                val dialog = CreateDiagramDialog(project)
                if (dialog.showAndGet()) {
                    diagramService.createDiagram(dialog.getDiagramName(), dialog.getDiagramType(), dialog.getDescription())
                    refreshList()
                }
            }
        })
        
        row1.add(JButton(Messages.openInEditor).apply {
            toolTipText = Messages.openInEditorTip
            addActionListener { openSelectedDiagram(inEditor = true, viewOnly = false) }
        })
        
        row1.add(JButton(Messages.viewOnly).apply {
            toolTipText = Messages.viewOnlyTip
            addActionListener { openSelectedDiagram(inEditor = true, viewOnly = true) }
        })
        
        row1.add(JButton(Messages.openInWindow).apply {
            toolTipText = Messages.openInWindowTip
            addActionListener { openSelectedDiagram(inEditor = false, viewOnly = false) }
        })
        
        buttonPanel.add(row1)
        
        // 第二行：编辑操作
        val row2 = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        
        row2.add(JButton(Messages.edit).apply {
            toolTipText = "重命名选中的导览图"
            addActionListener {
                diagramList.selectedValue?.let { diagram ->
                    val newName = JOptionPane.showInputDialog(
                        mainPanel,
                        Messages.renameDiagram,
                        diagram.name
                    )
                    if (!newName.isNullOrBlank() && newName != diagram.name) {
                        diagram.name = newName
                        diagramService.updateDiagram(diagram)
                        refreshList()
                    }
                }
            }
        })
        
        row2.add(JButton(Messages.delete).apply {
            addActionListener {
                diagramList.selectedValue?.let {
                    if (JOptionPane.showConfirmDialog(mainPanel, 
                        "${Messages.deleteDiagram}: ${it.name}?", Messages.delete, 
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        diagramService.removeDiagram(it.id)
                        refreshList()
                    }
                }
            }
        })
        
        buttonPanel.add(row2)
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        return mainPanel
    }

    private fun openSelectedDiagram(inEditor: Boolean = false, viewOnly: Boolean = false) {
        diagramList.selectedValue?.let {
            close(OK_EXIT_CODE)
            if (inEditor) {
                DiagramEditorProvider.openDiagramInEditor(project, it, viewOnly)
            } else {
                DiagramEditorProvider.openDiagramEditor(project, it)
            }
        }
    }

    override fun createActions(): Array<Action> = arrayOf(cancelAction)
}

/**
 * 创建导览图对话框
 */
class CreateDiagramDialog(project: Project) : DialogWrapper(project) {

    private val nameField = JTextField(20)
    private val typeCombo = JComboBox(DiagramType.values())
    private val descField = JTextField(30)

    init {
        title = Messages.newDiagram
        typeCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int,
                isSelected: Boolean, cellHasFocus: Boolean): java.awt.Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                (value as? DiagramType)?.let { text = it.displayName }
                return this
            }
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(JPanel(BorderLayout()).apply {
            add(JLabel("${Messages.name}: "), BorderLayout.WEST)
            add(nameField, BorderLayout.CENTER)
        })
        panel.add(Box.createVerticalStrut(10))
        panel.add(JPanel(BorderLayout()).apply {
            add(JLabel("${Messages.type}: "), BorderLayout.WEST)
            add(typeCombo, BorderLayout.CENTER)
        })
        panel.add(Box.createVerticalStrut(10))
        panel.add(JPanel(BorderLayout()).apply {
            add(JLabel("${Messages.comment}: "), BorderLayout.WEST)
            add(descField, BorderLayout.CENTER)
        })

        panel.preferredSize = Dimension(400, 120)
        return panel
    }

    fun getDiagramName(): String = nameField.text.trim().ifEmpty { Messages.newDiagram }
    fun getDiagramType(): DiagramType = typeCombo.selectedItem as DiagramType
    fun getDescription(): String = descField.text.trim()
}
