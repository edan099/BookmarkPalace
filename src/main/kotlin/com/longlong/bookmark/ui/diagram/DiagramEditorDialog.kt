package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
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
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * 导览图编辑器 - 完整功能版
 */
class DiagramEditorDialog(
    private val project: Project,
    private val diagram: Diagram
) : DialogWrapper(project) {

    private val diagramService = DiagramService.getInstance(project)
    private val bookmarkService = BookmarkService.getInstance(project)
    private val canvas = DiagramCanvas()
    private val propertyPanel = PropertyPanel()
    private var zoomLabel: JLabel? = null
    private var searchField: SearchTextField? = null
    private var bookmarkListModel = DefaultListModel<Bookmark>()
    private var allBookmarks = listOf<Bookmark>()

    init {
        title = "${Messages.diagram}: ${diagram.name}"
        setSize(1400, 850)
        init()
    }

    override fun createCenterPanel(): JComponent {
        val main = JPanel(BorderLayout())
        main.add(createToolbar(), BorderLayout.NORTH)
        
        // 中间区域：属性面板 + 画布 + 书签面板
        val centerPanel = JPanel(BorderLayout())
        centerPanel.add(propertyPanel, BorderLayout.WEST)
        centerPanel.add(JBScrollPane(canvas).apply { preferredSize = Dimension(800, 700) }, BorderLayout.CENTER)
        centerPanel.add(createBookmarkPanel(), BorderLayout.EAST)
        
        main.add(centerPanel, BorderLayout.CENTER)
        main.add(createStatusBar(), BorderLayout.SOUTH)
        return main
    }

    private fun createToolbar(): JPanel {
        val bar = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4))
        bar.add(JButton(Messages.addNode).apply { addActionListener { addNode() } })
        bar.add(JButton(Messages.delete).apply { addActionListener { deleteSelected() } })
        bar.add(JSeparator(JSeparator.VERTICAL).apply { preferredSize = Dimension(2, 20) })
        bar.add(JButton("+").apply { toolTipText = Messages.zoomIn; addActionListener { canvas.zoom(1.2) } })
        bar.add(JButton("-").apply { toolTipText = Messages.zoomOut; addActionListener { canvas.zoom(0.8) } })
        bar.add(JButton("100%").apply { addActionListener { canvas.resetZoom() } })
        zoomLabel = JLabel("100%")
        bar.add(zoomLabel)
        return bar
    }

    private fun createBookmarkPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(240, 0)
        panel.border = JBUI.Borders.empty(8)
        
        // 搜索框
        searchField = SearchTextField().apply {
            textEditor.emptyText.text = Messages.searchPlaceholder
            addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent) = filterBookmarks()
                override fun removeUpdate(e: DocumentEvent) = filterBookmarks()
                override fun changedUpdate(e: DocumentEvent) = filterBookmarks()
            })
        }
        panel.add(searchField, BorderLayout.NORTH)
        
        // 书签列表
        allBookmarks = bookmarkService.getAllBookmarks()
        bookmarkListModel = DefaultListModel<Bookmark>().apply { allBookmarks.forEach { addElement(it) } }
        
        val list = JList(bookmarkListModel).apply {
            cellRenderer = BookmarkListRenderer()
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) selectedValue?.let { addBookmarkNode(it) }
                }
            })
        }
        panel.add(JBScrollPane(list), BorderLayout.CENTER)
        
        // 颜色筛选
        val filterPanel = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2))
        filterPanel.add(JLabel("${Messages.filterByColor}:"))
        val colorCombo = JComboBox(arrayOf(Messages.allColors) + BookmarkColor.values().map { it.displayName })
        colorCombo.addActionListener { filterBookmarks() }
        filterPanel.add(colorCombo)
        panel.add(filterPanel, BorderLayout.SOUTH)
        
        return panel
    }

    private fun filterBookmarks() {
        val query = searchField?.text?.lowercase() ?: ""
        bookmarkListModel.clear()
        allBookmarks.filter { bm ->
            query.isEmpty() || bm.alias.lowercase().contains(query) || 
            bm.comment.lowercase().contains(query) || bm.filePath.lowercase().contains(query)
        }.forEach { bookmarkListModel.addElement(it) }
    }

    private fun createStatusBar(): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel("💡 ${Messages.tipDragToConnect} | ${Messages.tipDoubleClickEdit} | ${Messages.tipDragCornerResize}"))
        }
    }

    private fun addNode() {
        val label = JOptionPane.showInputDialog(canvas, "${Messages.name}:", Messages.addNode, JOptionPane.PLAIN_MESSAGE)
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
            propertyPanel.clearSelection()
        }
        canvas.selectedConnection?.let {
            diagramService.removeConnectionFromDiagram(diagram.id, it.id)
            canvas.selectedConnection = null
            propertyPanel.clearSelection()
        }
        canvas.repaint()
    }

    private fun refreshUI() {
        title = "${Messages.diagram}: ${diagram.name}"
        SwingUtilities.updateComponentTreeUI(contentPane)
    }

    inner class BookmarkListRenderer : DefaultListCellRenderer() {
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

    /**
     * 属性编辑面板
     */
    inner class PropertyPanel : JPanel(BorderLayout()) {
        private val cardLayout = CardLayout()
        private val cardPanel = JPanel(cardLayout)
        private val emptyPanel = JPanel().apply { add(JLabel(Messages.properties)) }
        private val nodePanel = NodePropertyPanel()
        private val connPanel = ConnectionPropertyPanel()

        init {
            preferredSize = Dimension(220, 0)
            border = JBUI.Borders.empty(8)
            add(JLabel("<html><b>${Messages.properties}</b></html>"), BorderLayout.NORTH)
            cardPanel.add(emptyPanel, "empty")
            cardPanel.add(nodePanel, "node")
            cardPanel.add(connPanel, "connection")
            add(cardPanel, BorderLayout.CENTER)
            cardLayout.show(cardPanel, "empty")
        }

        fun showNode(node: DiagramNode) {
            nodePanel.bind(node)
            cardLayout.show(cardPanel, "node")
        }

        fun showConnection(conn: DiagramConnection) {
            connPanel.bind(conn)
            cardLayout.show(cardPanel, "connection")
        }

        fun clearSelection() {
            cardLayout.show(cardPanel, "empty")
        }

        inner class NodePropertyPanel : JPanel() {
            private var currentNode: DiagramNode? = null
            private val labelField = JTextField(15)
            private val fontSizeSpinner = JSpinner(SpinnerNumberModel(12, 8, 36, 1))
            private val borderWidthSpinner = JSpinner(SpinnerNumberModel(1.5, 0.5, 10.0, 0.5))
            private val fillColorBtn = JButton("    ")
            private val textColorBtn = JButton("    ")
            private val borderColorBtn = JButton("    ")
            private val shapeCombo = JComboBox(NodeShape.values())

            init {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(createRow(Messages.name, labelField))
                add(createRow(Messages.shape, shapeCombo))
                add(createRow(Messages.fontSize, fontSizeSpinner))
                add(createRow(Messages.borderWidth, borderWidthSpinner))
                add(createRow(Messages.fillColor, fillColorBtn))
                add(createRow(Messages.textColor, textColorBtn))
                add(createRow(Messages.borderColor, borderColorBtn))

                labelField.addActionListener { applyChanges() }
                fontSizeSpinner.addChangeListener { applyChanges() }
                borderWidthSpinner.addChangeListener { applyChanges() }
                shapeCombo.addActionListener { applyChanges() }
                
                setupColorButton(fillColorBtn) { currentNode?.color = it }
                setupColorButton(textColorBtn) { currentNode?.textColor = it }
                setupColorButton(borderColorBtn) { currentNode?.borderColor = it }
            }

            private fun createRow(label: String, comp: JComponent): JPanel {
                return JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
                    add(JLabel("$label:"))
                    add(comp)
                }
            }

            private fun setupColorButton(btn: JButton, setter: (String) -> Unit) {
                btn.addActionListener {
                    val c = JColorChooser.showDialog(btn, Messages.color, btn.background)
                    if (c != null) {
                        btn.background = c
                        setter(String.format("#%02X%02X%02X", c.red, c.green, c.blue))
                        applyChanges()
                    }
                }
            }

            fun bind(node: DiagramNode) {
                currentNode = node
                labelField.text = node.label
                fontSizeSpinner.value = node.fontSize
                borderWidthSpinner.value = node.borderWidth.toDouble()
                shapeCombo.selectedItem = node.shape
                fillColorBtn.background = try { Color.decode(node.color) } catch (e: Exception) { Color.BLUE }
                textColorBtn.background = try { Color.decode(node.textColor) } catch (e: Exception) { Color.WHITE }
                borderColorBtn.background = try { Color.decode(node.borderColor) } catch (e: Exception) { Color.DARK_GRAY }
            }

            private fun applyChanges() {
                currentNode?.let { node ->
                    node.label = labelField.text
                    node.fontSize = fontSizeSpinner.value as Int
                    node.borderWidth = (borderWidthSpinner.value as Double).toFloat()
                    node.shape = shapeCombo.selectedItem as NodeShape
                    diagramService.updateDiagram(diagram)
                    canvas.repaint()
                }
            }
        }

        inner class ConnectionPropertyPanel : JPanel() {
            private var currentConn: DiagramConnection? = null
            private val labelField = JTextField(15)
            private val lineWidthSpinner = JSpinner(SpinnerNumberModel(2.0, 0.5, 10.0, 0.5))
            private val fontSizeSpinner = JSpinner(SpinnerNumberModel(11, 8, 24, 1))
            private val lineColorBtn = JButton("    ")
            private val typeCombo = JComboBox(ConnectionType.values())

            init {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(createRow(Messages.connectionLabel, labelField))
                add(createRow(Messages.lineStyle, typeCombo))
                add(createRow(Messages.lineWidth, lineWidthSpinner))
                add(createRow(Messages.fontSize, fontSizeSpinner))
                add(createRow(Messages.lineColor, lineColorBtn))

                labelField.addActionListener { applyChanges() }
                lineWidthSpinner.addChangeListener { applyChanges() }
                fontSizeSpinner.addChangeListener { applyChanges() }
                typeCombo.addActionListener { applyChanges() }
                
                lineColorBtn.addActionListener {
                    val c = JColorChooser.showDialog(lineColorBtn, Messages.color, lineColorBtn.background)
                    if (c != null) {
                        lineColorBtn.background = c
                        currentConn?.lineColor = String.format("#%02X%02X%02X", c.red, c.green, c.blue)
                        applyChanges()
                    }
                }
            }

            private fun createRow(label: String, comp: JComponent): JPanel {
                return JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
                    add(JLabel("$label:"))
                    add(comp)
                }
            }

            fun bind(conn: DiagramConnection) {
                currentConn = conn
                labelField.text = conn.label
                lineWidthSpinner.value = conn.lineWidth.toDouble()
                fontSizeSpinner.value = conn.fontSize
                typeCombo.selectedItem = conn.connectionType
                lineColorBtn.background = try { Color.decode(conn.lineColor) } catch (e: Exception) { Color.GRAY }
            }

            private fun applyChanges() {
                currentConn?.let { conn ->
                    conn.label = labelField.text
                    conn.lineWidth = (lineWidthSpinner.value as Double).toFloat()
                    conn.fontSize = fontSizeSpinner.value as Int
                    conn.connectionType = typeCombo.selectedItem as ConnectionType
                    diagramService.updateDiagram(diagram)
                    canvas.repaint()
                }
            }
        }
    }

    /**
     * 导览图画布
     */
    inner class DiagramCanvas : JPanel() {
        var selectedNode: DiagramNode? = null
        var selectedConnection: DiagramConnection? = null
        
        private var scale = 1.0
        private var dragNode: DiagramNode? = null
        private var dragOffset = Point()
        private var resizeNode: DiagramNode? = null
        private var resizeCorner = -1
        private var isDrawingConnection = false
        private var connStartNode: DiagramNode? = null
        private var connEndPoint: Point? = null
        private val CORNER_SIZE = 8
        private val EDGE_SIZE = 10

        init {
            preferredSize = Dimension(2000, 1500)
            background = Color(248, 249, 250)
            setupMouseHandlers()
            componentPopupMenu = createContextMenu()
        }

        fun zoom(factor: Double) {
            scale = (scale * factor).coerceIn(0.25, 3.0)
            zoomLabel?.text = "${(scale * 100).toInt()}%"
            repaint()
        }

        fun resetZoom() { scale = 1.0; zoomLabel?.text = "100%"; repaint() }

        private fun setupMouseHandlers() {
            addMouseWheelListener { e -> zoom(if (e.wheelRotation < 0) 1.1 else 0.9) }

            addMouseListener(object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    val mx = (e.x / scale).toInt()
                    val my = (e.y / scale).toInt()

                    // 检查顶点（调整大小）
                    for (node in diagram.nodes.reversed()) {
                        val corner = getCornerAt(node, mx, my)
                        if (corner >= 0) {
                            resizeNode = node; resizeCorner = corner
                            selectedNode = node; selectedConnection = null
                            propertyPanel.showNode(node)
                            repaint(); return
                        }
                    }

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
                        propertyPanel.clearSelection()
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
                            // 检查是否有反向连线，设置偏移
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
                    
                    resizeNode?.let { diagramService.updateDiagram(diagram) }
                    resizeNode = null; resizeCorner = -1
                }

                override fun mouseClicked(e: MouseEvent) {
                    val mx = (e.x / scale).toInt()
                    val my = (e.y / scale).toInt()
                    if (e.clickCount == 2) {
                        selectedNode?.let { editNodeLabel(it); return }
                        findConnectionAt(mx, my)?.let { editConnectionLabel(it) }
                    }
                }
            })

            addMouseMotionListener(object : MouseMotionAdapter() {
                override fun mouseDragged(e: MouseEvent) {
                    val mx = (e.x / scale).toInt()
                    val my = (e.y / scale).toInt()

                    if (isDrawingConnection) { connEndPoint = Point(mx, my); repaint(); return }
                    if (resizeNode != null) { resizeNodeByCorner(resizeNode!!, resizeCorner, mx, my); repaint(); return }
                    if (dragNode != null) {
                        dragNode!!.x = (mx - dragOffset.x).coerceAtLeast(0).toDouble()
                        dragNode!!.y = (my - dragOffset.y).coerceAtLeast(0).toDouble()
                        repaint()
                    }
                }
            })
        }

        private fun getCornerAt(node: DiagramNode, x: Int, y: Int): Int {
            val corners = node.getCorners()
            for ((i, c) in corners.withIndex()) {
                if (Math.abs(x - c.first) <= CORNER_SIZE && Math.abs(y - c.second) <= CORNER_SIZE) return i
            }
            return -1
        }

        private fun isOnEdgeMidpoint(node: DiagramNode, x: Int, y: Int) = 
            node.getEdgeMidpoints().any { (mx, my) -> Math.abs(x - mx) <= EDGE_SIZE && Math.abs(y - my) <= EDGE_SIZE }

        private fun resizeNodeByCorner(node: DiagramNode, corner: Int, mx: Int, my: Int) {
            val minSize = 40.0
            when (corner) {
                0 -> { val nw = node.x + node.width - mx; val nh = node.y + node.height - my
                    if (nw >= minSize) { node.width = nw; node.x = mx.toDouble() }
                    if (nh >= minSize) { node.height = nh; node.y = my.toDouble() } }
                1 -> { val nw = mx - node.x; val nh = node.y + node.height - my
                    if (nw >= minSize) node.width = nw
                    if (nh >= minSize) { node.height = nh; node.y = my.toDouble() } }
                2 -> { val nw = mx - node.x; val nh = my - node.y
                    if (nw >= minSize) node.width = nw; if (nh >= minSize) node.height = nh }
                3 -> { val nw = node.x + node.width - mx; val nh = my - node.y
                    if (nw >= minSize) { node.width = nw; node.x = mx.toDouble() }
                    if (nh >= minSize) node.height = nh }
            }
        }

        private fun editNodeLabel(node: DiagramNode) {
            val label = JOptionPane.showInputDialog(this, Messages.name, node.label)
            if (label != null) { node.label = label; diagramService.updateDiagram(diagram); repaint() }
        }

        private fun editConnectionLabel(conn: DiagramConnection) {
            val label = JOptionPane.showInputDialog(this, Messages.connectionLabel, conn.label)
            if (label != null) { conn.label = label; diagramService.updateDiagram(diagram); repaint() }
        }

        private fun createContextMenu() = JPopupMenu().apply {
            add(JMenuItem(Messages.editNode).apply { addActionListener { selectedNode?.let { editNodeLabel(it) } } })
            add(JMenuItem(Messages.editConnection).apply { addActionListener { selectedConnection?.let { editConnectionLabel(it) } } })
            addSeparator()
            add(JMenuItem(Messages.jumpTo).apply {
                addActionListener {
                    selectedNode?.bookmarkId?.let { id ->
                        bookmarkService.getBookmark(id)?.let { bookmarkService.navigateToBookmark(it) }
                    }
                }
            })
            addSeparator()
            add(JMenuItem(Messages.delete).apply { addActionListener { deleteSelected() } })
        }

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
            val x = node.x; val y = node.y; val w = node.width; val h = node.height
            val isSelected = node == selectedNode

            // 圆形时使用正方形边界确保居中
            val size = if (node.shape == NodeShape.CIRCLE) maxOf(w, h) else w
            val actualW = if (node.shape == NodeShape.CIRCLE) size else w
            val actualH = if (node.shape == NodeShape.CIRCLE) size else h

            val shape: Shape = when (node.shape) {
                NodeShape.RECTANGLE -> Rectangle2D.Double(x, y, actualW, actualH)
                NodeShape.ROUNDED_RECT -> RoundRectangle2D.Double(x, y, actualW, actualH, 15.0, 15.0)
                NodeShape.CIRCLE -> Ellipse2D.Double(x, y, size, size)
                NodeShape.ELLIPSE -> Ellipse2D.Double(x, y, actualW, actualH)
                NodeShape.DIAMOND -> Path2D.Double().apply {
                    moveTo(x + actualW / 2, y); lineTo(x + actualW, y + actualH / 2)
                    lineTo(x + actualW / 2, y + actualH); lineTo(x, y + actualH / 2); closePath()
                }
            }

            // 填充
            g2d.color = try { Color.decode(node.color) } catch (e: Exception) { Color(74, 144, 217) }
            g2d.fill(shape)

            // 边框
            g2d.color = if (isSelected) Color(255, 193, 7) else try { Color.decode(node.borderColor) } catch (e: Exception) { Color.DARK_GRAY }
            g2d.stroke = BasicStroke(if (isSelected) node.borderWidth + 1.5f else node.borderWidth)
            g2d.draw(shape)

            // 非书签节点标记
            if (!node.isBookmarkNode()) {
                g2d.color = Color(220, 53, 69)
                g2d.fillOval((x + 4).toInt(), (y + 4).toInt(), 10, 10)
                g2d.color = Color.WHITE
                g2d.font = Font("Dialog", Font.BOLD, 8)
                g2d.drawString("!", (x + 7).toInt(), (y + 12).toInt())
            }

            // 文字 - 始终居中
            g2d.color = try { Color.decode(node.textColor) } catch (e: Exception) { Color.WHITE }
            g2d.font = Font("Dialog", Font.BOLD, node.fontSize)
            val fm = g2d.fontMetrics
            val textX = (x + (actualW - fm.stringWidth(node.label)) / 2).toInt()
            val textY = (y + (actualH + fm.ascent - fm.descent) / 2).toInt()
            g2d.drawString(node.label, textX, textY)

            // 选中时显示控制点
            if (isSelected) {
                g2d.color = Color(255, 193, 7)
                node.getCorners().forEach { (cx, cy) ->
                    g2d.fillRect((cx - CORNER_SIZE / 2).toInt(), (cy - CORNER_SIZE / 2).toInt(), CORNER_SIZE, CORNER_SIZE)
                }
                g2d.color = Color(100, 149, 237)
                node.getEdgeMidpoints().forEach { (mx, my) ->
                    g2d.fillOval((mx - 5).toInt(), (my - 5).toInt(), 10, 10)
                }
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

            // 计算曲线控制点（含偏移支持双向连线）
            val midX = (sx + ex) / 2
            val midY = (sy + ey) / 2
            val dx = ex - sx; val dy = ey - sy
            val len = Math.sqrt(dx * dx + dy * dy)
            val nx = if (len > 0) -dy / len else 0.0
            val ny = if (len > 0) dx / len else 0.0
            val ctrlX = midX + nx * conn.curveOffset
            val ctrlY = midY + ny * conn.curveOffset - 20

            g2d.draw(QuadCurve2D.Double(sx, sy, ctrlX, ctrlY, ex, ey))

            // 箭头
            if (conn.connectionType == ConnectionType.ARROW || conn.connectionType == ConnectionType.NORMAL) {
                drawArrow(g2d, ctrlX, ctrlY, ex, ey)
            }

            // 标签
            if (conn.label.isNotEmpty()) {
                g2d.color = Color.DARK_GRAY
                g2d.font = Font("Dialog", Font.PLAIN, conn.fontSize)
                g2d.drawString(conn.label, (ctrlX).toInt(), (ctrlY - 5).toInt())
            }
        }

        private fun drawArrow(g2d: Graphics2D, x1: Double, y1: Double, x2: Double, y2: Double) {
            val angle = Math.atan2(y2 - y1, x2 - x1)
            val size = 10.0
            g2d.fill(Path2D.Double().apply {
                moveTo(x2, y2)
                lineTo(x2 - size * Math.cos(angle - Math.PI / 6), y2 - size * Math.sin(angle - Math.PI / 6))
                lineTo(x2 - size * Math.cos(angle + Math.PI / 6), y2 - size * Math.sin(angle + Math.PI / 6))
                closePath()
            })
        }

        private fun findNodeAt(x: Int, y: Int) = diagram.nodes.findLast { n ->
            x >= n.x && x <= n.x + n.width && y >= n.y && y <= n.y + n.height
        }

        private fun findConnectionAt(x: Int, y: Int) = diagram.connections.find { c ->
            val s = diagram.getNode(c.sourceNodeId) ?: return@find false
            val t = diagram.getNode(c.targetNodeId) ?: return@find false
            val (sx, sy) = s.getNearestEdgeMidpoint(t.x + t.width / 2, t.y + t.height / 2)
            val (ex, ey) = t.getNearestEdgeMidpoint(s.x + s.width / 2, s.y + s.height / 2)
            pointToLineDistance(x.toDouble(), y.toDouble(), sx, sy, ex, ey) < 12
        }

        private fun pointToLineDistance(px: Double, py: Double, x1: Double, y1: Double, x2: Double, y2: Double): Double {
            val dx = x2 - x1; val dy = y2 - y1
            val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy).coerceAtLeast(0.001)
            val tc = t.coerceIn(0.0, 1.0)
            return Math.sqrt((px - x1 - tc * dx).let { it * it } + (py - y1 - tc * dy).let { it * it })
        }
    }
}
