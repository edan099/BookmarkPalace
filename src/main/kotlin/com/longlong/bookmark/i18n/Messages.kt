package com.longlong.bookmark.i18n

import com.intellij.ide.util.PropertiesComponent
import java.util.Locale

/**
 * 全局多语言支持 - 整个插件使用（带持久化）
 */
object Messages {
    
    private const val LANGUAGE_KEY = "bookmarkpalace.language"
    private const val LANGUAGE_INITIALIZED_KEY = "bookmarkpalace.language.initialized"
    private var isEnglish = false
    
    init {
        val properties = PropertiesComponent.getInstance()
        
        // 检查是否已经初始化过语言设置
        val initialized = properties.getBoolean(LANGUAGE_INITIALIZED_KEY, false)
        
        if (initialized) {
            // 已初始化，使用保存的设置
            isEnglish = properties.getBoolean(LANGUAGE_KEY, true)
        } else {
            // 首次运行，根据系统/IDE 语言自动选择
            val systemLanguage = Locale.getDefault().language
            isEnglish = systemLanguage != "zh" // 如果不是中文，则使用英文
            
            // 保存初始化状态和语言设置
            properties.setValue(LANGUAGE_INITIALIZED_KEY, true)
            properties.setValue(LANGUAGE_KEY, isEnglish)
        }
    }
    
    fun toggleLanguage() { 
        isEnglish = !isEnglish 
        PropertiesComponent.getInstance().setValue(LANGUAGE_KEY, isEnglish)
    }
    fun isEnglish() = isEnglish
    fun setEnglish(en: Boolean) { 
        isEnglish = en 
        PropertiesComponent.getInstance().setValue(LANGUAGE_KEY, isEnglish)
    }
    
    private fun get(zh: String, en: String) = if (isEnglish) en else zh
    
    // ===== 通用 =====
    val pluginName get() = get("书签宫殿", "BookmarkPalace")
    val ok get() = get("确定", "OK")
    val cancel get() = get("取消", "Cancel")
    val save get() = get("保存", "Save")
    val delete get() = get("删除", "Delete")
    val edit get() = get("编辑", "Edit")
    val add get() = get("添加", "Add")
    val close get() = get("关闭", "Close")
    val name get() = get("名称", "Name")
    val type get() = get("类型", "Type")
    val color get() = get("颜色", "Color")
    val shape get() = get("形状", "Shape")
    val description get() = get("描述", "Description")
    val comment get() = get("备注", "Comment")
    val search get() = get("搜索", "Search")
    val refresh get() = get("刷新", "Refresh")
    val settings get() = get("设置", "Settings")
    val language get() = get("语言", "Language")
    val switchLanguage get() = get("English", "中文")
    
    // ===== 书签 =====
    val bookmark get() = get("书签", "Bookmark")
    val bookmarks get() = get("书签列表", "Bookmarks")
    val addBookmark get() = get("添加书签", "Add Bookmark")
    val editBookmark get() = get("编辑书签", "Edit Bookmark")
    val deleteBookmark get() = get("删除书签", "Delete Bookmark")
    val bookmarkName get() = get("书签名称", "Bookmark Name")
    val bookmarkComment get() = get("书签备注", "Bookmark Comment")
    val bookmarkColor get() = get("书签颜色", "Bookmark Color")
    val bookmarkTags get() = get("书签标签", "Bookmark Tags")
    val jumpTo get() = get("跳转", "Jump To")
    val noBookmarks get() = get("暂无书签", "No Bookmarks")
    val quickAdd get() = get("快速添加", "Quick Add")
    val dragBookmarkHere get() = get("双击书签添加到画布", "Double-click to add")
    val insertBookmark get() = get("插入书签", "Insert Bookmark")
    
    // ===== 导览图 =====
    val diagram get() = get("导览图", "Diagram")
    val diagrams get() = get("导览图列表", "Diagrams")
    val newDiagram get() = get("新建导览图", "New Diagram")
    val openDiagram get() = get("打开导览图", "Open Diagram")
    val deleteDiagram get() = get("删除导览图", "Delete Diagram")
    val renameDiagram get() = get("重命名导览图", "Rename Diagram")
    val viewDiagram get() = get("查看导览图", "View Diagram")
    val viewMode get() = get("查看模式", "View Mode")
    val editMode get() = get("编辑模式", "Edit Mode")
    val viewOnly get() = get("仅查看", "View Only")
    val openView get() = get("打开查看", "Open View")
    val node get() = get("节点", "Node")
    val nodes get() = get("节点", "Nodes")
    val addNode get() = get("添加节点", "Add Node")
    val editNode get() = get("编辑节点", "Edit Node")
    val deleteNode get() = get("删除节点", "Delete Node")
    val connection get() = get("连线", "Connection")
    val editConnection get() = get("编辑连线", "Edit Connection")
    val deleteConnection get() = get("删除连线", "Delete Connection")
    val connectionLabel get() = get("连线文字", "Connection Label")
    val zoomIn get() = get("放大", "Zoom In")
    val zoomOut get() = get("缩小", "Zoom Out")
    val zoomReset get() = get("重置缩放", "Reset Zoom")
    val fitToScreen get() = get("适应屏幕", "Fit to Screen")
    
    // ===== 形状 =====
    val rectangle get() = get("矩形", "Rectangle")
    val roundedRect get() = get("圆角矩形", "Rounded Rect")
    val circle get() = get("圆形", "Circle")
    val ellipse get() = get("椭圆", "Ellipse")
    val diamond get() = get("菱形", "Diamond")
    
    // ===== 连线类型 =====
    val normalLine get() = get("普通线", "Normal")
    val dashedLine get() = get("虚线", "Dashed")
    val arrowLine get() = get("箭头", "Arrow")
    
