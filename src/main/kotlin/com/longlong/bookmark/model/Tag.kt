package com.longlong.bookmark.model

import java.util.UUID

/**
 * 标签分组
 */
data class TagGroup(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var description: String = "",
    var order: Int = 0
)

/**
 * 标签数据模型
 */
data class Tag(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var color: String = "#1E88E5",   // 十六进制颜色
    var groupId: String? = null,      // 所属分组ID
    var description: String = "",
    var order: Int = 0,
    var createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        // 预设标签
        val PRESET_TAGS = listOf(
            Tag(name = "入口", color = "#43A047", description = "程序入口点"),
            Tag(name = "核心逻辑", color = "#E53935", description = "核心业务逻辑"),
            Tag(name = "异常处理", color = "#FB8C00", description = "异常处理代码"),
            Tag(name = "数据校验", color = "#FDD835", description = "数据校验逻辑"),
            Tag(name = "RPC调用", color = "#8E24AA", description = "远程调用"),
            Tag(name = "数据库操作", color = "#00ACC1", description = "数据库相关"),
            Tag(name = "缓存", color = "#D81B60", description = "缓存相关"),
            Tag(name = "待优化", color = "#757575", description = "需要优化的代码"),
            Tag(name = "TODO", color = "#FF5722", description = "待完成"),
            Tag(name = "BUG", color = "#F44336", description = "已知问题")
        )
    }
}

/**
 * 标签数据传输对象
 */
data class TagDto(
    val id: String,
    val name: String,
    val color: String,
    val groupId: String?,
    val description: String,
    val order: Int,
    val createdAt: Long
) {
    companion object {
        fun fromTag(tag: Tag): TagDto {
            return TagDto(
                id = tag.id,
                name = tag.name,
                color = tag.color,
                groupId = tag.groupId,
                description = tag.description,
                order = tag.order,
                createdAt = tag.createdAt
            )
        }

        fun toTag(dto: TagDto): Tag {
            return Tag(
                id = dto.id,
                name = dto.name,
                color = dto.color,
                groupId = dto.groupId,
                description = dto.description,
                order = dto.order,
                createdAt = dto.createdAt
            )
        }
    }
}

/**
 * 标签分组数据传输对象
 */
data class TagGroupDto(
    val id: String,
    val name: String,
    val description: String,
    val order: Int
) {
    companion object {
        fun fromTagGroup(group: TagGroup): TagGroupDto {
            return TagGroupDto(
                id = group.id,
                name = group.name,
                description = group.description,
                order = group.order
            )
        }

        fun toTagGroup(dto: TagGroupDto): TagGroup {
            return TagGroup(
                id = dto.id,
                name = dto.name,
                description = dto.description,
                order = dto.order
            )
        }
    }
}
