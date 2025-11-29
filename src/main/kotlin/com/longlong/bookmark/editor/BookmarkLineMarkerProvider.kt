package com.longlong.bookmark.editor

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.longlong.bookmark.model.Bookmark
import com.longlong.bookmark.model.BookmarkStatus
import com.longlong.bookmark.service.BookmarkService
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * 书签行标记提供者 - 在 Gutter 区域显示书签图标
 * 每行只显示一个图标，如果同一行有多个书签则合并显示
 */
class BookmarkLineMarkerProvider : LineMarkerProvider {

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
        if (bookmarks.isEmpty()) return

        // 按行分组，每行只显示一个图标
        val bookmarksByLine = bookmarks.groupBy { it.startLine }
        val processedLines = mutableSetOf<Int>()

        // 只处理传入的 elements，避免重复添加同一行的标记
        for (element in elements) {
            val lineNumber = document.getLineNumber(element.textRange.startOffset)
            
            // 如果该行已处理过，跳过
            if (processedLines.contains(lineNumber)) continue
            
            // 检查该行是否有书签
            val lineBookmarks = bookmarksByLine[lineNumber]
            if (lineBookmarks != null && lineBookmarks.isNotEmpty()) {
                // 如果同一行有多个书签，使用第一个的颜色，tooltip 显示所有
                val primaryBookmark = lineBookmarks.first()
                result.add(createLineMarkerInfo(element, primaryBookmark, lineBookmarks, project))
                processedLines.add(lineNumber)
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
                // 点击跳转到第一个书签
                BookmarkService.getInstance(project).navigateToBookmark(primaryBookmark)
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

        // 绘制书签形状
        g2d.color = color
        val path = java.awt.geom.Path2D.Double()
        path.moveTo(2.0, 1.0)
        path.lineTo(10.0, 1.0)
        path.lineTo(10.0, 11.0)
        path.lineTo(6.0, 8.0)
        path.lineTo(2.0, 11.0)
        path.closePath()
        g2d.fill(path)

        // 如果失效，添加 X 标记
        if (bookmark.status == BookmarkStatus.MISSING) {
            g2d.color = Color.WHITE
            g2d.stroke = BasicStroke(1.5f)
            g2d.drawLine(4, 3, 8, 7)
            g2d.drawLine(8, 3, 4, 7)
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
            
            append("<br><i>点击跳转</i>")
            append("</html>")
        }
    }
}
