package com.longlong.bookmark.ui.common

import com.longlong.bookmark.model.BookmarkColor
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

/**
 * 书签颜色 ComboBox 渲染器
 * 统一管理颜色显示逻辑，避免代码重复
 */
class BookmarkColorRenderer : DefaultListCellRenderer() {
    
    override fun getListCellRendererComponent(
        list: JList<*>?,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        val color = value as? BookmarkColor
        if (color != null) {
            text = "${color.emoji} ${color.displayName}"
        }
        return this
    }
}

/**
 * BookmarkColor 扩展属性：获取颜色对应的 emoji
 */
val BookmarkColor.emoji: String
    get() = when (this) {
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
