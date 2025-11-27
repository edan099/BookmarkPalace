package com.longlong.bookmark.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.*
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.longlong.bookmark.model.Bookmark
import com.longlong.bookmark.model.BookmarkColor
import com.longlong.bookmark.model.BookmarkStatus
import com.longlong.bookmark.service.BookmarkChangeListener
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.ui.dialog.AddBookmarkDialog
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
    private val rootNode = DefaultMutableTreeNode("书签")

    // 折叠方式
    private var groupBy: GroupBy = GroupBy.FILE

    enum class GroupBy(val displayName: String) {
        FILE("按文件"),
        COLOR("按颜色"),
        TAG("按标签"),
        STATUS("按状态")
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

        // 折叠方式选择
        val groupByPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        groupByPanel.border = JBUI.Borders.empty(0, 4, 4, 4)
        val groupByCombo = JComboBox(GroupBy.entries.toTypedArray())
        groupByCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                text = (value as? GroupBy)?.displayName ?: ""
                return this
            }
        }
        groupByCombo.addActionListener {
            groupBy = groupByCombo.selectedItem as GroupBy
            refreshTree()
        }
        groupByPanel.add(JLabel("分组:"))
        groupByPanel.add(groupByCombo)

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
    }

    private fun createToolbar(): ActionToolbar {
        val actionGroup = DefaultActionGroup().apply {
            add(object : AnAction("添加书签", "在编辑器中添加书签", AllIcons.General.Add) {
                override fun actionPerformed(e: AnActionEvent) {
                    val actionEvent = AnActionEvent.createFromDataContext(
                        ActionPlaces.TOOLWINDOW_CONTENT,
                        null,
                        DataContext { dataId ->
                            when (dataId) {
                                CommonDataKeys.PROJECT.name -> project
                                else -> null
                            }
                        }
                    )
                    ActionManager.getInstance().getAction("LongLongBookmark.AddBookmark")?.actionPerformed(actionEvent)
                }
            })

            add(object : AnAction("刷新", "刷新书签列表", AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) {
                    bookmarkService.refreshBookmarks()
                    refreshTree()
                }
            })

            addSeparator()

            add(object : AnAction("导出", "导出书签", AllIcons.ToolbarDecorator.Export) {
                override fun actionPerformed(e: AnActionEvent) {
                    ExportDialog(project).show()
                }
            })

            add(object : AnAction("导入", "导入书签", AllIcons.ToolbarDecorator.Import) {
                override fun actionPerformed(e: AnActionEvent) {
                    ImportDialog(project).show()
                }
            })

            addSeparator()

            add(object : AnAction("导览图", "打开导览图", AllIcons.FileTypes.Diagram) {
                override fun actionPerformed(e: AnActionEvent) {
                    DiagramEditorProvider.openDiagramSelector(project)
                }
            })
        }

        return ActionManager.getInstance().createActionToolbar(
            ActionPlaces.TOOLWINDOW_CONTENT,
            actionGroup,
            true
        )
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
        BookmarkColor.entries.forEach { color ->
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
            val untaggedNode = DefaultMutableTreeNode(GroupNode("未标记", "untagged"))
            untaggedBookmarks.forEach { bookmark ->
                untaggedNode.add(DefaultMutableTreeNode(bookmark))
            }
            rootNode.add(untaggedNode)
        }
    }

    private fun groupByStatus(bookmarks: List<Bookmark>) {
        BookmarkStatus.entries.forEach { status ->
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

    private fun showPopupMenu(e: MouseEvent) {
        val path = tree.getPathForLocation(e.x, e.y) ?: return
        tree.selectionPath = path

        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val bookmark = node.userObject as? Bookmark ?: return

        val popup = JPopupMenu()

        popup.add(JMenuItem("跳转").apply {
            addActionListener { bookmarkService.navigateToBookmark(bookmark) }
        })

        popup.addSeparator()

        popup.add(JMenuItem("编辑").apply {
            addActionListener {
                val dialog = EditBookmarkDialog(project, bookmark)
                if (dialog.showAndGet()) {
                    bookmarkService.updateBookmark(bookmark)
                }
            }
        })

        // 颜色子菜单
        val colorMenu = JMenu("修改颜色")
        BookmarkColor.entries.forEach { color ->
            colorMenu.add(JMenuItem(color.displayName).apply {
                addActionListener {
                    bookmark.color = color
                    bookmarkService.updateBookmark(bookmark)
                }
            })
        }
        popup.add(colorMenu)

        popup.addSeparator()

        popup.add(JMenuItem("添加到导览图").apply {
            addActionListener {
                DiagramEditorProvider.addBookmarkToDiagram(project, bookmark)
            }
        })

        popup.addSeparator()

        popup.add(JMenuItem("删除").apply {
            addActionListener {
                bookmarkService.removeBookmark(bookmark.id)
            }
        })

        popup.show(tree, e.x, e.y)
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
                    val statusIcon = when (userObject.status) {
                        BookmarkStatus.VALID -> "●"
                        BookmarkStatus.MISSING -> "✖"
                        BookmarkStatus.OUTDATED -> "⚠"
                    }
                    val colorIcon = getColorIcon(userObject.color)
                    val tags = if (userObject.tags.isNotEmpty()) " [${userObject.tags.joinToString(",")}]" else ""

                    text = "$colorIcon ${userObject.getDisplayName()} (${userObject.getLocationDescription()})$tags"

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
