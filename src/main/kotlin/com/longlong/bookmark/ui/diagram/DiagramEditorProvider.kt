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
     */
    fun openDiagramEditor(project: Project, diagram: Diagram) {
        val dialog = DiagramEditorDialog(project, diagram)
        dialog.show()
    }

    /**
     * 在编辑器Tab中打开导览图（支持分栏）
     */
    fun openDiagramInEditor(project: Project, diagram: Diagram) {
        val virtualFile = LightVirtualFile("${diagram.id}.lldiagram", DiagramFileType, "")
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
                    text = "📊 ${it.name} (${it.type.displayName}) - ${it.nodes.size} ${Messages.node}"
                }
                return this
            }
        }
        
        diagramList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedDiagram()
                }
            }
        })
        
        mainPanel.add(JScrollPane(diagramList), BorderLayout.CENTER)
        
        // 按钮面板
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        
        buttonPanel.add(JButton(Messages.newDiagram).apply {
            addActionListener {
                val dialog = CreateDiagramDialog(project)
                if (dialog.showAndGet()) {
                    diagramService.createDiagram(dialog.getDiagramName(), dialog.getDiagramType(), dialog.getDescription())
                    refreshList()
                }
            }
        })
        
        buttonPanel.add(JButton(Messages.openInWindow).apply {
            addActionListener { openSelectedDiagram(false) }
        })
        
        buttonPanel.add(JButton(Messages.openInEditor).apply {
            addActionListener { openSelectedDiagram(true) }
        })
        
        buttonPanel.add(JButton(Messages.delete).apply {
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
        
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        return mainPanel
    }

    private fun openSelectedDiagram(inEditor: Boolean = false) {
        diagramList.selectedValue?.let {
            close(OK_EXIT_CODE)
            if (inEditor) {
                DiagramEditorProvider.openDiagramInEditor(project, it)
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
