package com.longlong.bookmark.export

import com.google.gson.GsonBuilder
import com.intellij.openapi.project.Project
import com.longlong.bookmark.model.*
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.service.DiagramService
import com.longlong.bookmark.service.TagService

/**
 * 导出格式
 */
enum class ExportFormat(val displayName: String, val extension: String) {
    JSON("JSON (完整配置)", "json"),
    MARKDOWN("Markdown (文档)", "md"),
    MERMAID("Mermaid (流程图)", "mmd")
}

/**
 * 导出数据结构
 */
data class ExportData(
    val version: String = "1.0",
    val projectName: String = "",
    val exportedAt: Long = System.currentTimeMillis(),
    val bookmarks: List<BookmarkDto> = emptyList(),
    val diagrams: List<DiagramDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val tagGroups: List<TagGroupDto> = emptyList()
)

/**
 * 书签导出器
 */
class BookmarkExporter(private val project: Project) {

    private val bookmarkService = BookmarkService.getInstance(project)
    private val diagramService = DiagramService.getInstance(project)
    private val tagService = TagService.getInstance(project)

    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 导出书签
     */
    fun export(
        format: ExportFormat,
        includeBookmarks: Boolean = true,
        includeDiagrams: Boolean = true,
        includeTags: Boolean = true
    ): String {
        return when (format) {
            ExportFormat.JSON -> exportJson(includeBookmarks, includeDiagrams, includeTags)
            ExportFormat.MARKDOWN -> exportMarkdown(includeBookmarks, includeDiagrams)
            ExportFormat.MERMAID -> exportMermaid()
        }
    }

    /**
     * 导出为 JSON
     */
    private fun exportJson(
        includeBookmarks: Boolean,
        includeDiagrams: Boolean,
        includeTags: Boolean
    ): String {
        val exportData = ExportData(
            projectName = project.name,
            bookmarks = if (includeBookmarks) {
                bookmarkService.getAllBookmarks().map { BookmarkDto.fromBookmark(it) }
            } else emptyList(),
            diagrams = if (includeDiagrams) {
                diagramService.getAllDiagrams().map { DiagramDto.fromDiagram(it) }
            } else emptyList(),
            tags = if (includeTags) {
                tagService.getAllTags().map { TagDto.fromTag(it) }
            } else emptyList(),
            tagGroups = if (includeTags) {
                tagService.getAllGroups().map { TagGroupDto.fromTagGroup(it) }
            } else emptyList()
        )

        return gson.toJson(exportData)
    }

    /**
     * 导出为 Markdown
     */
    private fun exportMarkdown(includeBookmarks: Boolean, includeDiagrams: Boolean): String {
        val sb = StringBuilder()

        sb.appendLine("# 🐉 龙龙书签导出")
        sb.appendLine()
        sb.appendLine("项目: ${project.name}")
        sb.appendLine("导出时间: ${java.time.LocalDateTime.now()}")
        sb.appendLine()

        if (includeBookmarks) {
            sb.appendLine("## 📚 书签列表")
            sb.appendLine()

            // 按文件分组
            val bookmarksByFile = bookmarkService.getAllBookmarks().groupBy { it.filePath }

            bookmarksByFile.forEach { (filePath, bookmarks) ->
                sb.appendLine("### 📄 $filePath")
                sb.appendLine()

                bookmarks.sortedBy { it.startLine }.forEach { bookmark ->
                    val statusIcon = when (bookmark.status) {
                        BookmarkStatus.VALID -> "✅"
                        BookmarkStatus.MISSING -> "❌"
                        BookmarkStatus.OUTDATED -> "⚠️"
                    }
                    val tags = if (bookmark.tags.isNotEmpty()) {
                        " `${bookmark.tags.joinToString("` `")}`"
                    } else ""

                    sb.appendLine("- $statusIcon **${bookmark.alias}** (行 ${bookmark.startLine + 1})$tags")

                    if (bookmark.comment.isNotEmpty()) {
                        sb.appendLine("  - 注释: ${bookmark.comment}")
                    }

                    sb.appendLine("  ```")
                    bookmark.codeSnippet.lines().take(5).forEach { line ->
                        sb.appendLine("  $line")
                    }
                    if (bookmark.codeSnippet.lines().size > 5) {
                        sb.appendLine("  // ... (${bookmark.codeSnippet.lines().size - 5} more lines)")
                    }
                    sb.appendLine("  ```")
                    sb.appendLine()
                }
            }
        }

        if (includeDiagrams) {
            sb.appendLine("## 🗺️ 导览图")
            sb.appendLine()

            diagramService.getAllDiagrams().forEach { diagram ->
                sb.appendLine("### ${diagram.name}")
                sb.appendLine()
                sb.appendLine("类型: ${diagram.type.displayName}")
                sb.appendLine("节点数: ${diagram.nodes.size}")
                sb.appendLine("连线数: ${diagram.connections.size}")
                sb.appendLine()

                // 生成简单的流程描述
                if (diagram.nodes.isNotEmpty()) {
                    sb.appendLine("**节点列表:**")
                    diagram.nodes.forEach { node ->
                        sb.appendLine("- ${node.label}")
                    }
                    sb.appendLine()

                    sb.appendLine("**连接关系:**")
                    diagram.connections.forEach { conn ->
                        val sourceNode = diagram.getNode(conn.sourceNodeId)
                        val targetNode = diagram.getNode(conn.targetNodeId)
                        if (sourceNode != null && targetNode != null) {
                            val label = if (conn.label.isNotEmpty()) " (${conn.label})" else ""
                            sb.appendLine("- ${sourceNode.label} → ${targetNode.label}$label")
                        }
                    }
                    sb.appendLine()
                }
            }
        }

        return sb.toString()
    }

