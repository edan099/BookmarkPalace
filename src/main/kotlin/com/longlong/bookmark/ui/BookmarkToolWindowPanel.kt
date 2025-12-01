package com.longlong.bookmark.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.longlong.bookmark.icons.BookmarkPalaceIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.*
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.model.Bookmark
import com.longlong.bookmark.model.BookmarkColor
import com.longlong.bookmark.model.BookmarkStatus
import com.longlong.bookmark.service.BookmarkChangeListener
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.ui.dialog.AddBookmarkDialog
import com.longlong.bookmark.ui.dialog.DonateDialog
import com.longlong.bookmark.ui.dialog.EditBookmarkDialog
import com.longlong.bookmark.ui.dialog.ExportDialog
import com.longlong.bookmark.ui.dialog.ImportDialog
import com.longlong.bookmark.ui.diagram.DiagramEditorProvider
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

/**
 * 书签工具窗口面板
 */
class BookmarkToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val bookmarkService = BookmarkService.getInstance(project)
    private val searchField = SearchTextField()
    private val tree: Tree
    private val treeModel: DefaultTreeModel
    private val rootNode = DefaultMutableTreeNode("Bookmarks")
    private val groupByLabel = JLabel()
    private val groupByCombo = JComboBox(GroupBy.values())

    // 折叠方式
    private var groupBy: GroupBy = GroupBy.FILE

    enum class GroupBy {
        FILE, COLOR, TAG, STATUS;
        
        fun getDisplayName(): String = when (this) {
            FILE -> if (Messages.isEnglish()) "By File" else "按文件"
            COLOR -> if (Messages.isEnglish()) "By Color" else "按颜色"
            TAG -> if (Messages.isEnglish()) "By Tag" else "按标签"
            STATUS -> if (Messages.isEnglish()) "By Status" else "按状态"
        }
    }

    init {
        // 创建树形结构
        treeModel = DefaultTreeModel(rootNode)
        tree = Tree(treeModel)
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = BookmarkTreeCellRenderer()

        // 双击跳转
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    navigateToSelectedBookmark()
                }
            }

            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showPopupMenu(e)
                }
            }

            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) {
                    showPopupMenu(e)
                }
            }
        })

        // 搜索功能
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: javax.swing.event.DocumentEvent) {
                refreshTree()
            }
        })

        // 布局
        val topPanel = JPanel(BorderLayout())

        // 搜索框
        val searchPanel = JPanel(BorderLayout())
        searchPanel.border = JBUI.Borders.empty(4)
        searchPanel.add(searchField, BorderLayout.CENTER)

        // 折叠方式选择 + 操作按钮
        val groupByPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        groupByPanel.border = JBUI.Borders.empty(0, 4, 4, 4)
        groupByCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? GroupBy)?.getDisplayName() ?: ""
                return this
            }
        }
        groupByCombo.addActionListener {
            groupBy = groupByCombo.selectedItem as GroupBy
            refreshTree()
        }
        groupByLabel.text = if (Messages.isEnglish()) "Group:" else "分组:"
        groupByPanel.add(groupByLabel)
        groupByPanel.add(groupByCombo)
        
        // 添加分隔符
        groupByPanel.add(JSeparator(JSeparator.VERTICAL).apply { 
            preferredSize = java.awt.Dimension(2, 20) 
        })
        
        // 跳转按钮
        val jumpButton = JButton(AllIcons.Actions.Play_forward).apply {
            toolTipText = if (Messages.isEnglish()) "Jump to selected bookmark" else "跳转到选中书签"
            preferredSize = java.awt.Dimension(28, 28)
            isFocusable = false
            addActionListener { navigateToSelectedBookmark() }
        }
        groupByPanel.add(jumpButton)
        
        // 编辑按钮
        val editButton = JButton(AllIcons.Actions.Edit).apply {
            toolTipText = if (Messages.isEnglish()) "Edit selected bookmark" else "编辑选中书签"
            preferredSize = java.awt.Dimension(28, 28)
            isFocusable = false
            addActionListener { editSelectedBookmark() }
        }
        groupByPanel.add(editButton)
        
        // 删除按钮
        val deleteButton = JButton(AllIcons.Actions.GC).apply {
            toolTipText = if (Messages.isEnglish()) "Delete selected bookmark" else "删除选中书签"
            preferredSize = java.awt.Dimension(28, 28)
            isFocusable = false
            addActionListener { deleteSelectedBookmark() }
        }
        groupByPanel.add(deleteButton)

        topPanel.add(searchPanel, BorderLayout.NORTH)
        topPanel.add(groupByPanel, BorderLayout.SOUTH)

        // 主内容
        val scrollPane = JBScrollPane(tree)
        val mainPanel = JPanel(BorderLayout())
        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        setContent(mainPanel)

        // 工具栏
        val toolbar = createToolbar()
        setToolbar(toolbar.component)

        // 监听书签变更
        bookmarkService.addChangeListener(object : BookmarkChangeListener {
            override fun onBookmarkAdded(bookmark: Bookmark) = refreshTree()
            override fun onBookmarkRemoved(bookmark: Bookmark) = refreshTree()
            override fun onBookmarkUpdated(bookmark: Bookmark) = refreshTree()
            override fun onBookmarksRefreshed() = refreshTree()
        })

        // 初始化树
        refreshTree()
        updateUITexts()
    }

    private fun updateUITexts() {
        searchField.textEditor.emptyText.text = Messages.searchPlaceholder
        groupByLabel.text = if (Messages.isEnglish()) "Group:" else "分组:"
        groupByCombo.repaint()
        refreshTree()
        // 更新 Tab 标题
        BookmarkToolWindowFactory.updateTabTitles()
    }

    private fun createToolbar(): ActionToolbar {
        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction(Messages.help, Messages.helpTip, AllIcons.Actions.Help) {
                override fun actionPerformed(e: AnActionEvent) {
                    showHelpDialog()
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.text = Messages.help
                }
            })

            add(object : AnAction(Messages.refresh, Messages.refresh, BookmarkPalaceIcons.Refresh) {
                override fun actionPerformed(e: AnActionEvent) {
                    bookmarkService.refreshBookmarks()
                    refreshTree()
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.text = Messages.refresh
                }
            })

            addSeparator()

            // 导览图
            add(object : AnAction(Messages.diagram, Messages.openDiagram, BookmarkPalaceIcons.Diagram) {
                override fun actionPerformed(e: AnActionEvent) {
                    DiagramEditorProvider.openDiagramSelector(project)
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.text = Messages.diagram
                }
            })

            // 语言切换
            add(object : AnAction(Messages.switchLanguage, "切换语言", BookmarkPalaceIcons.Language) {
                override fun actionPerformed(e: AnActionEvent) {
                    Messages.toggleLanguage()
                    updateUITexts()
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.text = Messages.switchLanguage
                }
            })

            addSeparator()

            // 导出
            add(object : AnAction(Messages.export, Messages.export, BookmarkPalaceIcons.Export) {
                override fun actionPerformed(e: AnActionEvent) {
                    ExportDialog(project).show()
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.text = Messages.export
                }
            })

            // 导入
            add(object : AnAction(Messages.import, Messages.import, BookmarkPalaceIcons.Import) {
                override fun actionPerformed(e: AnActionEvent) {
                    ImportDialog(project).show()
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.text = Messages.import
                }
            })

            addSeparator()

            add(object : AnAction("☕ 打赏", "请作者喝杯咖啡", BookmarkPalaceIcons.Donate) {
                override fun actionPerformed(e: AnActionEvent) {
                    DonateDialog(project).show()
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.text = if (Messages.isEnglish()) "☕ Donate" else "☕ 打赏"
                }
            })
        }

        return ActionManager.getInstance().createActionToolbar(
            ActionPlaces.TOOLWINDOW_CONTENT,
            actionGroup,
            true
        ).apply {
            targetComponent = this@BookmarkToolWindowPanel
        }
    }

    private fun refreshTree() {
        val query = searchField.text.lowercase()
        val bookmarks = bookmarkService.getAllBookmarks()
            .filter { bookmark ->
                query.isEmpty() ||
                bookmark.alias.lowercase().contains(query) ||
                bookmark.comment.lowercase().contains(query) ||
                bookmark.codeSnippet.lowercase().contains(query) ||
                bookmark.tags.any { it.lowercase().contains(query) } ||
                bookmark.getFileName().lowercase().contains(query)
            }

        rootNode.removeAllChildren()

        when (groupBy) {
            GroupBy.FILE -> groupByFile(bookmarks)
            GroupBy.COLOR -> groupByColor(bookmarks)
            GroupBy.TAG -> groupByTag(bookmarks)
            GroupBy.STATUS -> groupByStatus(bookmarks)
        }

        treeModel.reload()
        expandAllNodes()
    }

    private fun groupByFile(bookmarks: List<Bookmark>) {
        bookmarks.groupBy { it.filePath }.forEach { (filePath, fileBookmarks) ->
            val fileName = filePath.substringAfterLast("/")
            val fileNode = DefaultMutableTreeNode(GroupNode(fileName, filePath))
            fileBookmarks.sortedBy { it.startLine }.forEach { bookmark ->
                fileNode.add(DefaultMutableTreeNode(bookmark))
            }
            rootNode.add(fileNode)
        }
    }

    private fun groupByColor(bookmarks: List<Bookmark>) {
        BookmarkColor.values().forEach { color ->
            val colorBookmarks = bookmarks.filter { it.color == color }
            if (colorBookmarks.isNotEmpty()) {
                val colorNode = DefaultMutableTreeNode(GroupNode(color.displayName, color.name))
                colorBookmarks.forEach { bookmark ->
                    colorNode.add(DefaultMutableTreeNode(bookmark))
                }
                rootNode.add(colorNode)
            }
        }
    }

    private fun groupByTag(bookmarks: List<Bookmark>) {
        val allTags = bookmarks.flatMap { it.tags }.distinct()
        allTags.forEach { tag ->
            val tagBookmarks = bookmarks.filter { tag in it.tags }
            val tagNode = DefaultMutableTreeNode(GroupNode(tag, tag))
            tagBookmarks.forEach { bookmark ->
                tagNode.add(DefaultMutableTreeNode(bookmark))
            }
            rootNode.add(tagNode)
        }

        // 未标记的书签
        val untaggedBookmarks = bookmarks.filter { it.tags.isEmpty() }
        if (untaggedBookmarks.isNotEmpty()) {
            val untaggedNode = DefaultMutableTreeNode(GroupNode(if (Messages.isEnglish()) "Untagged" else "未标记", "untagged"))
            untaggedBookmarks.forEach { bookmark ->
                untaggedNode.add(DefaultMutableTreeNode(bookmark))
            }
            rootNode.add(untaggedNode)
        }
    }

    private fun groupByStatus(bookmarks: List<Bookmark>) {
        BookmarkStatus.values().forEach { status ->
            val statusBookmarks = bookmarks.filter { it.status == status }
            if (statusBookmarks.isNotEmpty()) {
                val statusName = when (status) {
                    BookmarkStatus.VALID -> "正常"
                    BookmarkStatus.MISSING -> "失效"
                    BookmarkStatus.OUTDATED -> "过期"
                }
                val statusNode = DefaultMutableTreeNode(GroupNode(statusName, status.name))
                statusBookmarks.forEach { bookmark ->
                    statusNode.add(DefaultMutableTreeNode(bookmark))
                }
                rootNode.add(statusNode)
            }
        }
    }

    private fun expandAllNodes() {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    private fun navigateToSelectedBookmark() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val bookmark = node.userObject as? Bookmark ?: return
        bookmarkService.navigateToBookmark(bookmark)
    }
    
    /**
     * 编辑选中的书签
     */
    private fun editSelectedBookmark() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val bookmark = node.userObject as? Bookmark
        if (bookmark == null) {
            // 如果选中的是分组节点，显示提示
            javax.swing.JOptionPane.showMessageDialog(
                this,
                if (Messages.isEnglish()) "Please select a bookmark first" else "请先选择一个书签",
                if (Messages.isEnglish()) "No Selection" else "未选择",
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        val dialog = EditBookmarkDialog(project, bookmark)
        if (dialog.showAndGet()) {
            bookmarkService.updateBookmark(bookmark)
        }
    }
    
    /**
     * 删除选中的书签
     */
    private fun deleteSelectedBookmark() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val bookmark = node.userObject as? Bookmark
        if (bookmark == null) {
            javax.swing.JOptionPane.showMessageDialog(
                this,
                if (Messages.isEnglish()) "Please select a bookmark first" else "请先选择一个书签",
                if (Messages.isEnglish()) "No Selection" else "未选择",
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        
        val confirm = javax.swing.JOptionPane.showConfirmDialog(
            this,
            if (Messages.isEnglish()) 
                "Delete bookmark \"${bookmark.getDisplayName()}\"?" 
            else 
                "确定删除书签 \"${bookmark.getDisplayName()}\" 吗？",
            if (Messages.isEnglish()) "Confirm Delete" else "确认删除",
            javax.swing.JOptionPane.YES_NO_OPTION
        )
        
        if (confirm == javax.swing.JOptionPane.YES_OPTION) {
            bookmarkService.removeBookmark(bookmark.id)
        }
    }

    private fun showPopupMenu(e: MouseEvent) {
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        tree.selectionPath = path

        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val bookmark = node.userObject as? Bookmark ?: return

        val popup = JPopupMenu()

        popup.add(JMenuItem(Messages.jumpTo).apply {
            addActionListener { bookmarkService.navigateToBookmark(bookmark) }
        })

        popup.addSeparator()

        popup.add(JMenuItem(Messages.edit).apply {
            addActionListener {
                val dialog = EditBookmarkDialog(project, bookmark)
                if (dialog.showAndGet()) {
                    bookmarkService.updateBookmark(bookmark)
                }
            }
        })

        // 颜色子菜单
        val colorMenu = JMenu(if (Messages.isEnglish()) "Change Color" else "修改颜色")
        BookmarkColor.values().forEach { color ->
            colorMenu.add(JMenuItem(color.displayName).apply {
                addActionListener {
                    bookmark.color = color
                    bookmarkService.updateBookmark(bookmark)
                }
            })
        }
        popup.add(colorMenu)

        popup.addSeparator()

        popup.add(JMenuItem(if (Messages.isEnglish()) "Add to Diagram" else "添加到导览图").apply {
            addActionListener {
                DiagramEditorProvider.addBookmarkToDiagram(project, bookmark)
            }
        })

        popup.addSeparator()

        popup.add(JMenuItem(Messages.delete).apply {
            addActionListener {
                bookmarkService.removeBookmark(bookmark.id)
            }
        })

        popup.show(tree, e.x, e.y)
    }

    /**
     * 显示使用说明对话框
     */
    private fun showHelpDialog() {
        val helpContent = if (Messages.isEnglish()) {
            """
            <html>
            <body style="font-family: sans-serif; padding: 10px; width: 450px;">
            <h2>🏰 BookmarkPalace User Guide</h2>
            
            <h3>📌 Add Bookmark</h3>
            <ul>
                <li><b>Shortcut:</b> <code>Ctrl+Shift+B</code> - Add bookmark with dialog</li>
                <li><b>Quick Add:</b> <code>Ctrl+Alt+B</code> - Quick add without dialog</li>
                <li><b>Right-click</b> on code → "Add Bookmark"</li>
            </ul>
            
            <h3>🔍 Navigate</h3>
            <ul>
                <li><b>Double-click</b> bookmark in list to jump to code</li>
                <li>Use <b>search box</b> to filter bookmarks</li>
                <li>Use <b>Group</b> dropdown to organize by file/color/tag/status</li>
            </ul>
            
            <h3>🗺️ Diagram</h3>
            <ul>
                <li>Click <b>Diagram</b> button to open diagram manager</li>
                <li><b>Edit Mode:</b> Double-click bookmark in sidebar to add to canvas</li>
                <li><b>View Mode:</b> Click node link to jump to code</li>
                <li><b>Split View:</b> Right-click tab → "Split Right" for side-by-side view</li>
            </ul>
            
            <h3>📤 Import/Export</h3>
            <ul>
                <li>Supports <b>JSON</b>, <b>Markdown</b>, <b>Mermaid</b> formats</li>
                <li>Share bookmarks with team members</li>
            </ul>
            
            <h3>💡 Tips</h3>
            <ul>
                <li>Bookmarks auto-track code position changes</li>
                <li>Use <b>colors</b> and <b>tags</b> to categorize bookmarks</li>
                <li>Right-click bookmark for more options</li>
            </ul>
            </body>
            </html>
            """.trimIndent()
        } else {
            """
            <html>
            <body style="font-family: sans-serif; padding: 10px; width: 450px;">
            <h2>🏰 书签宫殿使用说明</h2>
            
            <h3>📌 添加书签</h3>
            <ul>
                <li><b>快捷键：</b><code>Ctrl+Shift+B</code> - 添加书签（弹出对话框）</li>
                <li><b>快速添加：</b><code>Ctrl+Alt+B</code> - 快速添加（无对话框）</li>
                <li>在代码上<b>右键</b> → "添加书签"</li>
            </ul>
            
            <h3>🔍 导航跳转</h3>
            <ul>
                <li><b>双击</b>列表中的书签即可跳转到代码位置</li>
                <li>使用<b>搜索框</b>过滤书签</li>
                <li>使用<b>分组</b>下拉框按文件/颜色/标签/状态组织</li>
            </ul>
            
            <h3>🗺️ 导览图</h3>
            <ul>
                <li>点击<b>导览图</b>按钮打开导览图管理</li>
                <li><b>编辑模式：</b>双击左侧书签添加到画布</li>
                <li><b>查看模式：</b>点击节点链接跳转代码</li>
                <li><b>分栏查看：</b>右键标签页 → "Split Right" 可左右分栏同时看图和代码</li>
            </ul>
            
            <h3>📤 导入导出</h3>
            <ul>
                <li>支持 <b>JSON</b>、<b>Markdown</b>、<b>Mermaid</b> 格式</li>
                <li>可与团队成员共享书签</li>
            </ul>
            
            <h3>💡 使用技巧</h3>
            <ul>
                <li>书签会自动跟踪代码位置变化</li>
                <li>使用<b>颜色</b>和<b>标签</b>分类管理书签</li>
                <li>右键书签可进行更多操作</li>
            </ul>
            </body>
            </html>
            """.trimIndent()
        }
        
        val label = JLabel(helpContent)
        label.border = JBUI.Borders.empty(10)
        
        val scrollPane = JBScrollPane(label)
        scrollPane.preferredSize = java.awt.Dimension(500, 450)
        scrollPane.border = null
        
        JOptionPane.showMessageDialog(
            null,
            scrollPane,
            if (Messages.isEnglish()) "BookmarkPalace Help" else "书签宫殿使用说明",
            JOptionPane.INFORMATION_MESSAGE
        )
    }

    /**
     * 聚焦到指定书签（用于 Gutter 图标点击）
     */
    fun focusBookmark(bookmark: Bookmark) {
        // 刷新树形结构确保书签存在
        refreshTree()
        
        // 遍历树节点找到对应的书签
        val root = treeModel.root as DefaultMutableTreeNode
        var bookmarkNode: DefaultMutableTreeNode? = null
        
        for (i in 0 until root.childCount) {
            val groupNode = root.getChildAt(i) as DefaultMutableTreeNode
            for (j in 0 until groupNode.childCount) {
                val node = groupNode.getChildAt(j) as DefaultMutableTreeNode
                if (node.userObject is Bookmark && (node.userObject as Bookmark).id == bookmark.id) {
                    bookmarkNode = node
                    break
                }
            }
            if (bookmarkNode != null) break
        }
        
        // 如果找到节点，选中并滚动到可见区域
        if (bookmarkNode != null) {
            val path = javax.swing.tree.TreePath(treeModel.getPathToRoot(bookmarkNode))
            tree.selectionPath = path
            tree.scrollPathToVisible(path)
        }
    }

    /**
     * 分组节点数据类
     */
    data class GroupNode(val name: String, val key: String)

    /**
     * 树节点渲染器
     */
    inner class BookmarkTreeCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree?,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

            val node = value as? DefaultMutableTreeNode ?: return this
            val userObject = node.userObject

            when (userObject) {
                is Bookmark -> {
                    // 书签节点
                    val colorIcon = getColorIcon(userObject.color)
                    val statusIcon = when (userObject.status) {
                        BookmarkStatus.VALID -> ""
                        BookmarkStatus.MISSING -> " ✖"
                        BookmarkStatus.OUTDATED -> " ⚠"
                    }
                    val tags = if (userObject.tags.isNotEmpty()) " [${userObject.tags.joinToString(",")}]" else ""

                    text = "$colorIcon ${userObject.getDisplayName()} (${userObject.getLocationDescription()})$tags$statusIcon"

                    if (userObject.status == BookmarkStatus.MISSING) {
                        foreground = java.awt.Color.RED
                    }

                    icon = AllIcons.Nodes.Bookmark
                    toolTipText = buildToolTip(userObject)
                }
                is GroupNode -> {
                    // 分组节点
                    text = "${userObject.name} (${node.childCount})"
                    icon = AllIcons.Nodes.Folder
                }
            }

            return this
        }

        private fun getColorIcon(color: BookmarkColor): String {
            return when (color) {
                BookmarkColor.RED -> "🔴"
                BookmarkColor.ORANGE -> "🟠"
                BookmarkColor.YELLOW -> "🟡"
                BookmarkColor.GREEN -> "🟢"
                BookmarkColor.BLUE -> "🔵"
                BookmarkColor.PURPLE -> "🟣"
                BookmarkColor.PINK -> "💗"
                BookmarkColor.CYAN -> "🔷"
                BookmarkColor.GRAY -> "⚪"
            }
        }

        private fun buildToolTip(bookmark: Bookmark): String {
            return buildString {
                append("<html>")
                append("<b>${bookmark.alias}</b><br>")
                append("文件: ${bookmark.filePath}<br>")
                append("行号: ${bookmark.startLine + 1}<br>")
                if (bookmark.comment.isNotEmpty()) {
                    append("注释: ${bookmark.comment}<br>")
                }
                if (bookmark.tags.isNotEmpty()) {
                    append("标签: ${bookmark.tags.joinToString(", ")}<br>")
                }
                append("<hr>")
                append("<pre>${bookmark.codeSnippet.take(200)}</pre>")
                append("</html>")
            }
        }
    }
}
