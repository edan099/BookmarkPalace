package com.longlong.bookmark.model

import java.util.UUID

/**
 * 书签状态枚举
 */
enum class BookmarkStatus {
    VALID,      // 有效
    MISSING,    // 代码已删除，找不到位置
    OUTDATED    // 代码已变更，可能需要更新
}

/**
 * 书签颜色枚举
 */
enum class BookmarkColor(val displayName: String, val hexColor: String) {
    RED("红色", "#E53935"),
    ORANGE("橙色", "#FB8C00"),
    YELLOW("黄色", "#FDD835"),
    GREEN("绿色", "#43A047"),
    BLUE("蓝色", "#1E88E5"),
    PURPLE("紫色", "#8E24AA"),
    PINK("粉色", "#D81B60"),
    CYAN("青色", "#00ACC1"),
    GRAY("灰色", "#757575");

    companion object {
        fun fromName(name: String): BookmarkColor {
            return values().find { it.name.equals(name, ignoreCase = true) } ?: BLUE
        }
    }
}

/**
 * 书签历史信息
 */
data class BookmarkHistory(
    val originalSnippet: String = "",       // 原始代码片段
    val originalStartLine: Int = 0,         // 原始起始行
    val originalEndLine: Int = 0,           // 原始结束行
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 书签数据模型
 */
data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    var filePath: String = "",              // 文件路径（相对于项目根目录）
    var startLine: Int = 0,                 // 起始行号（0-indexed）
    var endLine: Int = 0,                   // 结束行号（0-indexed）
    var startOffset: Int = 0,               // 起始偏移量
    var endOffset: Int = 0,                 // 结束偏移量
    var alias: String = "",                 // 别名/标题
    var color: BookmarkColor = BookmarkColor.BLUE,  // 颜色
    var tags: MutableList<String> = mutableListOf(),  // 标签列表
    var comment: String = "",               // 注释/备注
    var status: BookmarkStatus = BookmarkStatus.VALID,  // 状态
    var history: BookmarkHistory = BookmarkHistory(),   // 历史信息
    var codeSnippet: String = "",           // 当前代码片段
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 获取显示名称
     */
    fun getDisplayName(): String {
        return alias.ifBlank { 
            codeSnippet.lines().firstOrNull()?.trim()?.take(30) ?: "未命名书签"
        }
    }

    /**
     * 获取文件名
     */
    fun getFileName(): String {
        return filePath.substringAfterLast("/").substringAfterLast("\\")
    }

    /**
     * 获取位置描述
     */
    fun getLocationDescription(): String {
        return "${getFileName()}:${startLine + 1}"
    }

    /**
     * 是否有效
     */
    fun isValid(): Boolean = status == BookmarkStatus.VALID

    /**
     * 更新时间戳
     */
    fun touch() {
        updatedAt = System.currentTimeMillis()
    }

    /**
     * 标记为失效
     */
    fun markAsMissing() {
        if (status == BookmarkStatus.VALID) {
            history = history.copy(
                originalSnippet = codeSnippet,
                originalStartLine = startLine,
                originalEndLine = endLine,
                updatedAt = System.currentTimeMillis()
            )
        }
        status = BookmarkStatus.MISSING
        touch()
    }

    /**
     * 标记为有效
     */
    fun markAsValid() {
        status = BookmarkStatus.VALID
        touch()
    }
    
    /**
     * 标记为过期（位置可能已变化）
     */
    fun markAsOutdated() {
        status = BookmarkStatus.OUTDATED
        touch()
    }

    /**
     * 复制书签
     */
    fun copy(): Bookmark {
        return Bookmark(
            id = UUID.randomUUID().toString(),
            filePath = filePath,
            startLine = startLine,
            endLine = endLine,
            startOffset = startOffset,
            endOffset = endOffset,
            alias = alias,
            color = color,
            tags = tags.toMutableList(),
            comment = comment,
            status = status,
            history = history.copy(),
            codeSnippet = codeSnippet,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}

/**
 * 书签数据传输对象（用于序列化）
 */
data class BookmarkDto(
    val id: String,
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val startOffset: Int,
    val endOffset: Int,
    val alias: String,
    val color: String,
    val tags: List<String>,
    val comment: String,
    val status: String,
    val originalSnippet: String,
    val originalStartLine: Int,
    val originalEndLine: Int,
    val codeSnippet: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        fun fromBookmark(bookmark: Bookmark): BookmarkDto {
            return BookmarkDto(
                id = bookmark.id,
                filePath = bookmark.filePath,
                startLine = bookmark.startLine,
                endLine = bookmark.endLine,
                startOffset = bookmark.startOffset,
                endOffset = bookmark.endOffset,
                alias = bookmark.alias,
                color = bookmark.color.name,
                tags = bookmark.tags.toList(),
                comment = bookmark.comment,
                status = bookmark.status.name,
                originalSnippet = bookmark.history.originalSnippet,
                originalStartLine = bookmark.history.originalStartLine,
                originalEndLine = bookmark.history.originalEndLine,
                codeSnippet = bookmark.codeSnippet,
                createdAt = bookmark.createdAt,
                updatedAt = bookmark.updatedAt
            )
        }

        fun toBookmark(dto: BookmarkDto): Bookmark {
            return Bookmark(
                id = dto.id,
                filePath = dto.filePath,
                startLine = dto.startLine,
                endLine = dto.endLine,
                startOffset = dto.startOffset,
                endOffset = dto.endOffset,
                alias = dto.alias,
                color = BookmarkColor.fromName(dto.color),
                tags = dto.tags.toMutableList(),
                comment = dto.comment,
                status = BookmarkStatus.valueOf(dto.status),
                history = BookmarkHistory(
                    originalSnippet = dto.originalSnippet,
                    originalStartLine = dto.originalStartLine,
                    originalEndLine = dto.originalEndLine,
                    createdAt = dto.createdAt,
                    updatedAt = dto.updatedAt
                ),
                codeSnippet = dto.codeSnippet,
                createdAt = dto.createdAt,
                updatedAt = dto.updatedAt
            )
        }
    }
}