    /**
     * 导出为 Mermaid 流程图
     */
    private fun exportMermaid(): String {
        val sb = StringBuilder()

        sb.appendLine("```mermaid")
        sb.appendLine("flowchart TD")
        sb.appendLine()

        // 导出所有导览图
        diagramService.getAllDiagrams().forEach { diagram ->
            sb.appendLine("    %% ${diagram.name}")

            // 节点定义
            diagram.nodes.forEach { node ->
                val nodeId = "N${node.id.take(8)}"
                val shape = when (node.shape) {
                    NodeShape.RECTANGLE -> "[${node.label}]"
                    NodeShape.ROUNDED_RECT -> "([${node.label}])"
                    NodeShape.CIRCLE -> "((${node.label}))"
                    NodeShape.ELLIPSE -> "([${node.label}])"
                    NodeShape.DIAMOND -> "{${node.label}}"
                }
                sb.appendLine("    $nodeId$shape")
            }

            sb.appendLine()

            // 连线
            diagram.connections.forEach { conn ->
                val sourceId = "N${conn.sourceNodeId.take(8)}"
                val targetId = "N${conn.targetNodeId.take(8)}"
                val arrow = when (conn.connectionType) {
                    ConnectionType.NORMAL, ConnectionType.ARROW -> "-->"
                    ConnectionType.DASHED -> "-.->"
                }
                val label = if (conn.label.isNotEmpty()) "-->|${conn.label}|" else arrow
                sb.appendLine("    $sourceId $label $targetId")
            }

            sb.appendLine()
        }

        // 如果没有导览图，根据书签生成简单的文件结构图
        if (diagramService.getAllDiagrams().all { it.nodes.isEmpty() }) {
            sb.appendLine("    %% 书签概览")

            val bookmarksByFile = bookmarkService.getAllBookmarks().groupBy { it.filePath }
            var nodeIndex = 0

            bookmarksByFile.forEach { (filePath, bookmarks) ->
                val fileId = "F${nodeIndex++}"
                val fileName = filePath.substringAfterLast("/")
                sb.appendLine("    $fileId[$fileName]")

                bookmarks.forEach { bookmark ->
                    val bookmarkId = "B${nodeIndex++}"
                    sb.appendLine("    $bookmarkId[${bookmark.alias}]")
                    sb.appendLine("    $fileId --> $bookmarkId")
                }
            }
        }

        sb.appendLine("```")

        return sb.toString()
    }

    /**
     * 导出特定书签
     */
    fun exportBookmarks(bookmarks: List<Bookmark>): String {
        val exportData = ExportData(
            projectName = project.name,
            bookmarks = bookmarks.map { BookmarkDto.fromBookmark(it) }
        )
        return gson.toJson(exportData)
    }

    /**
     * 导出特定导览图
     */
    fun exportDiagram(diagram: Diagram): String {
        val exportData = ExportData(
            projectName = project.name,
            diagrams = listOf(DiagramDto.fromDiagram(diagram))
        )
        return gson.toJson(exportData)
    }
}
