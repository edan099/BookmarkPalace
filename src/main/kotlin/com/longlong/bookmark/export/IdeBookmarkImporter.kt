package com.longlong.bookmark.export

import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.longlong.bookmark.model.Bookmark
import com.longlong.bookmark.model.BookmarkColor
import com.longlong.bookmark.model.BookmarkHistory
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.storage.BookmarkStorage

/**
 * IDE 自带书签导入器
 * 从 IntelliJ IDEA 内置书签系统导入到书签宫殿
 */
class IdeBookmarkImporter(private val project: Project) {

    private val bookmarkService = BookmarkService.getInstance(project)
    private val storage = BookmarkStorage.getInstance(project)

    /**
     * 从 IDE 书签导入
     * @param replace 是否替换现有书签（true=清空后导入，false=合并导入）
     * @return 导入结果
     */
    fun importFromIde(replace: Boolean = false): ImportResult {
        val errors = mutableListOf<String>()
        val importedBookmarks = mutableListOf<Bookmark>()

        try {
            // 获取 IDE 书签管理器
            val bookmarksManager = BookmarksManager.getInstance(project)
            if (bookmarksManager == null) {
                return ImportResult(errors = listOf("无法获取 IDE 书签管理器"))
            }

            // 遍历所有书签组
            val groups = bookmarksManager.groups
            for (group in groups) {
                val groupName = group.name

                // 遍历组内的书签
                for (bookmark in group.getBookmarks()) {
                    // 只处理行书签（LineBookmark）
                    if (bookmark is LineBookmark) {
                        val converted = convertLineBookmark(bookmark, groupName)
                        if (converted != null) {
                            importedBookmarks.add(converted)
                        } else {
                            errors.add("无法转换书签: ${bookmark.file.name}")
                        }
                    }
                    // 文件/目录书签暂时忽略，或可选择映射到第一行
                }
            }

            if (importedBookmarks.isEmpty()) {
                return ImportResult(errors = listOf("IDE 中没有可导入的行书签"))
            }

            // 保存到存储
            if (replace) {
                storage.saveBookmarks(importedBookmarks)
            } else {
                // 合并模式：避免重复
                val existingBookmarks = bookmarkService.getAllBookmarks().toMutableList()
                importedBookmarks.forEach { newBookmark ->
                    val existing = existingBookmarks.find {
                        it.filePath == newBookmark.filePath && it.startLine == newBookmark.startLine
                    }
                    if (existing == null) {
                        existingBookmarks.add(newBookmark)
                    }
                }
                storage.saveBookmarks(existingBookmarks)
            }

            // 刷新服务层
            bookmarkService.reloadFromStorage()

            return ImportResult(
                bookmarkCount = importedBookmarks.size,
                errors = errors
            )

        } catch (e: Exception) {
            return ImportResult(errors = listOf("导入失败: ${e.message}"))
        }
    }

    /**
     * 将 IDE 的 LineBookmark 转换为书签宫殿的 Bookmark
     */
    private fun convertLineBookmark(lineBookmark: LineBookmark, groupName: String): Bookmark? {
        val file = lineBookmark.file
        val line = lineBookmark.line  // 0-indexed

        // 获取相对路径
        val filePath = getRelativePath(file)

        // 尝试获取代码片段
        val codeSnippet = ReadAction.compute<String, Throwable> {
            try {
                val document = FileDocumentManager.getInstance().getDocument(file)
                if (document != null && line < document.lineCount) {
                    val startOffset = document.getLineStartOffset(line)
                    val endOffset = document.getLineEndOffset(line)
                    document.getText(TextRange(startOffset, endOffset))
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
        }

        // 生成别名
        val alias = generateAlias(codeSnippet, file.name, line)

        // 构建标签列表（使用组名作为标签）
        val tags = mutableListOf<String>()
        if (groupName.isNotBlank() && groupName != "Bookmarks") {
            tags.add(groupName)
        }

        return Bookmark(
            filePath = filePath,
            startLine = line,
            endLine = line,
            startOffset = 0,  // 后续会通过 RangeMarker 更新
            endOffset = 0,
            alias = alias,
            color = BookmarkColor.BLUE,  // IDE 书签没有颜色概念，使用默认蓝色
            tags = tags,
            comment = "从 IDE 书签导入",
            codeSnippet = codeSnippet,
            history = BookmarkHistory(
                originalSnippet = codeSnippet,
                originalStartLine = line,
                originalEndLine = line
            )
        )
    }

    /**
     * 获取文件相对于项目根目录的路径
     */
    private fun getRelativePath(file: VirtualFile): String {
        val basePath = project.basePath ?: return file.path
        return if (file.path.startsWith(basePath)) {
            file.path.substring(basePath.length + 1)
        } else {
            file.path
        }
    }

    /**
     * 生成书签别名
     */
    private fun generateAlias(codeSnippet: String, fileName: String, line: Int): String {
        val trimmed = codeSnippet.trim()
        return if (trimmed.isNotBlank() && trimmed.length <= 40) {
            trimmed
        } else if (trimmed.length > 40) {
            trimmed.take(37) + "..."
        } else {
            "$fileName:${line + 1}"
        }
    }

    /**
     * 获取 IDE 中可导入的书签数量（预览用）
     */
    fun getIdeBookmarkCount(): Int {
        return try {
            val bookmarksManager = BookmarksManager.getInstance(project) ?: return 0
            var count = 0
            for (group in bookmarksManager.groups) {
                count += group.getBookmarks().count { it is LineBookmark }
            }
            count
        } catch (e: Exception) {
            0
        }
    }
}
