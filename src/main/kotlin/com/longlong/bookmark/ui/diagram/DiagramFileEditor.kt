package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.model.*
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.service.DiagramService
import java.awt.*
import java.awt.event.*
import java.awt.geom.*
import java.beans.PropertyChangeListener
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 导览图文件编辑器 - 支持分栏视图
 */
class DiagramFileEditor(
    private val project: Project,
    private val file: VirtualFile
) : UserDataHolderBase(), FileEditor {

    private val diagramService = DiagramService.getInstance(project)
    private val bookmarkService = BookmarkService.getInstance(project)
    private val diagramId = file.nameWithoutExtension
    private val diagram: Diagram get() = diagramService.getDiagram(diagramId) ?: createDefaultDiagram()
    
    private val mainPanel = JPanel(BorderLayout())
    private val canvas = DiagramEditorCanvas()
    private val propertyPanel = QuickPropertyPanel()
    private var zoomLabel = JLabel("100%")

    init {
        setupUI()
    }

    private fun createDefaultDiagram(): Diagram {
        return diagramService.createDiagram(diagramId, DiagramType.CUSTOM_FLOW)
    }

    private fun setupUI() {
        mainPanel.add(createToolbar(), BorderLayout.NORTH)
        
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            leftComponent = propertyPanel
            rightComponent = JBScrollPane(canvas)
            dividerLocation = 200
            dividerSize = 4
        }
        mainPanel.add(splitPane, BorderLayout.CENTER)
        mainPanel.add(createBookmarkSidebar(), BorderLayout.EAST)
    }

    private fun createToolbar(): JPanel {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        bar.add(JButton(Messages.addNode).apply { addActionListener { addNode() } })
        bar.add(JButton(Messages.delete).apply { addActionListener { deleteSelected() } })
        bar.add(JSeparator(JSeparator.VERTICAL).apply { preferredSize = Dimension(2, 20) })
        bar.add(JButton("+").apply { addActionListener { canvas.zoom(1.2) } })
        bar.add(JButton("-").apply { addActionListener { canvas.zoom(0.8) } })
        bar.add(zoomLabel)
        bar.add(Box.createHorizontalGlue())
        bar.add(JButton(Messages.switchLanguage).apply { 
            addActionListener { Messages.toggleLanguage(); mainPanel.repaint() } 
        })
        return bar
    }

    private fun createBookmarkSidebar(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(200, 0)
        panel.border = JBUI.Borders.empty(8)
        
        val searchField = SearchTextField().apply {
            textEditor.emptyText.text = Messages.searchPlaceholder
        }
        panel.add(searchField, BorderLayout.NORTH)
        
        val listModel = DefaultListModel<Bookmark>()
        bookmarkService.getAllBookmarks().forEach { listModel.addElement(it) }
        
        val list = JList(listModel).apply {
            cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, 
                    sel: Boolean, focus: Boolean): Component {
                    super.getListCellRendererComponent(list, value, index, sel, focus)
                    (value as? Bookmark)?.let {
                        text = "📌 ${it.getDisplayName()}"
                        foreground = Color.decode(it.color.hexColor)
                    }
                    return this
                }
            }
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) selectedValue?.let { addBookmarkNode(it) }
                }
            })
        }
        panel.add(JBScrollPane(list), BorderLayout.CENTER)
        return panel
    }

    private fun addNode() {
        val label = JOptionPane.showInputDialog(mainPanel, Messages.name, Messages.addNode, JOptionPane.PLAIN_MESSAGE)
        if (!label.isNullOrBlank()) {
            val node = DiagramNode(label = label, x = 100 + Math.random() * 200, y = 100 + Math.random() * 150)
            diagramService.addNodeToDiagram(diagram.id, node)
            canvas.repaint()
        }
    }

    private fun addBookmarkNode(bookmark: Bookmark) {
        val node = diagramService.addBookmarkToDiagram(diagram.id, bookmark)
        if (node != null) {
            node.x = 100 + Math.random() * 200
            node.y = 100 + Math.random() * 150
            canvas.repaint()
        }
    }

    private fun deleteSelected() {
        canvas.selectedNode?.let {
            diagramService.removeNodeFromDiagram(diagram.id, it.id)
            canvas.selectedNode = null
            propertyPanel.clear()
        }
        canvas.selectedConnection?.let {
            diagramService.removeConnectionFromDiagram(diagram.id, it.id)
            canvas.selectedConnection = null
            propertyPanel.clear()
        }
        canvas.repaint()
    }

    /**
     * 简化属性面板
     */
    inner class QuickPropertyPanel : JPanel(BorderLayout()) {
        private val content = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        
        init {
            preferredSize = Dimension(200, 0)
            border = JBUI.Borders.empty(8)
            add(JLabel("<html><b>${Messages.properties}</b></html>"), BorderLayout.NORTH)
            add(JBScrollPane(content), BorderLayout.CENTER)
        }

        fun showNode(node: DiagramNode) {
            content.removeAll()
            content.add(JLabel("${Messages.name}: ${node.label}"))
            content.add(JLabel("${Messages.shape}: ${node.shape.displayName}"))
            content.add(JButton(Messages.editNode).apply {
                addActionListener {
                    val label = JOptionPane.showInputDialog(this@QuickPropertyPanel, Messages.name, node.label)
                    if (label != null) { node.label = label; diagramService.updateDiagram(diagram); canvas.repaint() }
                }
            })
            content.revalidate()
            content.repaint()
        }

        fun showConnection(conn: DiagramConnection) {
            content.removeAll()
            content.add(JLabel("${Messages.connectionLabel}: ${conn.label}"))
            content.add(JButton(Messages.editConnection).apply {
                addActionListener {
                    val label = JOptionPane.showInputDialog(this@QuickPropertyPanel, Messages.connectionLabel, conn.label)
                    if (label != null) { conn.label = label; diagramService.updateDiagram(diagram); canvas.repaint() }
                }
            })
            content.revalidate()
            content.repaint()
        }

        fun clear() {
            content.removeAll()
            content.add(JLabel(Messages.properties))
            content.revalidate()
            content.repaint()
        }
    }

    /**
     * 画布组件
     */
    inner class DiagramEditorCanvas : JPanel() {
        var selectedNode: DiagramNode? = null
        var selectedConnection: DiagramConnection? = null
        private var scale = 1.0
        private var dragNode: DiagramNode? = null
        private var dragOffset = Point()
        private var isDrawingConnection = false
        private var connStartNode: DiagramNode? = null
        private var connEndPoint: Point? = null

        init {
            preferredSize = Dimension(2000, 1500)
            background = Color(248, 249, 250)
            setupMouseHandlers()
        }

        fun zoom(factor: Double) {
            scale = (scale * factor).coerceIn(0.25, 3.0)
            zoomLabel.text = "${(scale * 100).toInt()}%"
            repaint()
        }

        private fun setupMouseHandlers() {
            addMouseWheelListener { e -> zoom(if (e.wheelRotation < 0) 1.1 else 0.9) }

            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val mx = (e.x / scale).toInt()
                    val my = (e.y / scale).toInt()

                    // 检查边缘中点（连线）
                    for (node in diagram.nodes.reversed()) {
                        if (isOnEdgeMidpoint(node, mx, my)) {
                            isDrawingConnection = true
                            connStartNode = node
                            connEndPoint = Point(mx, my)
                            return
                        }
                    }

                    // 检查节点
                    val node = findNodeAt(mx, my)
                    if (node != null) {
                        selectedNode = node; selectedConnection = null
                        dragNode = node
                        dragOffset = Point(mx - node.x.toInt(), my - node.y.toInt())
                        propertyPanel.showNode(node)
                        repaint(); return
                    }

                    // 检查连线
                    val conn = findConnectionAt(mx, my)
                    if (conn != null) {
                        selectedConnection = conn; selectedNode = null
                        propertyPanel.showConnection(conn)
                    } else {
                        selectedNode = null; selectedConnection = null
                        propertyPanel.clear()
                    }
                    repaint()
                }

                override fun mouseReleased(e: MouseEvent) {
                    val mx = (e.x / scale).toInt()
                    val my = (e.y / scale).toInt()

                    if (isDrawingConnection && connStartNode != null) {
                        val target = findNodeAt(mx, my)
                        if (target != null && target != connStartNode) {
                            val conn = diagramService.addConnectionToDiagram(diagram.id, connStartNode!!.id, target.id)
                            if (conn != null) {
                                val reverse = diagram.connections.find { 
                                    it.sourceNodeId == target.id && it.targetNodeId == connStartNode!!.id 
                                }
                                if (reverse != null) {
                                    conn.curveOffset = 25.0
                                    reverse.curveOffset = -25.0
                                    diagramService.updateDiagram(diagram)
                                }
                            }
                        }
                        isDrawingConnection = false
                        connStartNode = null; connEndPoint = null
                        repaint()
                    }

                    dragNode?.let { diagramService.updateNodePosition(diagram.id, it.id, it.x, it.y) }
                    dragNode = null
                }

                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        selectedNode?.let { 
                            val label = JOptionPane.showInputDialog(this@DiagramEditorCanvas, Messages.name, it.label)
                            if (label != null) { it.label = label; diagramService.updateDiagram(diagram); repaint() }
                        }
                    }
                }
            })

            addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseDragged(e: MouseEvent) {
                    val mx = (e.x / scale).toInt()
                    val my = (e.y / scale).toInt()

                    if (isDrawingConnection) { connEndPoint = Point(mx, my); repaint(); return }
                    if (dragNode != null) {
                        dragNode!!.x = (mx - dragOffset.x).coerceAtLeast(0).toDouble()
                        dragNode!!.y = (my - dragOffset.y).coerceAtLeast(0).toDouble()
                        repaint()
                    }
                }
            })
        }

        private fun isOnEdgeMidpoint(node: DiagramNode, x: Int, y: Int) = 
            node.getEdgeMidpoints().any { (mx, my) -> Math.abs(x - mx) <= 10 && Math.abs(y - my) <= 10 }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.scale(scale, scale)

            diagram.connections.forEach { drawConnection(g2d, it) }
            
            if (isDrawingConnection && connStartNode != null && connEndPoint != null) {
                g2d.color = Color(100, 149, 237)
                g2d.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(6f), 0f)
                val (sx, sy) = connStartNode!!.getNearestEdgeMidpoint(connEndPoint!!.x.toDouble(), connEndPoint!!.y.toDouble())
                g2d.draw(Line2D.Double(sx, sy, connEndPoint!!.x.toDouble(), connEndPoint!!.y.toDouble()))
            }

            diagram.nodes.forEach { drawNode(g2d, it) }
        }

        private fun drawNode(g2d: Graphics2D, node: DiagramNode) {
            val x = node.x; val y = node.y
            val w = if (node.shape == NodeShape.CIRCLE) maxOf(node.width, node.height) else node.width
            val h = if (node.shape == NodeShape.CIRCLE) maxOf(node.width, node.height) else node.height
            val isSelected = node == selectedNode

            val shape: Shape = when (node.shape) {
                NodeShape.RECTANGLE -> Rectangle2D.Double(x, y, w, h)
                NodeShape.ROUNDED_RECT -> RoundRectangle2D.Double(x, y, w, h, 15.0, 15.0)
                NodeShape.CIRCLE -> Ellipse2D.Double(x, y, w, h)
                NodeShape.ELLIPSE -> Ellipse2D.Double(x, y, w, h)
                NodeShape.DIAMOND -> Path2D.Double().apply {
                    moveTo(x + w / 2, y); lineTo(x + w, y + h / 2)
                    lineTo(x + w / 2, y + h); lineTo(x, y + h / 2); closePath()
                }
            }

            g2d.color = try { Color.decode(node.color) } catch (e: Exception) { Color(74, 144, 217) }
            g2d.fill(shape)
            g2d.color = if (isSelected) Color(255, 193, 7) else try { Color.decode(node.borderColor) } catch (e: Exception) { Color.DARK_GRAY }
            g2d.stroke = BasicStroke(if (isSelected) 3f else node.borderWidth)
            g2d.draw(shape)

            // 文字居中
            g2d.color = try { Color.decode(node.textColor) } catch (e: Exception) { Color.WHITE }
            g2d.font = Font("Dialog", Font.BOLD, node.fontSize)
            val fm = g2d.fontMetrics
            val textX = (x + (w - fm.stringWidth(node.label)) / 2).toInt()
            val textY = (y + (h + fm.ascent - fm.descent) / 2).toInt()
            g2d.drawString(node.label, textX, textY)

            // 选中时显示连接点
            if (isSelected) {
                g2d.color = Color(100, 149, 237)
                node.getEdgeMidpoints().forEach { (mx, my) -> g2d.fillOval((mx - 5).toInt(), (my - 5).toInt(), 10, 10) }
            }
        }

        private fun drawConnection(g2d: Graphics2D, conn: DiagramConnection) {
            val source = diagram.getNode(conn.sourceNodeId) ?: return
            val target = diagram.getNode(conn.targetNodeId) ?: return
            val isSelected = conn == selectedConnection

            val (sx, sy) = source.getNearestEdgeMidpoint(target.x + target.width / 2, target.y + target.height / 2)
            val (ex, ey) = target.getNearestEdgeMidpoint(source.x + source.width / 2, source.y + source.height / 2)

            g2d.color = if (isSelected) Color(255, 193, 7) else try { Color.decode(conn.lineColor) } catch (e: Exception) { Color.GRAY }
            g2d.stroke = when (conn.connectionType) {
                ConnectionType.DASHED -> BasicStroke(conn.lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, floatArrayOf(8f, 4f), 0f)
                else -> BasicStroke(conn.lineWidth)
            }

            val midX = (sx + ex) / 2; val midY = (sy + ey) / 2
            val dx = ex - sx; val dy = ey - sy
            val len = Math.sqrt(dx * dx + dy * dy)
            val nx = if (len > 0) -dy / len else 0.0
            val ny = if (len > 0) dx / len else 0.0
            val ctrlX = midX + nx * conn.curveOffset
            val ctrlY = midY + ny * conn.curveOffset - 20

            g2d.draw(QuadCurve2D.Double(sx, sy, ctrlX, ctrlY, ex, ey))

            // 箭头
            val angle = Math.atan2(ey - ctrlY, ex - ctrlX)
            val size = 10.0
            g2d.fill(Path2D.Double().apply {
                moveTo(ex, ey)
                lineTo(ex - size * Math.cos(angle - Math.PI / 6), ey - size * Math.sin(angle - Math.PI / 6))
                lineTo(ex - size * Math.cos(angle + Math.PI / 6), ey - size * Math.sin(angle + Math.PI / 6))
                closePath()
            })

            if (conn.label.isNotEmpty()) {
                g2d.color = Color.DARK_GRAY
                g2d.font = Font("Dialog", Font.PLAIN, conn.fontSize)
                g2d.drawString(conn.label, (ctrlX).toInt(), (ctrlY - 5).toInt())
            }
        }

        private fun findNodeAt(x: Int, y: Int) = diagram.nodes.findLast { n ->
            x >= n.x && x <= n.x + n.width && y >= n.y && y <= n.y + n.height
        }

        private fun findConnectionAt(x: Int, y: Int) = diagram.connections.find { c ->
            val s = diagram.getNode(c.sourceNodeId) ?: return@find false
            val t = diagram.getNode(c.targetNodeId) ?: return@find false
            val (sx, sy) = s.getNearestEdgeMidpoint(t.x + t.width / 2, t.y + t.height / 2)
            val (ex, ey) = t.getNearestEdgeMidpoint(s.x + s.width / 2, s.y + s.height / 2)
            val dx = ex - sx; val dy = ey - sy
            val t2 = ((x - sx) * dx + (y - sy) * dy) / (dx * dx + dy * dy).coerceAtLeast(0.001)
            val tc = t2.coerceIn(0.0, 1.0)
            Math.sqrt((x - sx - tc * dx).let { it * it } + (y - sy - tc * dy).let { it * it }) < 12
        }
    }

    // FileEditor 接口实现
    override fun getComponent(): JComponent = mainPanel
    override fun getPreferredFocusedComponent(): JComponent = canvas
    override fun getName(): String = "Diagram: ${diagram.name}"
    override fun setState(state: FileEditorState) {}
    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun dispose() {}
    override fun getFile(): VirtualFile = file
}
