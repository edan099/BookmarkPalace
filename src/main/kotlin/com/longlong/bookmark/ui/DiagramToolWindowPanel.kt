package com.longlong.bookmark.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.model.Diagram
import com.longlong.bookmark.service.DiagramChangeListener
import com.longlong.bookmark.service.DiagramService
import com.longlong.bookmark.ui.diagram.DiagramEditorProvider
import com.longlong.bookmark.ui.diagram.EmbeddedDiagramViewer
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.SwingUtilities

/**
 * 导览图侧边栏面板（类似 Maven 工具窗口）
 * 支持侧边栏内嵌查看和编辑导览图
 */
class DiagramToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val diagramService = DiagramService.getInstance(project)
    private val listModel = DefaultListModel<Diagram>()
    private val diagramList = JBList(listModel)
    
    // 嵌入式导览图查看器（复用同一个，避免重复创建）
    private val previewContainer = JPanel(CardLayout())
    private val emptyLabel = JLabel(
        if (Messages.isEnglish()) "Select a diagram to preview | 选择导览图进行预览" 
        else "选择导览图进行预览 | Select a diagram to preview", 
        SwingConstants.CENTER
    )
    private val loadingLabel = JLabel(
        if (Messages.isEnglish()) "⏳ Loading Draw.io... | 正在加载..." 
        else "⏳ 正在加载 Draw.io... | Loading...", 
        SwingConstants.CENTER
    )
    private var viewer: EmbeddedDiagramViewer? = null
    private var currentDiagramId: String? = null
    
    companion object {
        private const val CARD_EMPTY = "empty"
        private const val CARD_LOADING = "loading"
        private const val CARD_VIEWER = "viewer"
    }

    init {
        // 列表渲染器
        diagramList.cellRenderer = DiagramListCellRenderer()
        
        // 选中导览图时更新预览
        diagramList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                showDiagramPreview(diagramList.selectedValue)
            }
        }
        
        // 双击在编辑器中打开
        diagramList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    openSelectedDiagram(viewOnly = false)
                }
            }
            
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) showPopupMenu(e)
            }
            
            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) showPopupMenu(e)
            }
        })

        // 构建预览容器
        previewContainer.add(emptyLabel, CARD_EMPTY)
        previewContainer.add(loadingLabel, CARD_LOADING)
        
        // 立即创建 viewer（让 Draw.io 在后台提前加载）
        viewer = EmbeddedDiagramViewer(project) { ready ->
            // Draw.io 加载完成回调
            if (ready && currentDiagramId != null) {
                SwingUtilities.invokeLater {
                    (previewContainer.layout as CardLayout).show(previewContainer, CARD_VIEWER)
                }
            }
        }
        previewContainer.add(viewer!!.component, CARD_VIEWER)
        
        // 使用分割面板：上方列表，下方预览
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            topComponent = JBScrollPane(diagramList)
            bottomComponent = previewContainer
            resizeWeight = 0.3
            dividerSize = 5
        }
        
        setContent(splitPane)

        // 工具栏
        val toolbar = createToolbar()
        setToolbar(toolbar.component)

        // 监听导览图变更
        diagramService.addChangeListener(object : DiagramChangeListener {
            override fun onDiagramAdded(diagram: Diagram) = refreshList()
            override fun onDiagramRemoved(diagram: Diagram) {
                refreshList()
                if (currentDiagramId == diagram.id) {
                    showEmptyPreview()
                }
            }
            override fun onDiagramUpdated(diagram: Diagram) {
                refreshList()
                if (currentDiagramId == diagram.id) {
                    viewer?.refresh(diagram)
                }
            }
            override fun onDiagramsRefreshed() = refreshList()
        })

        // 初始化列表
        refreshList()
    }
    
    /**
     * 显示导览图预览（复用同一个 viewer，切换时只更新数据）
     */
    private fun showDiagramPreview(diagram: Diagram?) {
        if (diagram == null) {
            showEmptyPreview()
            return
        }
        
        currentDiagramId = diagram.id
        
        // 加载导览图数据
        viewer?.loadDiagram(diagram)
        
        // 如果 Draw.io 还没准备好，显示加载中；否则直接显示
        if (viewer?.isReady == true) {
            (previewContainer.layout as CardLayout).show(previewContainer, CARD_VIEWER)
        } else {
            (previewContainer.layout as CardLayout).show(previewContainer, CARD_LOADING)
        }
    }
    
    private fun showEmptyPreview() {
        (previewContainer.layout as CardLayout).show(previewContainer, CARD_EMPTY)
        viewer?.clear()
        currentDiagramId = null
    }

    private fun refreshList() {
        val selectedId = diagramList.selectedValue?.id
        listModel.clear()
        diagramService.getAllDiagrams().forEach { listModel.addElement(it) }
        
        // 恢复选中
        if (selectedId != null) {
            for (i in 0 until listModel.size()) {
                if (listModel.getElementAt(i).id == selectedId) {
                    diagramList.selectedIndex = i
                    break
                }
            }
        }
    }

    private fun createToolbar(): ActionToolbar {
        val actionGroup = DefaultActionGroup().apply {
            // 新建导览图
            add(object : AnAction(
                if (Messages.isEnglish()) "New Diagram" else "新建导览图",
                if (Messages.isEnglish()) "Create a new diagram" else "创建新的导览图",
                AllIcons.General.Add
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    val diagram = DiagramEditorProvider.createNewDiagram(project)
                    if (diagram != null) {
                        refreshList()
                    }
                }
            })

            // 打开选中的导览图（编辑模式）
            add(object : AnAction(
                if (Messages.isEnglish()) "Open" else "打开",
                if (Messages.isEnglish()) "Open selected diagram in editor" else "在编辑器中打开选中的导览图",
                AllIcons.Actions.Edit
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    openSelectedDiagram(viewOnly = false)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = diagramList.selectedValue != null
                }
                
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })

            // 查看模式打开（编辑器Tab）
            add(object : AnAction(
                Messages.viewOnly,
                Messages.viewOnlyTip,
                AllIcons.Actions.Preview
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    openSelectedDiagram(viewOnly = true)
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = diagramList.selectedValue != null
                }
                
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
            
            // 在窗口中打开
            add(object : AnAction(
                Messages.openInWindow,
                Messages.openInWindowTip,
                AllIcons.Actions.MoveToWindow
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    openSelectedDiagramInWindow()
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = diagramList.selectedValue != null
                }
                
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })

            addSeparator()

            // 刷新
            add(object : AnAction(
                if (Messages.isEnglish()) "Refresh" else "刷新",
                if (Messages.isEnglish()) "Refresh diagram list" else "刷新导览图列表",
                AllIcons.Actions.Refresh
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    refreshList()
                }
            })

            // 删除
            add(object : AnAction(
                if (Messages.isEnglish()) "Delete" else "删除",
                if (Messages.isEnglish()) "Delete selected diagram" else "删除选中的导览图",
                AllIcons.General.Remove
            ) {
                override fun actionPerformed(e: AnActionEvent) {
                    deleteSelectedDiagram()
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = diagramList.selectedValue != null
                }
                
                override fun getActionUpdateThread() = ActionUpdateThread.EDT
            })
        }

        return ActionManager.getInstance()
            .createActionToolbar("DiagramToolbar", actionGroup, true)
            .apply { targetComponent = this@DiagramToolWindowPanel }
    }

    private fun openSelectedDiagram(viewOnly: Boolean) {
        val diagram = diagramList.selectedValue ?: return
        DiagramEditorProvider.openDiagramInEditor(project, diagram, viewOnly)
    }
    
    private fun openSelectedDiagramInWindow() {
        val diagram = diagramList.selectedValue ?: return
        DiagramEditorProvider.openDiagramEditor(project, diagram)
    }

    private fun deleteSelectedDiagram() {
        val diagram = diagramList.selectedValue ?: return
        val confirm = JOptionPane.showConfirmDialog(
            this,
            if (Messages.isEnglish()) 
                "Delete diagram \"${diagram.name}\"?" 
            else 
                "确定删除导览图 \"${diagram.name}\" 吗？",
            if (Messages.isEnglish()) "Confirm Delete" else "确认删除",
            JOptionPane.YES_NO_OPTION
        )
        if (confirm == JOptionPane.YES_OPTION) {
            diagramService.removeDiagram(diagram.id)
        }
    }

    private fun showPopupMenu(e: MouseEvent) {
        val index = diagramList.locationToIndex(e.point)
        if (index < 0) return
        diagramList.selectedIndex = index
        
        val diagram = diagramList.selectedValue ?: return

        val popup = JPopupMenu()
        
        // 侧边栏预览（最快）
        popup.add(JMenuItem(if (Messages.isEnglish()) "👁 Preview (Fast)" else "👁 预览（快速）").apply {
            addActionListener { showDiagramPreview(diagram) }
        })
        
        // 编辑器编辑（双击默认）
        popup.add(JMenuItem(if (Messages.isEnglish()) "✏️ Edit in Editor" else "✏️ 编辑器编辑").apply {
            addActionListener { openSelectedDiagram(viewOnly = false) }
        })
        
        // 窗口打开
        popup.add(JMenuItem(if (Messages.isEnglish()) "🪟 Open in Window" else "🪟 窗口打开").apply {
            addActionListener { openSelectedDiagramInWindow() }
        })

        popup.addSeparator()

        popup.add(JMenuItem(if (Messages.isEnglish()) "Rename" else "重命名").apply {
            addActionListener {
                val newName = JOptionPane.showInputDialog(
                    this@DiagramToolWindowPanel,
                    if (Messages.isEnglish()) "New name:" else "新名称:",
                    diagram.name
                )
                if (!newName.isNullOrBlank() && newName != diagram.name) {
                    diagram.name = newName
                    diagramService.updateDiagram(diagram)
                }
            }
        })

        popup.add(JMenuItem(if (Messages.isEnglish()) "🗑 Delete" else "🗑 删除").apply {
            addActionListener { deleteSelectedDiagram() }
        })

        popup.show(diagramList, e.x, e.y)
    }

    /**
     * 导览图列表渲染器
     */
    private inner class DiagramListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            
            val diagram = value as? Diagram ?: return this
            
            text = "📊 ${diagram.name}"
            toolTipText = if (diagram.description.isNotBlank()) {
                "${diagram.name} - ${diagram.description}"
            } else {
                diagram.name
            }
            
            border = JBUI.Borders.empty(4, 8)
            
            return this
        }
    }
}
