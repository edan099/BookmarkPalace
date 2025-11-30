package com.longlong.bookmark.editor

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.longlong.bookmark.model.Bookmark
import com.longlong.bookmark.model.BookmarkStatus
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.ui.BookmarkToolWindowPanel
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * 书签行标记提供者 - 在 Gutter 区域显示书签图标
 * 每行只显示一个图标，如果同一行有多个书签则合并显示
 * 
 * 重要：使用全局缓存防止同一文件多次调用导致的图标重复
 */
class BookmarkLineMarkerProvider : LineMarkerProvider {
    
    companion object {
        // 全局缓存：文件路径 -> 已处理的行号集合
        // 使用 WeakHashMap 避免内存泄漏
        private val processedCache = java.util.WeakHashMap<String, MutableSet<Int>>()
        
        // 清除指定文件的缓存（在书签变更时调用）
        fun clearCache(filePath: String? = null) {
            if (filePath != null) {
                processedCache.remove(filePath)
            } else {
                processedCache.clear()
            }
        }
    }

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // 不在快速模式中处理，全部交给 collectSlowLineMarkers
        return null
    }

    override fun collectSlowLineMarkers(
        elements: MutableList<out PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (elements.isEmpty()) return

        val firstElement = elements.firstOrNull() ?: return
        val project = firstElement.project
        val psiFile = firstElement.containingFile ?: return
        val virtualFile = psiFile.virtualFile ?: return
        val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return

        val bookmarkService = BookmarkService.getInstance(project)
        val basePath = project.basePath ?: return
        val filePath = if (virtualFile.path.startsWith(basePath)) {
            virtualFile.path.substring(basePath.length + 1)
        } else {
            virtualFile.path
        }

        val bookmarks = bookmarkService.getBookmarksByFile(filePath)
        if (bookmarks.isEmpty()) {
            // 没有书签时清除该文件的缓存
            processedCache.remove(filePath)
            return
        }

        // 按行分组，每行只显示一个图标
        val bookmarksByLine = bookmarks.groupBy { it.startLine }
        
        // 获取或创建该文件的已处理行集合
        // 每次 collectSlowLineMarkers 调用时重置，因为这是一个完整的扫描
        val processedLines = mutableSetOf<Int>()
        
        // 同时维护 result 中已有的行号（防止 IntelliJ 多次调用导致重复）
        val existingLines = result.mapNotNull { marker ->
            try {
                val markerElement = (marker as? LineMarkerInfo<*>)?.element
                if (markerElement != null && markerElement.containingFile == psiFile) {
                    document.getLineNumber(markerElement.textRange.startOffset)
                } else null
            } catch (e: Exception) { null }
        }.toMutableSet()

        // 只处理传入的 elements，避免重复添加同一行的标记
        for (element in elements) {
            try {
                val lineNumber = document.getLineNumber(element.textRange.startOffset)
                
                // 三重检查：本次处理过、result 中已有、缓存中已有
                if (processedLines.contains(lineNumber)) continue
                if (existingLines.contains(lineNumber)) continue
                
                // 检查该行是否有书签
                val lineBookmarks = bookmarksByLine[lineNumber]
                if (lineBookmarks != null && lineBookmarks.isNotEmpty()) {
                    // 如果同一行有多个书签，使用第一个的颜色，tooltip 显示所有
                    val primaryBookmark = lineBookmarks.first()
                    result.add(createLineMarkerInfo(element, primaryBookmark, lineBookmarks, project))
                    processedLines.add(lineNumber)
                    existingLines.add(lineNumber)
                }
            } catch (e: Exception) {
                // 忽略单个元素处理异常
            }
        }
    }

    private fun createLineMarkerInfo(
        element: PsiElement,
        primaryBookmark: Bookmark,
        allBookmarks: List<Bookmark>,
        project: com.intellij.openapi.project.Project
    ): LineMarkerInfo<PsiElement> {
        val icon = createBookmarkIcon(primaryBookmark, allBookmarks.size)
        val tooltipText = buildTooltip(allBookmarks)

        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { tooltipText },
            { _, _ ->
                // 点击打开 BookmarkPalace 工具窗口并聚焦到对应书签
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("BookmarkPalace")
                toolWindow?.show {
                    // 获取工具窗口的内容面板并聚焦到书签
                    val contentManager = toolWindow.contentManager
                    val content = contentManager.contents.firstOrNull()
                    val panel = content?.component as? BookmarkToolWindowPanel
                    panel?.focusBookmark(primaryBookmark)
                }
            },
            GutterIconRenderer.Alignment.LEFT,
            { if (allBookmarks.size > 1) "${primaryBookmark.getDisplayName()} (+${allBookmarks.size - 1})" else primaryBookmark.getDisplayName() }
        )
    }

    private fun createBookmarkIcon(bookmark: Bookmark, count: Int = 1): Icon {
        val size = 12
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val g2d = image.createGraphics()

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // 根据状态和颜色绘制图标
        val color = when (bookmark.status) {
            BookmarkStatus.MISSING -> Color.RED
            BookmarkStatus.OUTDATED -> Color.ORANGE
            BookmarkStatus.VALID -> Color.decode(bookmark.color.hexColor)
        }

        // 绘制宫殿风格的图标（城堡形状）
        g2d.color = color
        
        // 主体
        g2d.fillRect(1, 5, 10, 6)
        
        // 塔楼
        g2d.fillRect(1, 2, 3, 3)
        g2d.fillRect(8, 2, 3, 3)
        
        // 城垛
        g2d.fillRect(1, 2, 1, 1)
        g2d.fillRect(3, 2, 1, 1)
        g2d.fillRect(8, 2, 1, 1)
        g2d.fillRect(10, 2, 1, 1)
        
        // 门
        g2d.color = Color.WHITE
        g2d.fillRect(5, 7, 2, 4)

        // 如果失效，添加 X 标记
        if (bookmark.status == BookmarkStatus.MISSING) {
            g2d.color = Color.RED
            g2d.stroke = BasicStroke(1.5f)
            g2d.drawLine(3, 3, 9, 9)
            g2d.drawLine(9, 3, 3, 9)
        }

        g2d.dispose()
        return ImageIcon(image)
    }

    private fun buildTooltip(bookmarks: List<Bookmark>): String {
        return buildString {
            append("<html>")
            
            bookmarks.forEachIndexed { index, bookmark ->
                if (index > 0) append("<hr>")
                
                append("<b>📌 ${bookmark.getDisplayName()}</b><br>")

                when (bookmark.status) {
                    BookmarkStatus.VALID -> append("<font color='green'>✓ 有效</font><br>")
                    BookmarkStatus.MISSING -> append("<font color='red'>✗ 失效</font><br>")
                    BookmarkStatus.OUTDATED -> append("<font color='orange'>⚠ 可能过期</font><br>")
                }

                if (bookmark.tags.isNotEmpty()) {
                    append("标签: ${bookmark.tags.joinToString(", ")}<br>")
                }

                if (bookmark.comment.isNotEmpty()) {
                    append("注释: ${bookmark.comment}<br>")
                }
            }
            
            append("<br><i>🏰 点击打开书签宫殿</i>")
            append("</html>")
        }
    }
}
