package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import com.longlong.bookmark.model.*
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.service.DiagramService
import java.awt.*
import java.awt.event.*
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import javax.swing.*

/**
 * 导览图编辑器对话框
 */
class DiagramEditorDialog(
    private val project: Project,
    private val diagram: Diagram
) : DialogWrapper(project) {

    private val diagramService = DiagramService.getInstance(project)
    private val bookmarkService = BookmarkService.getInstance(project)
    private val canvasPanel = DiagramCanvas()

    init {
        title = "导览图: ${diagram.name}"
        setSize(1000, 700)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())

        // 工具栏
        val toolbar = createToolbar()
        mainPanel.add(toolbar, BorderLayout.NORTH)

        // 画布
        val scrollPane = JScrollPane(canvasPanel)
        scrollPane.preferredSize = Dimension(900, 600)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        // 右侧面板 - 书签列表
        val sidePanel = createSidePanel()
        mainPanel.add(sidePanel, BorderLayout.EAST)

        return mainPanel
    }

    private fun createToolbar(): JPanel {
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))

        // 布局选择
        toolbar.add(JLabel("布局:"))
        val layoutCombo = JComboBox(DiagramLayout.entries.toTypedArray())
        layoutCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? DiagramLayout)?.displayName ?: ""
                return this
            }
        }
        layoutCombo.selectedItem = diagram.layout
        layoutCombo.addActionListener {
            diagramService.autoLayout(diagram.id, layoutCombo.selectedItem as DiagramLayout)
            canvasPanel.repaint()
        }
        toolbar.add(layoutCombo)

        toolbar.add(Box.createHorizontalStrut(20))

        // 自动布局按钮
        val autoLayoutBtn = JButton("自动布局")
        autoLayoutBtn.addActionListener {
            diagramService.autoLayout(diagram.id, diagram.layout)
            canvasPanel.repaint()
        }
        toolbar.add(autoLayoutBtn)

        // 添加节点按钮
        val addNodeBtn = JButton("添加节点")
        addNodeBtn.addActionListener {
            addNewNode()
        }
        toolbar.add(addNodeBtn)

        // 删除选中按钮
        val deleteBtn = JButton("删除选中")
        deleteBtn.addActionListener {
            deleteSelected()
        }
        toolbar.add(deleteBtn)

        toolbar.add(Box.createHorizontalStrut(20))

        // 连线模式
        val connectModeCheck = JCheckBox("连线模式")
        connectModeCheck.addActionListener {
            canvasPanel.connectMode = connectModeCheck.isSelected
        }
        toolbar.add(connectModeCheck)

        return toolbar
    }

    private fun createSidePanel(): JPanel {
        val sidePanel = JPanel(BorderLayout())
        sidePanel.preferredSize = Dimension(200, 0)
        sidePanel.border = JBUI.Borders.empty(5)

        val label = JLabel("拖拽书签到画布")
        sidePanel.add(label, BorderLayout.NORTH)

        // 书签列表
        val bookmarks = bookmarkService.getAllBookmarks()
        val listModel = DefaultListModel<Bookmark>()
        bookmarks.forEach { listModel.addElement(it) }

        val bookmarkList = JList(listModel)
        bookmarkList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val bookmark = value as? Bookmark
                if (bookmark != null) {
                    text = bookmark.getDisplayName()
                    toolTipText = "${bookmark.filePath}:${bookmark.startLine + 1}"
                }
                return this
            }
        }

        // 双击添加到画布
        bookmarkList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = bookmarkList.selectedValue
                    if (selected != null) {
                        val node = diagramService.addBookmarkToDiagram(diagram.id, selected)
                        if (node != null) {
                            canvasPanel.repaint()
                        }
                    }
                }
            }
        })

        // 支持拖拽
        bookmarkList.dragEnabled = true

        val scrollPane = JScrollPane(bookmarkList)
        sidePanel.add(scrollPane, BorderLayout.CENTER)

        return sidePanel
    }

    private fun addNewNode() {
        val label = JOptionPane.showInputDialog(
            canvasPanel,
            "输入节点名称:",
            "添加节点",
            JOptionPane.PLAIN_MESSAGE
        )

        if (!label.isNullOrBlank()) {
            val node = DiagramNode(
                label = label,
                x = 100.0 + Math.random() * 300,
                y = 100.0 + Math.random() * 200
            )
            diagramService.addNodeToDiagram(diagram.id, node)
            canvasPanel.repaint()
        }
    }

    private fun deleteSelected() {
        val selectedNode = canvasPanel.selectedNode
        if (selectedNode != null) {
            diagramService.removeNodeFromDiagram(diagram.id, selectedNode.id)
            canvasPanel.selectedNode = null
            canvasPanel.repaint()
        }

        val selectedConnection = canvasPanel.selectedConnection
        if (selectedConnection != null) {
            diagramService.removeConnectionFromDiagram(diagram.id, selectedConnection.id)
            canvasPanel.selectedConnection = null
            canvasPanel.repaint()
        }
    }

    /**
     * 导览图画布
     */
    inner class DiagramCanvas : JPanel() {

        var selectedNode: DiagramNode? = null
        var selectedConnection: DiagramConnection? = null
        var connectMode = false

        private var dragNode: DiagramNode? = null
        private var dragOffsetX = 0.0
        private var dragOffsetY = 0.0
        private var connectStartNode: DiagramNode? = null

        init {
            preferredSize = Dimension(2000, 1500)
            background = Color.WHITE

            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val node = findNodeAt(e.x, e.y)

                    if (connectMode && node != null) {
                        if (connectStartNode == null) {
                            connectStartNode = node
                        } else if (connectStartNode != node) {
                            // 创建连线
                            diagramService.addConnectionToDiagram(
                                diagram.id,
                                connectStartNode!!.id,
                                node.id
                            )
                            connectStartNode = null
                            repaint()
                        }
                        return
                    }

                    if (node != null) {
                        selectedNode = node
                        selectedConnection = null
                        dragNode = node
                        dragOffsetX = e.x - node.x
                        dragOffsetY = e.y - node.y
                    } else {
                        val conn = findConnectionAt(e.x, e.y)
                        if (conn != null) {
                            selectedConnection = conn
                            selectedNode = null
                        } else {
                            selectedNode = null
                            selectedConnection = null
                        }
                    }
                    repaint()

                    // 双击跳转到代码
                    if (e.clickCount == 2 && selectedNode != null) {
                        val bookmarkId = selectedNode?.bookmarkId
                        if (bookmarkId != null) {
                            val bookmark = bookmarkService.getBookmark(bookmarkId)
                            if (bookmark != null) {
                                bookmarkService.navigateToBookmark(bookmark)
                            }
                        }
                    }
                }

                override fun mouseReleased(e: MouseEvent) {
                    if (dragNode != null) {
                        diagramService.updateNodePosition(diagram.id, dragNode!!.id, dragNode!!.x, dragNode!!.y)
                    }
                    dragNode = null
                }
            })

            addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseDragged(e: MouseEvent) {
                    if (dragNode != null) {
                        dragNode!!.x = e.x - dragOffsetX
                        dragNode!!.y = e.y - dragOffsetY
                        repaint()
                    }
                }
            })

            // 右键菜单
            componentPopupMenu = createPopupMenu()
        }

        private fun createPopupMenu(): JPopupMenu {
            val menu = JPopupMenu()

            menu.add(JMenuItem("编辑节点").apply {
                addActionListener {
                    val node = selectedNode ?: return@addActionListener
                    val newLabel = JOptionPane.showInputDialog(
                        this@DiagramCanvas,
                        "节点名称:",
                        node.label
                    )
                    if (!newLabel.isNullOrBlank()) {
                        node.label = newLabel
                        diagramService.updateDiagram(diagram)
                        repaint()
                    }
                }
            })

            menu.add(JMenuItem("设置节点类型").apply {
                addActionListener {
                    val node = selectedNode ?: return@addActionListener
                    val types = NodeType.entries.map { it.displayName }.toTypedArray()
                    val selected = JOptionPane.showInputDialog(
                        this@DiagramCanvas,
                        "选择节点类型:",
                        "节点类型",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        types,
                        node.nodeType.displayName
                    )
                    if (selected != null) {
                        node.nodeType = NodeType.entries.find { it.displayName == selected } ?: NodeType.NORMAL
                        diagramService.updateDiagram(diagram)
                        repaint()
                    }
                }
            })

            menu.addSeparator()

            menu.add(JMenuItem("设置连线类型").apply {
                addActionListener {
                    val conn = selectedConnection ?: return@addActionListener
                    val types = ConnectionType.entries.map { it.displayName }.toTypedArray()
                    val selected = JOptionPane.showInputDialog(
                        this@DiagramCanvas,
                        "选择连线类型:",
                        "连线类型",
                        JOptionPane.PLAIN_MESSAGE,
                        null,
                        types,
                        conn.connectionType.displayName
                    )
                    if (selected != null) {
                        conn.connectionType = ConnectionType.entries.find { it.displayName == selected } ?: ConnectionType.NORMAL
                        diagramService.updateDiagram(diagram)
                        repaint()
                    }
                }
            })

            menu.add(JMenuItem("设置连线标签").apply {
                addActionListener {
                    val conn = selectedConnection ?: return@addActionListener
                    val label = JOptionPane.showInputDialog(
                        this@DiagramCanvas,
                        "连线标签:",
                        conn.label
                    )
                    if (label != null) {
                        conn.label = label
                        diagramService.updateDiagram(diagram)
                        repaint()
                    }
                }
            })

            menu.addSeparator()

            menu.add(JMenuItem("跳转到代码").apply {
                addActionListener {
                    val node = selectedNode ?: return@addActionListener
                    val bookmarkId = node.bookmarkId ?: return@addActionListener
                    val bookmark = bookmarkService.getBookmark(bookmarkId)
                    bookmark?.let { bookmarkService.navigateToBookmark(it) }
                }
            })

            menu.addSeparator()

            menu.add(JMenuItem("删除").apply {
                addActionListener {
                    deleteSelected()
                }
            })

            return menu
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            // 绘制连线
            diagram.connections.forEach { conn ->
                drawConnection(g2d, conn)
            }

            // 绘制节点
            diagram.nodes.forEach { node ->
                drawNode(g2d, node)
            }

            // 绘制连线起点标记
            if (connectMode && connectStartNode != null) {
                g2d.color = Color.RED
                g2d.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(5f), 0f)
                g2d.drawOval(
                    (connectStartNode!!.x + connectStartNode!!.width / 2 - 10).toInt(),
                    (connectStartNode!!.y + connectStartNode!!.height / 2 - 10).toInt(),
                    20, 20
                )
            }
        }

        private fun drawNode(g2d: Graphics2D, node: DiagramNode) {
            val x = node.x.toInt()
            val y = node.y.toInt()
            val w = node.width.toInt()
            val h = node.height.toInt()

            // 节点形状
            val shape = when (node.nodeType) {
                NodeType.START, NodeType.END -> {
                    java.awt.geom.RoundRectangle2D.Double(node.x, node.y, node.width, node.height, 30.0, 30.0)
                }
                NodeType.DECISION -> {
                    val diamond = Path2D.Double()
                    diamond.moveTo(node.x + node.width / 2, node.y)
                    diamond.lineTo(node.x + node.width, node.y + node.height / 2)
                    diamond.lineTo(node.x + node.width / 2, node.y + node.height)
                    diamond.lineTo(node.x, node.y + node.height / 2)
                    diamond.closePath()
                    diamond
                }
                else -> {
                    Rectangle(x, y, w, h)
                }
            }

            // 填充颜色
            val fillColor = if (node == selectedNode) {
                Color(200, 220, 255)
            } else {
                node.color?.let { Color.decode(it).brighter().brighter() } ?: Color(240, 240, 240)
            }
            g2d.color = fillColor
            g2d.fill(shape)

            // 边框
            g2d.color = if (node == selectedNode) Color.BLUE else (node.color?.let { Color.decode(it) } ?: Color.GRAY)
            g2d.stroke = BasicStroke(2f)
            g2d.draw(shape)

            // 文字
            g2d.color = Color.BLACK
            g2d.font = Font("Dialog", Font.PLAIN, 12)
            val fm = g2d.fontMetrics
            val textX = x + (w - fm.stringWidth(node.label)) / 2
            val textY = y + (h + fm.ascent) / 2 - 2
            g2d.drawString(node.label, textX, textY)

            // 如果关联了书签，显示图标
            if (node.bookmarkId != null) {
                g2d.color = Color.ORANGE
                g2d.fillOval(x + w - 12, y + 4, 8, 8)
            }
        }

        private fun drawConnection(g2d: Graphics2D, conn: DiagramConnection) {
            val sourceNode = diagram.getNode(conn.sourceNodeId) ?: return
            val targetNode = diagram.getNode(conn.targetNodeId) ?: return

            val startX = sourceNode.x + sourceNode.width / 2
            val startY = sourceNode.y + sourceNode.height
            val endX = targetNode.x + targetNode.width / 2
            val endY = targetNode.y

            // 连线样式
            val stroke = when (conn.connectionType) {
                ConnectionType.LOOP, ConnectionType.EXCEPTION -> {
                    BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(5f), 0f)
                }
                else -> BasicStroke(2f)
            }
            g2d.stroke = stroke

            // 连线颜色
            g2d.color = when {
                conn == selectedConnection -> Color.BLUE
                conn.connectionType == ConnectionType.CONDITION_YES -> Color(67, 160, 71)
                conn.connectionType == ConnectionType.CONDITION_NO -> Color(229, 57, 53)
                conn.connectionType == ConnectionType.EXCEPTION -> Color(255, 152, 0)
                else -> Color.GRAY
            }

            // 绘制线
            g2d.draw(Line2D.Double(startX, startY, endX, endY))

            // 绘制箭头
            drawArrowHead(g2d, startX, startY, endX, endY)

            // 绘制标签
            if (conn.label.isNotEmpty()) {
                val labelX = ((startX + endX) / 2).toInt()
                val labelY = ((startY + endY) / 2).toInt()
                g2d.color = Color.BLACK
                g2d.font = Font("Dialog", Font.PLAIN, 10)
                g2d.drawString(conn.label, labelX, labelY)
            }
        }

        private fun drawArrowHead(g2d: Graphics2D, x1: Double, y1: Double, x2: Double, y2: Double) {
            val arrowSize = 10.0
            val angle = Math.atan2(y2 - y1, x2 - x1)

            val path = Path2D.Double()
            path.moveTo(x2, y2)
            path.lineTo(
                x2 - arrowSize * Math.cos(angle - Math.PI / 6),
                y2 - arrowSize * Math.sin(angle - Math.PI / 6)
            )
            path.lineTo(
                x2 - arrowSize * Math.cos(angle + Math.PI / 6),
                y2 - arrowSize * Math.sin(angle + Math.PI / 6)
            )
            path.closePath()

            g2d.fill(path)
        }

        private fun findNodeAt(x: Int, y: Int): DiagramNode? {
            return diagram.nodes.find { node ->
                x >= node.x && x <= node.x + node.width &&
                y >= node.y && y <= node.y + node.height
            }
        }

        private fun findConnectionAt(x: Int, y: Int): DiagramConnection? {
            return diagram.connections.find { conn ->
                val sourceNode = diagram.getNode(conn.sourceNodeId) ?: return@find false
                val targetNode = diagram.getNode(conn.targetNodeId) ?: return@find false

                val startX = sourceNode.x + sourceNode.width / 2
                val startY = sourceNode.y + sourceNode.height
                val endX = targetNode.x + targetNode.width / 2
                val endY = targetNode.y

                // 检查点是否在线段附近
                val dist = pointToLineDistance(x.toDouble(), y.toDouble(), startX, startY, endX, endY)
                dist < 10
            }
        }

        private fun pointToLineDistance(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
            val dx = x2 - x1
            val dy = y2 - y1
            val t = maxOf(0.0, minOf(1.0, ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)))
            val nearestX = x1 + t * dx
            val nearestY = y1 + t * dy
            return Math.sqrt((px - nearestX) * (px - nearestX) + (py - nearestY) * (py - nearestY))
        }
    }
}
