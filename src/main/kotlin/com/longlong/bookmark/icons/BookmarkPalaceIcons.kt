package com.longlong.bookmark.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * BookmarkPalace 插件图标集合
 * 自动支持暗色主题（同名 _dark.svg 文件）
 */
object BookmarkPalaceIcons {
    
    // === 主 Logo ===
    @JvmField val Logo = load("/icons/logo.svg")
    @JvmField val LogoLarge = load("/icons/logo_large.svg")
    
    // === 工具栏图标 ===
    @JvmField val BookmarkTool = load("/icons/bookmark_tool.svg")
    @JvmField val AddBookmark = load("/icons/add_bookmark.svg")
    @JvmField val QuickAdd = load("/icons/quick_add.svg")
    @JvmField val BookmarkList = load("/icons/bookmark_list.svg")
    
    // === 导览图相关 ===
    @JvmField val Diagram = load("/icons/diagram.svg")
    @JvmField val DiagramView = load("/icons/diagram_view.svg")
    
    // === 导入导出 ===
    @JvmField val Export = load("/icons/export.svg")
    @JvmField val Import = load("/icons/import.svg")
    
    // === 语言切换 ===
    @JvmField val Language = load("/icons/language.svg")
    
    // === 刷新 ===
    @JvmField val Refresh = load("/icons/refresh.svg")
    
    private fun load(path: String): Icon {
        return IconLoader.getIcon(path, BookmarkPalaceIcons::class.java)
    }
}
