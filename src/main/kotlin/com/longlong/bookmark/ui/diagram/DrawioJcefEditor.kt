package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.model.*
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.service.DiagramService
import com.google.gson.Gson
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.*
import javax.swing.DefaultListModel
import javax.swing.ListSelectionModel
import javax.swing.ListCellRenderer
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.network.CefRequest
import org.cef.callback.CefCallback
import org.cef.misc.BoolRef
import com.intellij.ide.BrowserUtil
import java.io.File
import java.nio.file.Files

/**
 * 基于 jCEF 的 Draw.io 编辑器
 * @param viewOnly 是否为只读查看模式（隐藏编辑按钮，点击节点可跳转代码）
 */
class DrawioJcefEditor(
    private val project: Project,
    private val file: VirtualFile,
    private val viewOnly: Boolean = false
) : UserDataHolderBase(), FileEditor {

    private val logger = Logger.getInstance(DrawioJcefEditor::class.java)
    private val diagramService = DiagramService.getInstance(project)
    private val bookmarkService = BookmarkService.getInstance(project)
    private val diagramId = file.nameWithoutExtension
    
    // 缓存 diagram 引用，避免每次访问都查找/创建
    private val diagram: Diagram by lazy {
        diagramService.getDiagram(diagramId) ?: createAndRegisterDiagram()
    }
    
    private val mainPanel = JPanel(BorderLayout())
    private val browser: JBCefBrowser = JBCefBrowser()
    private val gson = Gson()
    
    // 书签面板和分割组件
    private lateinit var splitPane: JSplitPane
    private lateinit var bookmarkPanel: JPanel
    private var bookmarkPanelVisible = true
    private var lastDividerLocation = 250
    
    // JavaScript Bridge 用于双向通信
    private val jsQuery = JBCefJSQuery.create(browser)
    
    // 待插入的书签（用于异步处理）
    private var pendingBookmark: Bookmark? = null
    // 等待插入书签的操作标记
    private var waitingForInsertExport = false
    // 等待跳转的操作标记
    private var waitingForJumpExport = false
    // 等待保存并切换的操作标记
    private var waitingForSaveAndSwitch = false
    // 缓存当前画布的 XML（通过 autosave 更新）
    private var currentCanvasXml: String? = null
    
    // 修改跟踪
    private var modified = false
    private val propertyChangeListeners = mutableListOf<PropertyChangeListener>()
    
    init {
        setupUI()
        setupJavaScriptBridge()
        setupLinkInterceptor()
        setupKeyBindings()
        loadDrawio()
    }
    
    private fun setupKeyBindings() {
        // Command+S (Mac) / Ctrl+S (Win/Linux) 保存
        val saveAction = object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) {
                save()
            }
        }
        // 注册到多个层级确保能捕获
        mainPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx),
            "saveDiagram"
        )
        mainPanel.actionMap.put("saveDiagram", saveAction)
        
        // 也注册到 browser component
        browser.component.getInputMap(JComponent.WHEN_FOCUSED).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_S, Toolkit.getDefaultToolkit().menuShortcutKeyMaskEx),
            "saveDiagram"
        )
        browser.component.actionMap.put("saveDiagram", saveAction)
    }
    
    /**
     * 保存导览图 - public 方法供外部调用
     */
    fun save() {
        saveDiagram()
    }
    
    private fun setModified(value: Boolean) {
        if (modified != value) {
            modified = value
            propertyChangeListeners.forEach {
                it.propertyChange(java.beans.PropertyChangeEvent(
                    this, FileEditor.PROP_MODIFIED, !value, value
                ))
            }
        }
    }
    
    /**
     * 设置链接拦截器，捕获 bookmark:// 协议
     */
    private fun setupLinkInterceptor() {
        browser.jbCefClient.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun getResourceRequestHandler(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
                isNavigation: Boolean,
                isDownload: Boolean,
                requestInitiator: String?,
                disableDefaultHandling: BoolRef?
            ): CefResourceRequestHandler? {
                val url = request?.url ?: return null
                
                // 拦截 bookmark:// 协议
                if (url.startsWith("bookmark://")) {
                    logger.debug("🔗 Intercepted bookmark link: $url")
                    disableDefaultHandling?.set(true)
                    
                    // 链接格式：bookmark://短ID/完整ID 或 bookmark://完整ID
                    val bookmarkId = extractBookmarkId(url)
                    // 在 UI 线程中执行跳转
                    ApplicationManager.getApplication().invokeLater {
                        navigateToBookmark(bookmarkId)
                    }
                    
                    return object : CefResourceRequestHandlerAdapter() {
                        override fun onBeforeResourceLoad(
                            browser: CefBrowser?,
                            frame: CefFrame?,
                            request: CefRequest?
                        ): Boolean {
                            return true // 取消请求
                        }
                    }
                }
                return null
            }
        }, browser.cefBrowser)
        
        // 拦截 popup 窗口（防止打开空白窗口）
        browser.jbCefClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(
                browser: CefBrowser?,
                frame: CefFrame?,
                targetUrl: String?,
                targetFrameName: String?
            ): Boolean {
                logger.debug("🔗 Popup intercepted: $targetUrl")
                
                // 如果是 bookmark:// 协议，拦截并跳转
                if (targetUrl?.startsWith("bookmark://") == true) {
                    val bookmarkId = extractBookmarkId(targetUrl)
                    ApplicationManager.getApplication().invokeLater {
                        navigateToBookmark(bookmarkId)
                    }
                    return true // 阻止 popup
                }
                
                return true // 阻止所有 popup
            }
        }, browser.cefBrowser)
    }
    
    /**
     * 从链接中提取书签 ID
     * 支持格式：bookmark://短ID/完整ID 或 bookmark://完整ID
     */
    private fun extractBookmarkId(url: String): String {
        val path = url.removePrefix("bookmark://")
        // 如果包含 /，取最后一部分（完整ID）
        return if (path.contains("/")) {
            path.substringAfterLast("/")
        } else {
            path
        }
    }

    /**
     * 创建并注册图表，确保 ID 与文件名一致
     */
    private fun createAndRegisterDiagram(): Diagram {
        logger.debug("📊 Creating new diagram with id: $diagramId")
        return diagramService.createDiagram(
            name = diagramId,
            type = DiagramType.CUSTOM_FLOW,
            description = "",
            id = diagramId  // 使用文件名作为 ID，确保后续能找到
        )
    }

    private fun setupUI() {
        mainPanel.add(createToolbar(), BorderLayout.NORTH)
        
        if (viewOnly) {
            // 查看模式：只显示浏览器，不显示书签面板
            mainPanel.add(browser.component, BorderLayout.CENTER)
        } else {
            // 编辑模式：创建书签面板
            bookmarkPanel = createBookmarkPanel()
            
            // 创建主内容区域：左侧书签面板 + 右侧 Draw.io 编辑器
            splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
                leftComponent = bookmarkPanel
                rightComponent = browser.component
                dividerLocation = 250
                dividerSize = 5
                isContinuousLayout = true
            }
            mainPanel.add(splitPane, BorderLayout.CENTER)
        }
    }
    
    /**
     * 切换书签面板显示/隐藏
     */
    private fun toggleBookmarkPanel() {
        if (bookmarkPanelVisible) {
            lastDividerLocation = splitPane.dividerLocation
            splitPane.dividerLocation = 0
            bookmarkPanel.isVisible = false
        } else {
            bookmarkPanel.isVisible = true
            splitPane.dividerLocation = lastDividerLocation
        }
        bookmarkPanelVisible = !bookmarkPanelVisible
    }
    
    /**
     * 创建书签面板（支持搜索和双击添加）
     */
    private fun createBookmarkPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = java.awt.Dimension(250, 0)
        panel.border = BorderFactory.createTitledBorder("📚 书签列表")
        
        // 搜索框
        val searchField = JTextField().apply {
            toolTipText = "搜索书签..."
        }
        panel.add(searchField, BorderLayout.NORTH)
        
        // 书签列表
        val listModel = DefaultListModel<BookmarkListItem>()
        val bookmarkList = JList(listModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = BookmarkListCellRenderer()
        }
        
        // 双击添加书签节点
        bookmarkList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = bookmarkList.selectedValue
                    if (selected != null) {
                        insertBookmarkNode(selected.bookmark)
                    }
                }
            }
        })
        
        val scrollPane = JScrollPane(bookmarkList)
        panel.add(scrollPane, BorderLayout.CENTER)
        
        // 加载书签列表
        fun loadBookmarks(filter: String = "") {
            listModel.clear()
            val allBookmarks = bookmarkService.getAllBookmarks()
            val filtered = if (filter.isBlank()) allBookmarks else {
                allBookmarks.filter { bm ->
                    bm.getDisplayName().contains(filter, ignoreCase = true) ||
                    bm.getFileName().contains(filter, ignoreCase = true) ||
                    bm.tags.any { it.contains(filter, ignoreCase = true) }
                }
            }
            filtered.forEach { listModel.addElement(BookmarkListItem(it)) }
        }
        
        // 搜索过滤
        searchField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = loadBookmarks(searchField.text)
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = loadBookmarks(searchField.text)
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) = loadBookmarks(searchField.text)
        })
        
        // 刷新按钮
        val refreshButton = JButton("↻ 刷新").apply {
            addActionListener { loadBookmarks(searchField.text) }
        }
        panel.add(refreshButton, BorderLayout.SOUTH)
        
        // 初始加载
        loadBookmarks()
        
        return panel
    }
    
    /**
     * 书签列表项
     */
    private data class BookmarkListItem(val bookmark: Bookmark) {
        override fun toString(): String = "${bookmark.getDisplayName()} (${bookmark.getFileName()}:${bookmark.startLine + 1})"
    }
    
    /**
     * 书签列表渲染器
     */
    private inner class BookmarkListCellRenderer : ListCellRenderer<BookmarkListItem> {
        private val label = JLabel()
        
        override fun getListCellRendererComponent(
            list: JList<out BookmarkListItem>?,
            value: BookmarkListItem?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            val bm = value?.bookmark
            label.text = "<html><b>${bm?.getDisplayName() ?: ""}</b><br><small>${bm?.getFileName()}:${(bm?.startLine ?: 0) + 1}</small></html>"
            label.icon = null
            label.border = BorderFactory.createEmptyBorder(4, 8, 4, 8)
            
            if (isSelected) {
                label.background = list?.selectionBackground
                label.foreground = list?.selectionForeground
                label.isOpaque = true
            } else {
                label.background = list?.background
                label.foreground = list?.foreground
                label.isOpaque = false
            }
            
            return label
        }
    }

    private fun createToolbar(): JPanel {
        // 使用自定义 WrapLayout 实现自动换行
        val toolbar = JPanel(WrapLayout(java.awt.FlowLayout.LEFT, 4, 2))
        
        if (viewOnly) {
            // 查看模式：简洁工具栏
            toolbar.add(JLabel("👁 ${Messages.viewMode}").apply {
                foreground = java.awt.Color(0, 120, 215)
            })
            
            // 刷新按钮 - 重新加载 Draw.io
            toolbar.add(JButton("🔄").apply {
                toolTipText = if (Messages.isEnglish()) "Refresh Draw.io" else "刷新 Draw.io"
                addActionListener { refreshDrawio() }
            })
            
            // 编辑/导出按钮
            toolbar.add(JButton("✏️ ${Messages.editMode}").apply {
                toolTipText = Messages.switchToEditMode
                addActionListener { switchToEditMode() }
            })
            toolbar.add(JButton("🌐 ${if (Messages.isEnglish()) "Open in Browser" else "外部浏览器"}").apply {
                toolTipText = Messages.openInBrowserTip
                addActionListener { openInExternalBrowser() }
            })
            toolbar.add(JButton("↻ ${if (Messages.isEnglish()) "Sync" else "同步"}").apply {
                toolTipText = Messages.syncFromBrowserTip
                addActionListener { syncFromBrowser() }
            })
            toolbar.add(JButton("PNG").apply {
                toolTipText = "${Messages.export} PNG"
                addActionListener { exportAsPng() }
            })
            toolbar.add(JButton("SVG").apply {
                toolTipText = "${Messages.export} SVG"
                addActionListener { exportAsSvg() }
            })
            
            // 提示：Draw.io 内部支持拖动和缩放
            toolbar.add(JLabel("📌 ${Messages.clickNodeToJump} | Ctrl+滚轮缩放, 中键拖动").apply {
                foreground = java.awt.Color(100, 100, 100)
                font = font.deriveFont(11f)
            })
        } else {
            // 编辑模式：完整工具栏
            toolbar.add(JButton(Messages.collapseBookmarks).apply {
                toolTipText = Messages.toggleBookmarksTip
                addActionListener {
                    toggleBookmarkPanel()
                    text = if (bookmarkPanelVisible) Messages.collapseBookmarks else Messages.expandBookmarks
                }
            })
            
            toolbar.add(JButton(Messages.save).apply {
                addActionListener { saveDiagram() }
            })
            toolbar.add(JButton(Messages.saveAndView).apply {
                toolTipText = Messages.saveAndViewTip
                addActionListener { saveAndSwitchToViewMode() }
            })
            toolbar.add(JButton("${Messages.export} PNG").apply {
                addActionListener { exportAsPng() }
            })
            toolbar.add(JButton("${Messages.export} SVG").apply {
                addActionListener { exportAsSvg() }
            })
            
            toolbar.add(JLabel("📌 ${Messages.clickNodeToJump}").apply {
                foreground = java.awt.Color(0, 120, 215)
            })
            
            // 浏览器编辑
            toolbar.add(JButton("🌐 ${Messages.openInBrowser}").apply {
                toolTipText = Messages.openInBrowserTip
                addActionListener { openInExternalBrowser() }
            })
            toolbar.add(JButton("↻ ${Messages.syncFromBrowser}").apply {
                toolTipText = Messages.syncFromBrowserTip
                addActionListener { syncFromBrowser() }
            })
        }
        
        return toolbar
    }
    
    /**
     * 显示书签选择器
     */
    private fun showBookmarkSelector() {
        val bookmarkService = BookmarkService.getInstance(project)
        val allBookmarks = bookmarkService.getAllBookmarks()
        
        if (allBookmarks.isEmpty()) {
            javax.swing.JOptionPane.showMessageDialog(
                mainPanel,
                "没有可用的书签，请先添加书签",
                "提示",
                javax.swing.JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        
        // 创建书签列表
        val bookmarkNames = allBookmarks.map { "${it.getDisplayName()} (${it.getFileName()}:${it.startLine + 1})" }.toTypedArray()
        val selected = javax.swing.JOptionPane.showInputDialog(
            mainPanel,
            "选择要插入的书签：",
            "插入书签",
            javax.swing.JOptionPane.QUESTION_MESSAGE,
            null,
            bookmarkNames,
            bookmarkNames.firstOrNull()
        )
        
        if (selected != null) {
            val index = bookmarkNames.indexOf(selected)
            if (index >= 0) {
                insertBookmarkNode(allBookmarks[index])
            }
        }
    }
    
    /**
     * 插入书签节点到 Draw.io
     * 
     * Draw.io embed 模式下无法直接插入节点，需要：
     * 1. 先强制触发导出获取最新 XML
     * 2. 在 Kotlin 端修改 XML 添加节点
     * 3. 重新加载修改后的 XML
     */
    private fun insertBookmarkNode(bookmark: Bookmark) {
        logger.debug("📌 Preparing to insert bookmark: ${bookmark.getDisplayName()}")
        logger.debug("📌 Current autosave cache length: ${currentCanvasXml?.length ?: 0}")
        
        // 保存待插入的书签，设置标记
        pendingBookmark = bookmark
        waitingForInsertExport = true
        
        // 显示状态
        executeJS("status.textContent = '正在获取画布内容...'; status.style.display = 'block'; status.style.background = '#2196f3';")
        
        // 请求导出当前画布内容
        logger.debug("📌 Requesting export...")
        executeJS("""
            console.log('📤 Requesting XML export for bookmark insertion...');
            console.log('📤 Sending export request now...');
            iframe.contentWindow.postMessage(JSON.stringify({
                action: 'export',
                format: 'xml'
            }), '*');
        """.trimIndent())
    }
    
    /**
     * 跳转到图表中的书签节点对应的代码
     * 改为从图表 XML 中解析所有书签，让用户选择
     */
    private fun jumpToSelectedBookmark() {
        logger.debug("🚀 Requesting diagram XML for bookmark list...")
        waitingForJumpExport = true
        
        // 导出整个图表 XML
        executeJS("""
            console.log('🚀 Requesting XML for jump...');
            iframe.contentWindow.postMessage(JSON.stringify({
                action: 'export',
                format: 'xml'
            }), '*');
        """.trimIndent())
    }
    
    /**
     * 从导出的 XML 中提取所有书签 ID 并让用户选择跳转
     */
    private fun extractBookmarkAndJump(xml: String) {
        logger.debug("🔍 Extracting bookmarks from XML...")
        
        // 查找所有 link="bookmark://短ID/完整ID" 或 "bookmark://完整ID" 模式
        val linkPattern = Regex("""link="bookmark://([^"]+)"""")
        val matches = linkPattern.findAll(xml).toList()
            .map { extractBookmarkId("bookmark://" + it.groupValues[1]) }
            .distinct()
        
        if (matches.isEmpty()) {
            logger.debug("❌ No bookmark links found in diagram")
            ApplicationManager.getApplication().invokeLater {
                javax.swing.JOptionPane.showMessageDialog(
                    mainPanel,
                    "图表中没有书签节点，请先插入书签",
                    "无书签",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE
                )
            }
            return
        }
        
        // matches 已经是书签 ID 列表
        val bookmarks = matches.mapNotNull { id -> 
            bookmarkService.getBookmark(id)?.let { id to it }
        }
        
        if (bookmarks.isEmpty()) {
            logger.debug("❌ Bookmarks not found in service")
            ApplicationManager.getApplication().invokeLater {
                javax.swing.JOptionPane.showMessageDialog(
                    mainPanel,
                    "书签已被删除，无法跳转",
                    "书签不存在",
                    javax.swing.JOptionPane.WARNING_MESSAGE
                )
            }
            return
        }
        
        ApplicationManager.getApplication().invokeLater {
            if (bookmarks.size == 1) {
                // 只有一个书签，直接跳转
                navigateToBookmark(bookmarks[0].first)
            } else {
                // 多个书签，让用户选择
                val options = bookmarks.map { (_, bm) -> 
                    "${bm.getDisplayName()} (${bm.getFileName()}:${bm.startLine + 1})"
                }.toTypedArray()
                
                val selected = javax.swing.JOptionPane.showInputDialog(
                    mainPanel,
                    "选择要跳转的书签：",
                    "跳转到书签",
                    javax.swing.JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options.firstOrNull()
                )
                
                if (selected != null) {
                    val index = options.indexOf(selected)
                    if (index >= 0) {
                        navigateToBookmark(bookmarks[index].first)
                    }
                }
            }
        }
    }
    
    /**
     * 将书签节点插入到 XML 中
     */
    private fun insertNodeIntoXml(currentXml: String, bookmark: Bookmark) {
        try {
            logger.debug("📌 Inserting bookmark node into XML...")
            
            // 先转义特殊字符，再添加换行符实体（&#10; 是 Draw.io XML 格式的换行）
            val displayName = escapeXml(bookmark.getDisplayName())
            val fileName = escapeXml(bookmark.getFileName())
            val escapedLabel = "$displayName&#10;$fileName:${bookmark.startLine + 1}"
            
            // 生成唯一 ID（基于时间戳）
            val nodeId = "bookmark_${System.currentTimeMillis()}"
            
            // 计算节点位置（随机偏移避免重叠）
            val x = 100 + (Math.random() * 200).toInt()
            val y = 100 + (Math.random() * 200).toInt()
            
            // 创建带链接的节点 XML（UserObject 包装使链接生效，双击可跳转到代码）
            // 链接格式：bookmark://别名/完整ID（别名用于显示，完整ID用于跳转）
            val linkDisplayName = bookmark.getDisplayName().take(20).replace(" ", "_")
            val bookmarkLink = "bookmark://$linkDisplayName/${bookmark.id}"
            val tooltip = escapeXml("点击跳转: ${bookmark.getDisplayName()}")
            val nodeWithLink = """<UserObject label="$escapedLabel" link="$bookmarkLink" tooltip="$tooltip" id="$nodeId"><mxCell style="rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;fontColor=#333333;fontSize=12;" vertex="1" parent="1"><mxGeometry x="$x" y="$y" width="180" height="60" as="geometry"/></mxCell></UserObject>"""
            
            // 在 </root> 之前插入新节点（使用带链接的版本）
            val modifiedXml = if (currentXml.contains("</root>")) {
                currentXml.replace("</root>", "$nodeWithLink</root>")
            } else {
                // 如果 XML 格式不正确，创建新的
                """<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/>$nodeWithLink</root></mxGraphModel>"""
            }
            
            logger.debug("📌 Modified XML length: ${modifiedXml.length}")
            
            // 更新缓存
            currentCanvasXml = modifiedXml
            
            // 重新加载修改后的 XML
            ApplicationManager.getApplication().invokeLater {
                val escapedXml = escapeJS(modifiedXml)
                executeJS("window.loadDiagram('$escapedXml');")
                executeJS("status.textContent = '✅ 书签已插入'; status.style.display = 'block'; status.style.background = '#4caf50'; setTimeout(() => status.style.display = 'none', 2000);")
                logger.debug("✅ Bookmark node inserted successfully!")
            }
            
        } catch (e: Exception) {
            logger.error("Failed to insert bookmark node", e)
            logger.debug("❌ Failed to insert bookmark: ${e.message}")
        }
    }

    /**
     * 设置 JavaScript Bridge - 实现 Java 与 JS 双向通信
     */
    private fun setupJavaScriptBridge() {
        // Java -> JavaScript: 接收来自 Draw.io 的消息
        jsQuery.addHandler { msg ->
            try {
                logger.info("Received from Draw.io: $msg")
                handleDrawioMessage(msg)
                null // 返回 null 表示成功
            } catch (e: Exception) {
                logger.error("Error handling Draw.io message", e)
                JBCefJSQuery.Response(null, 0, e.message ?: "Unknown error")
            }
        }

        // 监听页面加载完成
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    logger.info("✅ Main page loaded successfully")
                    logger.debug("✅ Main page loaded, waiting for Draw.io to initialize...")
                }
            }
        }, browser.cefBrowser)
    }

    /**
     * 加载 Draw.io Embed
     */
    private fun loadDrawio() {
        val drawioHtml = generateDrawioHtml()
        browser.loadHTML(drawioHtml)
    }

    /**
     * 生成 Draw.io Embed HTML
     * 在查看模式下使用 viewer 参数禁用编辑
     */
    private fun generateDrawioHtml(): String {
        // 根据插件语言设置 Draw.io 语言
        val drawioLang = if (Messages.isEnglish()) "en" else "zh"
        
        // 查看模式使用不同的 URL 参数
        val drawioUrl = if (viewOnly) {
            // 查看模式：使用 chromeless + lightbox 实现真正的只读模式
            // chrome=0 启用 chromeless 只读查看器
            // lightbox=1 使用 lightbox 模式
            // nav=1 启用导航（可折叠/展开）
            // layers=1 启用图层控制
            "https://embed.diagrams.net/?embed=1&chrome=0&lightbox=1&nav=1&layers=1&spin=1&proto=json&lang=$drawioLang&configure=1"
        } else {
            // 编辑模式：完整编辑功能
            "https://embed.diagrams.net/?embed=1&ui=atlas&spin=1&proto=json&saveAndExit=1&noSaveBtn=1&lang=$drawioLang&configure=1"
        }
        val modeLabel = if (viewOnly) "查看模式" else "编辑模式"
        
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Draw.io ${if (viewOnly) "Viewer" else "Editor"}</title>
    <style>
        body { margin: 0; padding: 0; overflow: hidden; }
        #drawio-frame { width: 100%; height: 100vh; border: none; }
    </style>
</head>
<body>
    <div id="status" style="position: absolute; top: 10px; left: 10px; background: #333; color: #fff; padding: 5px 10px; border-radius: 3px; z-index: 9999;">正在加载 Draw.io ($modeLabel)...</div>
    <iframe id="drawio-frame" src="$drawioUrl" tabindex="0" allow="clipboard-read; clipboard-write"></iframe>
    
    <script>
        const iframe = document.getElementById('drawio-frame');
        const status = document.getElementById('status');
        let drawioReady = false;
        
        // 监听 iframe 加载
        iframe.onload = function() {
            console.log('✅ Draw.io iframe loaded');
            status.textContent = 'Draw.io iframe 已加载，等待初始化...';
        };
        
        iframe.onerror = function(e) {
            console.error('❌ Draw.io iframe failed to load:', e);
            status.textContent = '❌ 无法加载 Draw.io (网络错误)';
            status.style.background = '#d32f2f';
        };
        
        // 接收来自 Draw.io 的消息
        window.addEventListener('message', function(evt) {
            if (!evt.data) return;
            
            try {
                const msg = typeof evt.data === 'string' ? JSON.parse(evt.data) : evt.data;
                console.log('📨 Event:', msg.event, 'Format:', msg.format);
                
                // 特别记录 export 事件
                if (msg.event === 'export') {
                    console.log('📨 EXPORT received!');
                    console.log('📨   format:', msg.format);
                    console.log('📨   xml length:', msg.xml ? msg.xml.length : 0);
                    console.log('📨   data length:', msg.data ? msg.data.length : 0);
                    console.log('📨   xml preview:', msg.xml ? msg.xml.substring(0, 200) : 'null');
                }
                
                // 更新状态
                if (msg.event === 'init') {
                    status.textContent = '✅ Draw.io 初始化中...';
                } else if (msg.event === 'configure') {
                    status.textContent = '✅ Draw.io 配置完成';
                    drawioReady = true;
                    setTimeout(() => status.style.display = 'none', 2000);
                } else if (msg.event === 'autosave') {
                    console.log('📦 Autosave event, xml length:', msg.xml ? msg.xml.length : 0);
                }
                
                // 发送到 Java 端
                ${jsQuery.inject("JSON.stringify(msg)")}
            } catch (e) {
                console.error('❌ Failed to parse message:', e);
                status.textContent = '❌ 消息解析错误';
                status.style.background = '#d32f2f';
            }
        });
        
        // Java 调用此函数发送数据到 Draw.io
        window.sendToDrawio = function(data) {
            iframe.contentWindow.postMessage(JSON.stringify(data), '*');
        };
        
        // Java 调用此函数加载图表数据
        window.loadDiagram = function(xmlData) {
            console.log('📤 Loading diagram, XML length:', xmlData.length);
            
            if (!iframe.contentWindow) {
                console.error('❌ iframe.contentWindow is null!');
                status.textContent = '❌ iframe 未就绪';
                status.style.background = '#d32f2f';
                return;
            }
            
            if (!drawioReady) {
                console.warn('⚠️ Draw.io not ready yet, waiting...');
                setTimeout(() => window.loadDiagram(xmlData), 500);
                return;
            }
            
            const msg = {
                action: 'load',
                autosave: 1,
                xml: xmlData
            };
            console.log('📤 Sending load message to Draw.io:', msg);
            iframe.contentWindow.postMessage(JSON.stringify(msg), '*');
            status.textContent = '📤 正在加载图表...';
            setTimeout(() => status.style.display = 'none', 2000);
        };
        
        // Java 调用此函数导出为 PNG
        window.exportPng = function() {
            iframe.contentWindow.postMessage(JSON.stringify({
                action: 'export',
                format: 'png'
            }), '*');
        };
        
        // Java 调用此函数导出为 SVG
        window.exportSvg = function() {
            iframe.contentWindow.postMessage(JSON.stringify({
                action: 'export',
                format: 'svg'
            }), '*');
        };
        
        // 监听键盘事件
        document.addEventListener('keydown', function(e) {
            const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0;
            const modifier = isMac ? e.metaKey : e.ctrlKey;
            
            if (modifier) {
                if (e.key === 's' || e.key === 'S') {
                    // 保存
                    e.preventDefault();
                    e.stopPropagation();
                    console.log('💾 Save shortcut detected!');
                    ${jsQuery.inject("JSON.stringify({event: 'saveRequested'})")}
                } else if (e.key === 'z' || e.key === 'Z') {
                    // 撤销 - 转发到 Draw.io
                    e.preventDefault();
                    e.stopPropagation();
                    console.log('↩️ Undo shortcut - forwarding to Draw.io');
                    if (e.shiftKey) {
                        // Cmd+Shift+Z = Redo
                        iframe.contentWindow.postMessage(JSON.stringify({action: 'redo'}), '*');
                    } else {
                        // Cmd+Z = Undo
                        iframe.contentWindow.postMessage(JSON.stringify({action: 'undo'}), '*');
                    }
                } else if (e.key === 'y' || e.key === 'Y') {
                    // 重做 - 转发到 Draw.io
                    e.preventDefault();
                    e.stopPropagation();
                    console.log('↪️ Redo shortcut - forwarding to Draw.io');
                    iframe.contentWindow.postMessage(JSON.stringify({action: 'redo'}), '*');
                }
            }
        }, true); // 使用捕获阶段
        
        // 鼠标滚轮事件 - 交给 Draw.io 自己处理缩放与拖动
        // 这里只是尽量保持 iframe 聚焦，不再拦截 Ctrl/Cmd + 滚轮
        document.addEventListener('wheel', function(e) {
            if (document.activeElement !== iframe) {
                iframe.focus();
            }
        }, { passive: true });
        
        // 确保 iframe 能获取焦点
        iframe.addEventListener('load', function() {
            // 自动聚焦到 iframe
            setTimeout(() => iframe.focus(), 100);
        });
        
        // 点击时聚焦 iframe
        document.addEventListener('click', function(e) {
            iframe.focus();
        });
    </script>
</body>
</html>
        """.trimIndent()
    }

    /**
     * 处理来自 Draw.io 的消息
     */
    private fun handleDrawioMessage(msg: String) {
        try {
            logger.debug("📨 Java received message: $msg")
            val message = gson.fromJson(msg, Map::class.java)
            val event = message["event"] as? String
            logger.debug("📨 Event type: $event")
            
            when (event) {
                "configure" -> {
                    // Draw.io 请求配置，回复配置信息
                    logger.info("Draw.io requesting configuration")
                    logger.debug("📨 Draw.io requesting configuration, sending config...")
                    
                    // 发送配置响应
                    ApplicationManager.getApplication().invokeLater {
                        executeJS("""
                            iframe.contentWindow.postMessage(JSON.stringify({
                                action: 'configure',
                                config: {
                                    defaultFonts: ['Microsoft YaHei', 'SimHei', 'Arial'],
                                    autosave: 1,  // 启用自动保存，用于缓存当前画布内容
                                    autosaveDelay: 100,  // 自动保存延迟 100ms（更快响应）
                                    // 关键配置：将链接点击作为消息发送，而不是直接打开
                                    sendExternalLinks: true,
                                    // 禁用链接在新窗口打开
                                    linkTarget: 'none'
                                }
                            }), '*');
                            console.log('📤 Configuration sent to Draw.io');
                        """.trimIndent())
                    }
                }
                "init" -> {
                    // Draw.io 初始化完成，现在可以加载数据
                    logger.info("Draw.io initialized")
                    logger.debug("✅ Draw.io initialized successfully! Now loading diagram...")
                    
                    // 在后台线程执行，避免阻塞
                    ApplicationManager.getApplication().executeOnPooledThread {
                        Thread.sleep(500)
                        loadExistingDiagram()
                    }
                }
                "save" -> {
                    // 用户点击保存
                    val xml = message["xml"] as? String
                    if (xml != null) {
                        saveDiagramXml(xml)
                    }
                }
                "export" -> {
                    // 导出完成
                    val data = message["data"] as? String
                    val format = message["format"] as? String
                    val xml = message["xml"] as? String
                    @Suppress("UNUSED_VARIABLE") val spinKey = message["spinKey"] as? String
                    val messageStr = message["message"] as? String  // 有时候 XML 在 message 字段
                    
                    logger.debug("📨 Export event received!")
                    logger.debug("📨   format=$format")
                    logger.debug("📨   hasXml=${xml != null}, xmlLength=${xml?.length ?: 0}")
                    logger.debug("📨   hasData=${data != null}, dataLength=${data?.length ?: 0}")
                    logger.debug("📨   hasMessage=${messageStr != null}")
                    logger.debug("📨   waitingForInsert=$waitingForInsertExport, waitingForJump=$waitingForJumpExport, waitingForSave=$waitingForSaveExport, waitingForSaveAndSwitch=$waitingForSaveAndSwitch")
                    
                    // 尝试从多个字段提取 XML
                    val xmlContent = xml 
                        ?: messageStr?.takeIf { it.contains("<mxGraphModel") }
                        ?: data?.let { extractXmlFromSvg(it) }
                    
                    logger.debug("📨 Final XML content length: ${xmlContent?.length ?: 0}")
                    
                    // 如果是为了跳转而导出的 XML
                    if (waitingForJumpExport && xmlContent != null) {
                        waitingForJumpExport = false
                        extractBookmarkAndJump(xmlContent)
                    }
                    // 如果是为了插入书签而导出的 XML
                    else if (waitingForInsertExport) {
                        waitingForInsertExport = false
                        val bookmark = pendingBookmark
                        pendingBookmark = null
                        
                        if (bookmark != null) {
                            // 优先使用导出的 XML，如果为空则使用缓存
                            val xmlToUse = if (xmlContent != null && xmlContent.contains("<mxGraphModel")) {
                                logger.debug("📌 Using exported XML for insert (length: ${xmlContent.length})")
                                xmlContent
                            } else if (currentCanvasXml != null && currentCanvasXml!!.contains("<mxGraphModel")) {
                                logger.debug("📌 Export returned empty, using cached XML (length: ${currentCanvasXml!!.length})")
                                currentCanvasXml!!
                            } else {
                                logger.debug("⚠️ No valid XML available, using empty template")
                                "<mxGraphModel><root><mxCell id=\"0\"/><mxCell id=\"1\" parent=\"0\"/></root></mxGraphModel>"
                            }
                            insertNodeIntoXml(xmlToUse, bookmark)
                        }
                    }
                    // 如果是保存请求的导出
                    else if (waitingForSaveExport && xmlContent != null) {
                        waitingForSaveExport = false
                        saveDiagramXml(xmlContent)
                    }
                    // 如果是保存并切换请求的导出
                    else if (waitingForSaveAndSwitch && xmlContent != null) {
                        waitingForSaveAndSwitch = false
                        doSaveAndSwitch(xmlContent)
                    }
                    // 如果是 XML 导出（保存功能）
                    else if (format == "xml" && xmlContent != null) {
                        saveDiagramXml(xmlContent)
                    } else if (data != null && format != null && format != "xmlsvg") {
                        // 其他格式导出（PNG/SVG）
                        handleExport(data, format)
                    }
                }
                "autosave" -> {
                    // 自动保存 - 自动保存到文件
                    val xml = message["xml"] as? String
                    if (xml != null && xml.contains("<mxGraphModel")) {
                        logger.debug("📦 Autosave received, auto-saving XML (length: ${xml.length})")
                        currentCanvasXml = xml
                        // 自动保存到文件（静默保存，不显示提示）
                        autoSaveDiagramXml(xml)
                    }
                }
                "openLink" -> {
                    // 用户双击了带链接的节点
                    val link = message["link"] as? String
                    logger.debug("🔗 OpenLink event: link=$link")
                    if (link != null && link.startsWith("bookmark://")) {
                        val bookmarkId = link.removePrefix("bookmark://")
                        navigateToBookmark(bookmarkId)
                    }
                }
                "saveRequested" -> {
                    // 用户按了 Command+S / Ctrl+S
                    logger.debug("💾 Save requested via keyboard shortcut")
                    saveDiagram()
                }
            }
            
            // 额外检查：某些版本的 Draw.io 可能使用不同的消息格式
            val action = message["action"] as? String
            if (action == "openLink") {
                val link = message["link"] as? String ?: message["url"] as? String
                logger.debug("🔗 Action openLink: link=$link")
                if (link != null && link.startsWith("bookmark://")) {
                    val bookmarkId = link.removePrefix("bookmark://")
                    navigateToBookmark(bookmarkId)
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to parse Draw.io message", e)
        }
    }
    
    /**
     * 导航到书签位置
     */
    private fun navigateToBookmark(bookmarkId: String) {
        logger.debug("🚀 Navigating to bookmark: $bookmarkId")
        
        val bookmark = bookmarkService.getBookmark(bookmarkId)
        if (bookmark != null) {
            ApplicationManager.getApplication().invokeLater {
                val success = bookmarkService.navigateToBookmark(bookmark)
                if (success) {
                    logger.debug("✅ Navigation successful!")
                } else {
                    logger.debug("❌ Navigation failed - file not found")
                    javax.swing.JOptionPane.showMessageDialog(
                        mainPanel,
                        "无法跳转到书签位置，文件可能已被删除或移动",
                        "跳转失败",
                        javax.swing.JOptionPane.WARNING_MESSAGE
                    )
                }
            }
        } else {
            logger.debug("❌ Bookmark not found: $bookmarkId")
            ApplicationManager.getApplication().invokeLater {
                javax.swing.JOptionPane.showMessageDialog(
                    mainPanel,
                    "书签不存在，可能已被删除",
                    "书签未找到",
                    javax.swing.JOptionPane.WARNING_MESSAGE
                )
            }
        }
    }

    /**
     * 加载现有图表数据
     */
    private fun loadExistingDiagram() {
        logger.debug("📊 Loading existing diagram: ${diagram.name}, id: ${diagram.id}, nodes: ${diagram.nodes.size}")
        
        // 首先检查是否有保存的 Draw.io XML
        val savedXml = diagram.metadata["drawioXml"] as? String
        if (!savedXml.isNullOrBlank()) {
            logger.debug("📊 Loading saved Draw.io XML, length: ${savedXml.length}")
            // 同步更新缓存
            currentCanvasXml = savedXml
            ApplicationManager.getApplication().invokeLater {
                val escapedXml = escapeJS(savedXml)
                executeJS("window.loadDiagram('$escapedXml');")
                logger.debug("✅ Saved diagram loaded")
            }
            return
        }
        
        // 如果图表是空的，发送一个空白模板
        if (diagram.nodes.isEmpty() && diagram.connections.isEmpty()) {
            logger.debug("📝 Empty diagram, sending blank template...")
            val emptyXml = """
                <mxGraphModel>
                    <root>
                        <mxCell id="0"/>
                        <mxCell id="1" parent="0"/>
                    </root>
                </mxGraphModel>
            """.trimIndent()
            // 同步更新缓存
            currentCanvasXml = emptyXml
            
            ApplicationManager.getApplication().invokeLater {
                val escapedXml = escapeJS(emptyXml)
                executeJS("window.loadDiagram('$escapedXml');")
                logger.debug("✅ Blank template sent to Draw.io")
            }
        } else {
            // 有旧数据，转换为 Draw.io 格式
            logger.debug("📊 Has existing nodes, injecting data...")
            injectDiagramData()
        }
    }
    
    /**
     * 注入图表数据到 Draw.io
     */
    private fun injectDiagramData() {
        val xmlData = convertDiagramToDrawioXml(diagram)
        // 同步更新缓存
        currentCanvasXml = xmlData
        ApplicationManager.getApplication().invokeLater {
            logger.debug("Injecting diagram XML data to Draw.io...")
            executeJS("window.loadDiagram('${escapeJS(xmlData)}');")
        }
    }

    /**
     * 将内部图表格式转换为 Draw.io XML
     */
    private fun convertDiagramToDrawioXml(diagram: Diagram): String {
        // 生成 Draw.io 兼容的 mxGraph XML 格式
        val sb = StringBuilder()
        sb.append("<mxGraphModel><root>")
        sb.append("<mxCell id=\"0\"/>")
        sb.append("<mxCell id=\"1\" parent=\"0\"/>")
        
        // 添加节点
        diagram.nodes.forEachIndexed { index, node ->
            val cellId = index + 2
            val style = buildDrawioNodeStyle(node)
            sb.append("<mxCell id=\"$cellId\" value=\"${escapeXml(node.label)}\" ")
            sb.append("style=\"$style\" vertex=\"1\" parent=\"1\">")
            sb.append("<mxGeometry x=\"${node.x}\" y=\"${node.y}\" ")
            sb.append("width=\"${node.width}\" height=\"${node.height}\" as=\"geometry\"/>")
            sb.append("</mxCell>")
        }
        
        // 添加连接线
        diagram.connections.forEach { conn ->
            val sourceIdx = diagram.nodes.indexOfFirst { it.id == conn.sourceNodeId }
            val targetIdx = diagram.nodes.indexOfFirst { it.id == conn.targetNodeId }
            if (sourceIdx >= 0 && targetIdx >= 0) {
                val style = buildDrawioConnectionStyle(conn)
                sb.append("<mxCell id=\"conn_${conn.id}\" value=\"${escapeXml(conn.label)}\" ")
                sb.append("style=\"$style\" edge=\"1\" parent=\"1\" ")
                sb.append("source=\"${sourceIdx + 2}\" target=\"${targetIdx + 2}\">")
                sb.append("<mxGeometry relative=\"1\" as=\"geometry\"/>")
                sb.append("</mxCell>")
            }
        }
        
        sb.append("</root></mxGraphModel>")
        return sb.toString()
    }

    private fun buildDrawioNodeStyle(node: DiagramNode): String {
        val parts = mutableListOf<String>()
        
        // 形状
        when (node.shape) {
            NodeShape.RECTANGLE -> parts.add("shape=rectangle")
            NodeShape.ROUNDED_RECT -> parts.add("rounded=1")
            NodeShape.CIRCLE -> parts.add("shape=ellipse;aspect=fixed")
            NodeShape.ELLIPSE -> parts.add("shape=ellipse")
            NodeShape.DIAMOND -> parts.add("shape=rhombus")
        }
        
        // 颜色
        parts.add("fillColor=${node.color}")
        parts.add("strokeColor=${node.borderColor}")
        parts.add("fontColor=${node.textColor}")
        parts.add("strokeWidth=${node.borderWidth}")
        parts.add("fontSize=${node.fontSize}")
        
        return parts.joinToString(";")
    }

    private fun buildDrawioConnectionStyle(conn: DiagramConnection): String {
        val parts = mutableListOf("edgeStyle=orthogonalEdgeStyle", "curved=1")
        parts.add("strokeColor=${conn.lineColor}")
        parts.add("strokeWidth=${conn.lineWidth}")
        parts.add("fontSize=${conn.fontSize}")
        
        if (conn.connectionType == ConnectionType.DASHED) {
            parts.add("dashed=1")
        }
        
        return parts.joinToString(";")
    }

    // 标记是否正在等待保存导出
    private var waitingForSaveExport = false
    
    /**
     * 保存图表数据
     */
    private fun saveDiagram() {
        logger.debug("📤 Saving diagram...")
        
        // 优先使用缓存的 XML（通过 autosave 自动更新）
        val cachedXml = currentCanvasXml
        if (cachedXml != null && cachedXml.contains("<mxGraphModel")) {
            logger.debug("📤 Using cached XML for save (length: ${cachedXml.length})")
            saveDiagramXml(cachedXml)
            return
        }
        
        // 如果没有缓存，请求导出（不使用 spin 避免 UI 阻塞）
        logger.debug("📤 No cached XML, requesting export...")
        waitingForSaveExport = true
        executeJS("""
            status.textContent = '正在保存...'; 
            status.style.display = 'block'; 
            status.style.background = '#2196f3';
            iframe.contentWindow.postMessage(JSON.stringify({
                action: 'export',
                format: 'xml'
            }), '*');
        """.trimIndent())
    }

    /**
     * 自动保存（静默，不显示提示）
     * 每次编辑后自动保存到文件，确保不会丢失数据
     */
    private fun autoSaveDiagramXml(xml: String) {
        try {
            diagram.metadata["drawioXml"] = xml
            diagramService.updateDiagram(diagram)
            logger.debug("📦 Auto-saved diagram XML (length: ${xml.length})")
            // 自动保存后标记为未修改，因为数据已经保存了
            if (modified) {
                setModified(false)
            }
        } catch (e: Exception) {
            logger.error("Failed to auto-save diagram", e)
        }
    }
    
    /**
     * 手动保存（显示提示）
     */
    private fun saveDiagramXml(xml: String) {
        try {
            logger.debug("💾 Saving diagram XML, length: ${xml.length}")
            // 保存 Draw.io XML 到 diagram 的 metadata
            diagram.metadata["drawioXml"] = xml
            diagramService.updateDiagram(diagram)
            logger.debug("✅ Diagram saved successfully!")
            
            // 标记为未修改
            setModified(false)
            
            // 显示保存成功提示
            ApplicationManager.getApplication().invokeLater {
                val message = if (Messages.isEnglish()) "✅ Saved" else "✅ 保存成功"
                executeJS("status.textContent = '$message'; status.style.display = 'block'; status.style.background = '#4caf50'; setTimeout(() => status.style.display = 'none', 2000);")
            }
        } catch (e: Exception) {
            logger.error("Failed to save diagram", e)
            logger.debug("❌ Failed to save diagram: ${e.message}")
        }
    }

    /**
     * 保存并切换到查看模式
     */
    private fun saveAndSwitchToViewMode() {
        logger.debug("📸 Save and switch to view mode...")
        
        // 优先使用缓存的 XML
        val cachedXml = currentCanvasXml
        if (cachedXml != null && cachedXml.contains("<mxGraphModel")) {
            doSaveAndSwitch(cachedXml)
        } else {
            // 没有缓存，请求导出
            waitingForSaveAndSwitch = true
            executeJS("""
                status.textContent = '正在保存...'; 
                status.style.display = 'block'; 
                status.style.background = '#2196f3';
                iframe.contentWindow.postMessage(JSON.stringify({
                    action: 'export',
                    format: 'xml'
                }), '*');
            """.trimIndent())
        }
    }
    
    private fun doSaveAndSwitch(xml: String) {
        try {
            diagram.metadata["drawioXml"] = xml
            diagramService.updateDiagram(diagram)
            logger.debug("✅ Diagram saved, switching to view mode...")
            
            executeJS("status.textContent = '✅ 保存成功，正在切换到查看模式...'; status.style.display = 'block'; status.style.background = '#4caf50';")
            
            // 使用 invokeLater 确保在正确的写操作上下文中执行
            javax.swing.Timer(800) {
                ApplicationManager.getApplication().invokeLater {
                    FileEditorManager.getInstance(project).closeFile(file)
                    DiagramEditorProvider.openDiagramInEditor(project, diagram, viewOnly = true)
                }
            }.apply { isRepeats = false; start() }
        } catch (e: Exception) {
            logger.error("Failed to save and switch", e)
        }
    }
    
    /**
     * 切换到编辑模式
     */
    private fun switchToEditMode() {
        // 使用 invokeLater 确保在正确的写操作上下文中执行
        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).closeFile(file)
            DiagramEditorProvider.openDiagramInEditor(project, diagram, viewOnly = false)
        }
    }
    
    /**
     * 刷新 Draw.io - 重新加载 iframe
     */
    private fun refreshDrawio() {
        // 重新加载浏览器页面
        browser.cefBrowser.reload()
        // 延迟后重新加载图表数据
        javax.swing.Timer(2000) {
            loadExistingDiagram()
        }.apply { isRepeats = false; start() }
    }

    /**
     * 导出为 PNG
     */
    private fun exportAsPng() {
        executeJS("window.exportPng();")
    }

    /**
     * 导出为 SVG
     */
    private fun exportAsSvg() {
        executeJS("window.exportSvg();")
    }
    
    // ===== 浏览器编辑功能 =====
    // 注意：缩放和拖动功能现在完全由 Draw.io 自己处理（Ctrl+滚轮缩放，中键/右键拖动）
    
    /**
     * 在外部浏览器中打开编辑
     * 将图表保存为临时 .drawio 文件，用户可以直接用 draw.io 桌面版或网页版打开
     */
    private fun openInExternalBrowser() {
        try {
            // 获取当前图表的 XML
            val xml = currentCanvasXml ?: diagram.metadata["drawioXml"] as? String
            
            if (xml.isNullOrBlank()) {
                // 如果没有内容，直接打开空白编辑器
                BrowserUtil.browse("https://app.diagrams.net/")
                return
            }
            
            // 将 XML 保存到临时 .drawio 文件
            val tempDir = File(System.getProperty("java.io.tmpdir"), "drawio_bookmark")
            tempDir.mkdirs()
            val tempFile = File(tempDir, "${diagram.name.replace(Regex("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]"), "_")}.drawio")
            
            // 写入完整的 drawio 格式（包装为 mxfile）
            val drawioContent = if (xml.contains("<mxfile")) {
                xml
            } else {
                """<mxfile host="app.diagrams.net" modified="${java.time.Instant.now()}" agent="BookmarkPalace" version="1.0">
                    <diagram name="Page-1" id="page1">$xml</diagram>
                </mxfile>""".trimIndent()
            }
            tempFile.writeText(drawioContent)
            
            logger.info("📁 Saved diagram to: ${tempFile.absolutePath}")
            
            // 显示选项对话框
            ApplicationManager.getApplication().invokeLater {
                val options = if (Messages.isEnglish()) {
                    arrayOf("Open with Desktop App", "Open draw.io website", "Show file location", "Cancel")
                } else {
                    arrayOf("用桌面版打开", "打开 draw.io 网站", "显示文件位置", "取消")
                }
                
                val choice = JOptionPane.showOptionDialog(
                    mainPanel,
                    if (Messages.isEnglish())
                        "Diagram saved to:\n${tempFile.absolutePath}\n\n" +
                        "Options:\n" +
                        "1. Open with Draw.io Desktop (recommended, faster)\n" +
                        "2. Open draw.io website and drag the file into it\n" +
                        "3. Show file in Finder/Explorer\n\n" +
                        "After editing, save the file. Then return to IDE and\n" +
                        "click ↻ sync button to import changes."
                    else
                        "导览图已保存到：\n${tempFile.absolutePath}\n\n" +
                        "选项：\n" +
                        "1. 用 Draw.io 桌面版打开（推荐，速度快）\n" +
                        "2. 打开 draw.io 网站，将文件拖入\n" +
                        "3. 在 Finder 中显示文件\n\n" +
                        "编辑完成后保存文件，然后返回 IDE\n" +
                        "点击 ↻ 同步按钮导入更改。",
                    Messages.openInBrowser,
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[0]
                )
                
                when (choice) {
                    0 -> {
                        // 用系统默认应用打开（Draw.io 桌面版）
                        try {
                            java.awt.Desktop.getDesktop().open(tempFile)
                        } catch (e: Exception) {
                            // 如果没有关联应用，提示用户
                            JOptionPane.showMessageDialog(
                                mainPanel,
                                if (Messages.isEnglish())
                                    "Cannot open .drawio file.\n\n" +
                                    "Please install Draw.io Desktop from:\n" +
                                    "https://github.com/jgraph/drawio-desktop/releases\n\n" +
                                    "Or open the file manually from:\n${tempFile.absolutePath}"
                                else
                                    "无法打开 .drawio 文件。\n\n" +
                                    "请从以下地址下载安装 Draw.io 桌面版：\n" +
                                    "https://github.com/jgraph/drawio-desktop/releases\n\n" +
                                    "或手动打开文件：\n${tempFile.absolutePath}",
                                if (Messages.isEnglish()) "App Not Found" else "未找到应用",
                                JOptionPane.WARNING_MESSAGE
                            )
                        }
                    }
                    1 -> BrowserUtil.browse("https://app.diagrams.net/")
                    2 -> {
                        // 打开文件所在目录
                        java.awt.Desktop.getDesktop().open(tempDir)
                    }
                }
            }
            
        } catch (e: Exception) {
            logger.error("Failed to open in browser", e)
            JOptionPane.showMessageDialog(
                mainPanel,
                "打开失败: ${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }
    
    /**
     * 从文件同步浏览器编辑的内容
     * 优先从临时文件读取，如果没有则从剪贴板读取
     */
    private fun syncFromBrowser() {
        try {
            // 尝试从临时文件读取
            val tempDir = File(System.getProperty("java.io.tmpdir"), "drawio_bookmark")
            val tempFile = File(tempDir, "${diagram.name.replace(Regex("[^a-zA-Z0-9_\\-\\u4e00-\\u9fa5]"), "_")}.drawio")
            
            var xmlData: String? = null
            var source = ""
            
            if (tempFile.exists()) {
                val content = tempFile.readText()
                if (content.contains("<mxGraphModel") || content.contains("<mxfile")) {
                    xmlData = content
                    source = "file"
                }
            }
            
            // 如果文件没有有效内容，尝试从剪贴板读取
            if (xmlData == null) {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                val contents = clipboard.getContents(null)
                if (contents != null && contents.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
                    val data = contents.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor) as String
                    if (data.contains("<mxGraphModel") || data.contains("<mxfile")) {
                        xmlData = data
                        source = "clipboard"
                    }
                }
            }
            
            if (xmlData != null) {
                // 提取 mxGraphModel（从 mxfile 包装中）
                val graphModel = if (xmlData.contains("<diagram")) {
                    // 从 mxfile 格式中提取 mxGraphModel
                    val diagramContent = Regex("<diagram[^>]*>([\\s\\S]*?)</diagram>").find(xmlData)?.groupValues?.get(1)
                    if (diagramContent != null && diagramContent.contains("<mxGraphModel")) {
                        diagramContent
                    } else {
                        xmlData
                    }
                } else {
                    xmlData
                }
                
                // 更新缓存和图表
                currentCanvasXml = graphModel
                diagram.metadata["drawioXml"] = graphModel
                diagramService.updateDiagram(diagram)
                
                // 重新加载到编辑器
                val escapedXml = escapeJS(graphModel)
                executeJS("window.loadDiagram('$escapedXml');")
                
                val msg = if (Messages.isEnglish()) "Synced from $source" else "已从${if (source == "file") "文件" else "剪贴板"}同步"
                executeJS("""
                    status.textContent = '✅ $msg';
                    status.style.display = 'block';
                    status.style.background = '#4caf50';
                    setTimeout(() => status.style.display = 'none', 3000);
                """.trimIndent())
                
                logger.info("✅ Synced diagram from $source")
            } else {
                JOptionPane.showMessageDialog(
                    mainPanel,
                    if (Messages.isEnglish())
                        "No valid diagram found.\n\n" +
                        "Please make sure you have:\n" +
                        "1. Saved the file in draw.io, or\n" +
                        "2. Copied the diagram to clipboard"
                    else
                        "未找到有效的图表内容。\n\n" +
                        "请确保您已经：\n" +
                        "1. 在 draw.io 中保存了文件，或\n" +
                        "2. 将图表复制到剪贴板",
                    if (Messages.isEnglish()) "Sync" else "同步",
                    JOptionPane.INFORMATION_MESSAGE
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to sync from browser", e)
            JOptionPane.showMessageDialog(
                mainPanel,
                "同步失败: ${e.message}",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
        }
    }

    /**
     * 处理导出的图片数据
     */
    private fun handleExport(data: String, format: String) {
        logger.info("Handling export: format=$format, data length=${data.length}")
        
        ApplicationManager.getApplication().invokeLater {
            try {
                // 确定文件扩展名和描述
                val extension = when (format.lowercase()) {
                    "png" -> "png"
                    "svg" -> "svg"
                    else -> format
                }
                val description = when (format.lowercase()) {
                    "png" -> "PNG 图片"
                    "svg" -> "SVG 矢量图"
                    else -> "导览图文件"
                }
                
                // 创建文件保存对话框
                val descriptor = FileSaverDescriptor(
                    "导出导览图",
                    description,
                    extension
                )
                
                val defaultFileName = "${diagram.name}.$extension"
                val fileWrapper = FileChooserFactory.getInstance()
                    .createSaveFileDialog(descriptor, project)
                    .save(defaultFileName)
                
                if (fileWrapper != null) {
                    val file = fileWrapper.file
                    
                    // 解码并保存数据
                    if (data.startsWith("data:")) {
                        // Base64 编码的数据 (PNG)
                        val base64Data = data.substringAfter(",")
                        val bytes = java.util.Base64.getDecoder().decode(base64Data)
                        file.writeBytes(bytes)
                    } else {
                        // 直接写入文本 (SVG)
                        file.writeText(data)
                    }
                    
                    logger.info("✅ Exported to: ${file.absolutePath}")
                    JOptionPane.showMessageDialog(
                        mainPanel,
                        "导出成功: ${file.absolutePath}",
                        "导出 $extension",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            } catch (e: Exception) {
                logger.error("Export failed", e)
                JOptionPane.showMessageDialog(
                    mainPanel,
                    "导出失败: ${e.message}",
                    "错误",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    /**
     * 执行 JavaScript
     */
    private fun executeJS(script: String) {
        browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
    }

    private fun escapeJS(s: String): String = s.replace("'", "\\'").replace("\n", "\\n")
    private fun escapeXml(s: String): String = s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
    
    /**
     * 从 SVG/xmlsvg 数据中提取 mxGraphModel XML
     * Draw.io 的 xmlsvg 格式会在 SVG 的 content 属性中嵌入 mxGraphModel
     */
    private fun extractXmlFromSvg(data: String): String? {
        // 如果已经是 mxGraphModel XML，直接返回
        if (data.contains("<mxGraphModel") || data.contains("<mxfile")) {
            logger.debug("📄 Data is already mxGraphModel XML")
            return data
        }
        
        // 尝试从 SVG 的 content 属性中提取
        val contentPattern = Regex("""content="([^"]+)"""")
        val match = contentPattern.find(data)
        if (match != null) {
            val encodedContent = match.groupValues[1]
            // URL 解码
            val decoded = java.net.URLDecoder.decode(encodedContent, "UTF-8")
            logger.debug("📄 Extracted XML from SVG content, length: ${decoded.length}")
            return decoded
        }
        
        // 尝试 Base64 解码（某些情况下数据可能是 base64 编码的）
        if (data.startsWith("data:")) {
            val base64Data = data.substringAfter(",")
            try {
                val decoded = String(java.util.Base64.getDecoder().decode(base64Data))
                if (decoded.contains("<mxGraphModel") || decoded.contains("<mxfile")) {
                    logger.debug("📄 Extracted XML from Base64 data")
                    return decoded
                }
            } catch (e: Exception) {
                logger.debug("⚠️ Base64 decode failed: ${e.message}")
            }
        }
        
        logger.debug("⚠️ Could not extract XML from data, returning original")
        return data
    }

    // FileEditor 接口实现
    override fun getComponent(): JComponent = mainPanel
    override fun getPreferredFocusedComponent(): JComponent = browser.component
    override fun getName(): String = if (viewOnly) "📖 ${diagram.name}" else "✏️ ${diagram.name}"
    override fun setState(state: FileEditorState) {}
    override fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
    override fun isModified(): Boolean = modified && !viewOnly
    override fun isValid(): Boolean = true
    
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeListeners.add(listener)
    }
    
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeListeners.remove(listener)
    }
    
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun getFile(): VirtualFile = file
    
    override fun dispose() {
        // dispose 是在关闭后调用的，不需要弹窗
        // 关闭前的保存提示由 DiagramEditorManagerListener 处理
        browser.dispose()
    }
}

/**
 * 自定义的自动换行布局管理器
 * 当容器宽度不够时自动将组件换到下一行
 */
class WrapLayout(align: Int = java.awt.FlowLayout.LEFT, hgap: Int = 5, vgap: Int = 5) : java.awt.FlowLayout(align, hgap, vgap) {
    
    override fun preferredLayoutSize(target: java.awt.Container): java.awt.Dimension {
        return layoutSize(target, true)
    }
    
    override fun minimumLayoutSize(target: java.awt.Container): java.awt.Dimension {
        val minimum = layoutSize(target, false)
        minimum.width -= (hgap + 1)
        return minimum
    }
    
    private fun layoutSize(target: java.awt.Container, preferred: Boolean): java.awt.Dimension {
        synchronized(target.treeLock) {
            val targetWidth = target.width
            val insets = target.insets
            val horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2)
            val maxWidth = if (targetWidth > 0) targetWidth - horizontalInsetsAndGap else Int.MAX_VALUE
            
            val dim = java.awt.Dimension(0, 0)
            var rowWidth = 0
            var rowHeight = 0
            
            for (i in 0 until target.componentCount) {
                val m = target.getComponent(i)
                if (m.isVisible) {
                    val d = if (preferred) m.preferredSize else m.minimumSize
                    
                    if (rowWidth + d.width > maxWidth) {
                        // 换行
                        dim.width = maxOf(dim.width, rowWidth)
                        dim.height += rowHeight + vgap
                        rowWidth = 0
                        rowHeight = 0
                    }
                    
                    if (rowWidth != 0) {
                        rowWidth += hgap
                    }
                    rowWidth += d.width
                    rowHeight = maxOf(rowHeight, d.height)
                }
            }
            
            dim.width = maxOf(dim.width, rowWidth)
            dim.height += rowHeight
            dim.width += horizontalInsetsAndGap
            dim.height += insets.top + insets.bottom + vgap * 2
            
            return dim
        }
    }
}
