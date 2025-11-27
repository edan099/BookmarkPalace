package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBList
import com.longlong.bookmark.model.Bookmark
import com.longlong.bookmark.model.Diagram
import com.longlong.bookmark.model.DiagramType
import com.longlong.bookmark.service.DiagramService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.*

/**
 * 导览图编辑器提供者
 */
object DiagramEditorProvider {

    /**
     * 打开导览图选择器
     */
    fun openDiagramSelector(project: Project) {
        val diagramService = DiagramService.getInstance(project)
        val diagrams = diagramService.getAllDiagrams()

        if (diagrams.isEmpty()) {
            // 创建默认导览图
            diagramService.createDiagram("主流程", DiagramType.MAIN_FLOW)
            openDiagramEditor(project, diagramService.getAllDiagrams().first())
            return
        }

        if (diagrams.size == 1) {
            openDiagramEditor(project, diagrams.first())
            return
        }

        // 显示选择列表
        val listModel = DefaultListModel<Diagram>()
        diagrams.forEach { listModel.addElement(it) }

        val list = JBList(listModel)
        list.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val diagram = value as? Diagram
                if (diagram != null) {
                    text = "${diagram.name} (${diagram.type.displayName}) - ${diagram.nodes.size} 节点"
                }
                return this
            }
        }

        val popup = JBPopupFactory.getInstance()
            .createListPopupBuilder(list)
            .setTitle("选择导览图")
            .setItemChoosenCallback {
                val selected = list.selectedValue
                if (selected != null) {
                    openDiagramEditor(project, selected)
                }
            }
            .setAdText("双击打开导览图")
            .createPopup()

        popup.showCenteredInCurrentWindow(project)
    }

    /**
     * 打开导览图编辑器
     */
    fun openDiagramEditor(project: Project, diagram: Diagram) {
        val dialog = DiagramEditorDialog(project, diagram)
        dialog.show()
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
 * 创建导览图对话框
 */
class CreateDiagramDialog(project: Project) : DialogWrapper(project) {

    private val nameField = JTextField(20)
    private val typeCombo = JComboBox(DiagramType.entries.toTypedArray())
    private val descField = JTextField(30)

    init {
        title = "创建新导览图"
        init()
    }

    override fun createCenterPanel(): JComponent {
        typeCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val type = value as? DiagramType
                if (type != null) {
                    text = type.displayName
                }
                return this
            }
        }

        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        val namePanel = JPanel(BorderLayout())
        namePanel.add(JLabel("名称: "), BorderLayout.WEST)
        namePanel.add(nameField, BorderLayout.CENTER)

        val typePanel = JPanel(BorderLayout())
        typePanel.add(JLabel("类型: "), BorderLayout.WEST)
        typePanel.add(typeCombo, BorderLayout.CENTER)

        val descPanel = JPanel(BorderLayout())
        descPanel.add(JLabel("描述: "), BorderLayout.WEST)
        descPanel.add(descField, BorderLayout.CENTER)

        panel.add(namePanel)
        panel.add(Box.createVerticalStrut(10))
        panel.add(typePanel)
        panel.add(Box.createVerticalStrut(10))
        panel.add(descPanel)

        panel.preferredSize = Dimension(400, 120)
        return panel
    }

    fun getDiagramName(): String = nameField.text.trim().ifEmpty { "新导览图" }
    fun getDiagramType(): DiagramType = typeCombo.selectedItem as DiagramType
    fun getDescription(): String = descField.text.trim()
}