    // ===== 操作提示 =====
    val tipDragToConnect get() = get("从边缘中点拖拽创建连线", "Drag from edge midpoint to connect")
    val tipDoubleClickEdit get() = get("双击编辑", "Double-click to edit")
    val tipDragCornerResize get() = get("拖拽顶点调整大小", "Drag corners to resize")
    val tipScrollToZoom get() = get("滚轮缩放画布", "Scroll to zoom")
    val tipRightClickMenu get() = get("右键打开菜单", "Right-click for menu")
    val bookmarkNode get() = get("书签节点", "Bookmark Node")
    val normalNode get() = get("普通节点", "Normal Node")
    
    // ===== 导入导出 =====
    val export get() = get("导出", "Export")
    val import get() = get("导入", "Import")
    val exportSuccess get() = get("导出成功", "Export Successful")
    val importSuccess get() = get("导入成功", "Import Successful")
    val exportFailed get() = get("导出失败", "Export Failed")
    val importFailed get() = get("导入失败", "Import Failed")
    
    // ===== 标签 =====
    val tag get() = get("标签", "Tag")
    val tags get() = get("标签", "Tags")
    val addTag get() = get("添加标签", "Add Tag")
    val manageTag get() = get("管理标签", "Manage Tags")
    
    // ===== 颜色 =====
    val colorBlue get() = get("蓝色", "Blue")
    val colorGreen get() = get("绿色", "Green")
    val colorYellow get() = get("黄色", "Yellow")
    val colorOrange get() = get("橙色", "Orange")
    val colorRed get() = get("红色", "Red")
    val colorPurple get() = get("紫色", "Purple")
    val colorPink get() = get("粉色", "Pink")
    val colorCyan get() = get("青色", "Cyan")
    val colorGray get() = get("灰色", "Gray")
    
    // ===== 属性面板 =====
    val properties get() = get("属性", "Properties")
    val nodeProperties get() = get("节点属性", "Node Properties")
    val connectionProperties get() = get("连线属性", "Connection Properties")
    val fontSize get() = get("文字大小", "Font Size")
    val textColor get() = get("文字颜色", "Text Color")
    val fillColor get() = get("填充颜色", "Fill Color")
    val borderColor get() = get("边框颜色", "Border Color")
    val borderWidth get() = get("边框粗细", "Border Width")
    val lineColor get() = get("线条颜色", "Line Color")
    val lineWidth get() = get("线条粗细", "Line Width")
    val lineStyle get() = get("线条样式", "Line Style")
    val width get() = get("宽度", "Width")
    val height get() = get("高度", "Height")
    
    // ===== 搜索 =====
    val searchPlaceholder get() = get("搜索书签...", "Search bookmarks...")
    val filterByColor get() = get("按颜色筛选", "Filter by Color")
    val filterByTag get() = get("按标签筛选", "Filter by Tag")
    val allColors get() = get("所有颜色", "All Colors")
    val allTags get() = get("所有标签", "All Tags")
    val noResults get() = get("无搜索结果", "No Results")
    
    // ===== 视图模式 =====
    val openInEditor get() = get("在编辑器中打开", "Open in Editor")
    val openInWindow get() = get("在窗口中打开", "Open in Window")
    val splitView get() = get("分栏视图", "Split View")
    val splitViewTip get() = get("右键标签页可设置左右分栏，同时查看代码", "Right-click tab to split view with code")
    val switchToEditMode get() = get("切换到编辑模式", "Switch to Edit Mode")
    val clickNodeToJump get() = get("点击节点链接跳转代码", "Click node link to jump to code")
    val saveAndView get() = get("保存并查看", "Save & View")
    val saveAndViewTip get() = get("保存并切换到查看模式", "Save and switch to view mode")
    
    // ===== 帮助 =====
    val help get() = get("使用说明", "Help")
    val helpTip get() = get("查看使用说明", "View user guide")
    
    // ===== 其他菜单项 =====
    val quickAddBookmark get() = get("快速添加书签", "Quick Add Bookmark")
    val donate get() = get("☕ 打赏与联系", "☕ Donate & Contact")
    val diagnoseDiagramEditor get() = get("诊断导览图编辑器", "Diagnose Diagram Editor")
    
    // ===== 导览图编辑器 =====
    val collapseBookmarks get() = get("◀ 收起书签", "◀ Collapse")
    val expandBookmarks get() = get("▶ 展开书签", "▶ Expand")
    val toggleBookmarksTip get() = get("收起/展开书签列表", "Collapse/Expand bookmark list")
    val viewOnlyTip get() = get("仅查看模式（不可编辑，点击节点跳转代码）", "View only mode (not editable, click node to jump to code)")
    val openInEditorTip get() = get("在编辑器中打开（支持分栏，可边看图边看代码）", "Open in editor (supports split view)")
    val openInWindowTip get() = get("在弹窗中打开编辑", "Open in popup window")
    
    // ===== 外部编辑 =====
    val openInBrowser get() = get("用桌面版或浏览器打开", "Open with Desktop/Browser")
    val openInBrowserTip get() = get("用 Draw.io 桌面版或浏览器编辑（推荐桌面版，速度快）", "Edit with Draw.io Desktop or Browser (Desktop recommended)")
    val syncFromBrowser get() = get("同步外部编辑", "Sync External Edits")
    val syncFromBrowserTip get() = get("从桌面版或浏览器导入最新编辑内容", "Import latest edits from Desktop/Browser")
    val browserEditMode get() = get("🌐 浏览器编辑中", "🌐 Editing in Browser")
    val clickToSync get() = get("点击下方按钮导入浏览器中的编辑", "Click button below to import browser edits")
}
