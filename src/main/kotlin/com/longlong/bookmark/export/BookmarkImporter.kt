package com.longlong.bookmark.export

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.intellij.openapi.project.Project
import com.longlong.bookmark.model.*
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.service.DiagramService
import com.longlong.bookmark.service.TagService
import com.longlong.bookmark.storage.BookmarkStorage

/**
 * 导入结果
 */
data class ImportResult(
    val bookmarkCount: Int = 0,
    val diagramCount: Int = 0,
    val tagCount: Int = 0,
    val errors: List<String> = emptyList()
)

/**
 * 书签导入器
 */
class BookmarkImporter(private val project: Project) {

    private val bookmarkService = BookmarkService.getInstance(project)
    private val diagramService = DiagramService.getInstance(project)
    private val tagService = TagService.getInstance(project)
    private val storage = BookmarkStorage.getInstance(project)

    private val gson = Gson()

    /**
     * 导入书签数据
     */
    fun import(content: String, replace: Boolean = false): ImportResult {
        val trimmedContent = content.trim()

        return when {
            // 尝试解析为标准 JSON 格式
            trimmedContent.startsWith("{") -> importJson(trimmedContent, replace)

            // 尝试解析 AI 返回的格式（可能包含额外文字）
            trimmedContent.contains("\"bookmarks\"") -> {
                val jsonStart = trimmedContent.indexOf("{")
                val jsonEnd = trimmedContent.lastIndexOf("}") + 1
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    importJson(trimmedContent.substring(jsonStart, jsonEnd), replace)
                } else {
                    importFromText(trimmedContent, replace)
                }
            }

            // 尝试解析简单的文本格式
            else -> importFromText(trimmedContent, replace)
        }
    }

    /**
     * 从 JSON 导入
     */
    private fun importJson(jsonContent: String, replace: Boolean): ImportResult {
        return try {
            val exportData = gson.fromJson(jsonContent, ExportData::class.java)
            doImport(exportData, replace)
        } catch (e: JsonSyntaxException) {
            ImportResult(errors = listOf("JSON 解析失败: ${e.message}"))
        }
    }

    /**
     * 从文本导入（支持 AI 返回的格式）
     */
    private fun importFromText(text: String, replace: Boolean): ImportResult {
        val bookmarks = mutableListOf<Bookmark>()
        val errors = mutableListOf<String>()

        // 尝试解析每一行
        text.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty() || trimmedLine.startsWith("#") || trimmedLine.startsWith("//")) {
                return@forEach
            }

            // 尝试解析格式: "别名 文件路径:行号 [标签1,标签2] 注释"
            val bookmark = parseBookmarkLine(trimmedLine)
            if (bookmark != null) {
                bookmarks.add(bookmark)
            }
        }

        if (bookmarks.isEmpty()) {
            return ImportResult(errors = listOf("未能解析任何书签"))
        }

        // 如果是替换模式，先清空
        if (replace) {
            storage.saveBookmarks(emptyList())
        }

        // 添加书签
        val existingBookmarks = bookmarkService.getAllBookmarks().toMutableList()
        bookmarks.forEach { newBookmark ->
            // 检查是否已存在相同位置的书签
            val existing = existingBookmarks.find {
                it.filePath == newBookmark.filePath && it.startLine == newBookmark.startLine
            }
            if (existing == null) {
                existingBookmarks.add(newBookmark)
            }
        }

        storage.saveBookmarks(existingBookmarks)

        return ImportResult(
            bookmarkCount = bookmarks.size,
            errors = errors
        )
    }

    /**
     * 解析单行书签描述
     */
    private fun parseBookmarkLine(line: String): Bookmark? {
        // 支持多种格式:
        // 1. 标准格式: 别名 | 文件路径:行号 | 标签1,标签2 | 注释
        // 2. 简单格式: 文件路径:行号 注释
        // 3. AI 格式: - 别名 (文件名:行号) [标签]

        return try {
            when {
                line.contains("|") -> {
                    // 标准格式
                    val parts = line.split("|").map { it.trim() }
                    if (parts.size >= 2) {
                        val alias = parts[0]
                        val locationPart = parts[1]
                        val location = parseLocation(locationPart)
                        val tags = if (parts.size >= 3) parts[2].split(",").map { it.trim() } else emptyList()
                        val comment = if (parts.size >= 4) parts[3] else ""

                        if (location != null) {
                            Bookmark(
                                filePath = location.first,
                                startLine = location.second,
                                endLine = location.second,
                                alias = alias,
                                tags = tags.toMutableList(),
                                comment = comment
                            )
                        } else null
                    } else null
                }

                line.startsWith("-") -> {
                    // AI 格式: - 别名 (文件名:行号) [标签]
                    val content = line.removePrefix("-").trim()
                    val aliasMatch = Regex("^([^(\\[]+)").find(content)
                    val locationMatch = Regex("\\(([^)]+)\\)").find(content)
                    val tagsMatch = Regex("\\[([^]]+)]").find(content)

                    val alias = aliasMatch?.groupValues?.get(1)?.trim() ?: ""
                    val location = locationMatch?.groupValues?.get(1)?.let { parseLocation(it) }
                    val tags = tagsMatch?.groupValues?.get(1)?.split(",")?.map { it.trim() } ?: emptyList()

                    if (location != null) {
                        Bookmark(
                            filePath = location.first,
                            startLine = location.second,
                            endLine = location.second,
                            alias = alias,
                            tags = tags.toMutableList()
                        )
                    } else null
                }

                else -> {
                    // 简单格式: 文件路径:行号 注释
                    val parts = line.split("\\s+".toRegex(), limit = 2)
                    val location = parseLocation(parts[0])
                    val comment = if (parts.size > 1) parts[1] else ""

                    if (location != null) {
                        Bookmark(
                            filePath = location.first,
                            startLine = location.second,
                            endLine = location.second,
                            alias = location.first.substringAfterLast("/"),
                            comment = comment
                        )
                    } else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析位置信息 (文件路径:行号)
     */
    private fun parseLocation(location: String): Pair<String, Int>? {
        val lastColon = location.lastIndexOf(":")
        if (lastColon <= 0) return null

        val filePath = location.substring(0, lastColon)
        val lineNumber = location.substring(lastColon + 1).toIntOrNull() ?: return null

        return Pair(filePath, lineNumber - 1) // 转为0-indexed
    }

    /**
     * 执行导入
     */
    private fun doImport(exportData: ExportData, replace: Boolean): ImportResult {
        val errors = mutableListOf<String>()
        var bookmarkCount = 0
        var diagramCount = 0
        var tagCount = 0

        // 导入标签
        if (exportData.tags.isNotEmpty()) {
            val newTags = exportData.tags.map { TagDto.toTag(it) }
            if (replace) {
                storage.saveTags(newTags)
            } else {
                val existingTags = tagService.getAllTags().toMutableList()
                newTags.forEach { newTag ->
                    if (existingTags.none { it.name == newTag.name }) {
                        existingTags.add(newTag)
                    }
                }
                storage.saveTags(existingTags)
            }
            tagCount = newTags.size
        }

        // 导入书签
        if (exportData.bookmarks.isNotEmpty()) {
            val newBookmarks = exportData.bookmarks.map { BookmarkDto.toBookmark(it) }
            if (replace) {
                storage.saveBookmarks(newBookmarks)
            } else {
                val existingBookmarks = bookmarkService.getAllBookmarks().toMutableList()
                newBookmarks.forEach { newBookmark ->
                    val existing = existingBookmarks.find {
                        it.filePath == newBookmark.filePath && it.startLine == newBookmark.startLine
                    }
                    if (existing == null) {
                        existingBookmarks.add(newBookmark)
                    }
                }
                storage.saveBookmarks(existingBookmarks)
            }
            bookmarkCount = newBookmarks.size
        }

        // 导入导览图
        if (exportData.diagrams.isNotEmpty()) {
            val newDiagrams = exportData.diagrams.map { DiagramDto.toDiagram(it) }
            if (replace) {
                storage.saveDiagrams(newDiagrams)
            } else {
                val existingDiagrams = diagramService.getAllDiagrams().toMutableList()
                newDiagrams.forEach { newDiagram ->
                    val existing = existingDiagrams.find { it.name == newDiagram.name }
                    if (existing == null) {
                        existingDiagrams.add(newDiagram)
                    }
                }
                storage.saveDiagrams(existingDiagrams)
            }
            diagramCount = newDiagrams.size
        }

        return ImportResult(
            bookmarkCount = bookmarkCount,
            diagramCount = diagramCount,
            tagCount = tagCount,
            errors = errors
        )
    }
}
