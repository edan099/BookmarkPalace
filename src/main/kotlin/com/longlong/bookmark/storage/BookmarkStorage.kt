package com.longlong.bookmark.storage

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.longlong.bookmark.model.*

/**
 * 书签存储状态
 */
@Tag("BookmarkState")
class BookmarkState {
    @XCollection(style = XCollection.Style.v2)
    var bookmarks: MutableList<BookmarkStorageItem> = mutableListOf()

    @XCollection(style = XCollection.Style.v2)
    var tags: MutableList<TagStorageItem> = mutableListOf()

    @XCollection(style = XCollection.Style.v2)
    var tagGroups: MutableList<TagGroupStorageItem> = mutableListOf()

    @XCollection(style = XCollection.Style.v2)
    var diagrams: MutableList<DiagramStorageItem> = mutableListOf()
}

/**
 * 书签存储项
 */
@Tag("Bookmark")
class BookmarkStorageItem {
    var id: String = ""
    var filePath: String = ""
    var startLine: Int = 0
    var endLine: Int = 0
    var startOffset: Int = 0
    var endOffset: Int = 0
    var alias: String = ""
    var color: String = "BLUE"
    var comment: String = ""
    var status: String = "VALID"
    var codeSnippet: String = ""
    var originalSnippet: String = ""
    var originalStartLine: Int = 0
    var originalEndLine: Int = 0
    var createdAt: Long = 0
    var updatedAt: Long = 0

    @XCollection(style = XCollection.Style.v2, elementName = "tag")
    var tags: MutableList<String> = mutableListOf()

    fun toBookmark(): Bookmark {
        return Bookmark(
            id = id,
            filePath = filePath,
            startLine = startLine,
            endLine = endLine,
            startOffset = startOffset,
            endOffset = endOffset,
            alias = alias,
            color = BookmarkColor.fromName(color),
            tags = tags.toMutableList(),
            comment = comment,
            status = try { BookmarkStatus.valueOf(status) } catch (e: Exception) { BookmarkStatus.VALID },
            history = BookmarkHistory(
                originalSnippet = originalSnippet,
                originalStartLine = originalStartLine,
                originalEndLine = originalEndLine,
                createdAt = createdAt,
                updatedAt = updatedAt
            ),
            codeSnippet = codeSnippet,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromBookmark(bookmark: Bookmark): BookmarkStorageItem {
            return BookmarkStorageItem().apply {
                id = bookmark.id
                filePath = bookmark.filePath
                startLine = bookmark.startLine
                endLine = bookmark.endLine
                startOffset = bookmark.startOffset
                endOffset = bookmark.endOffset
                alias = bookmark.alias
                color = bookmark.color.name
                tags = bookmark.tags.toMutableList()
                comment = bookmark.comment
                status = bookmark.status.name
                codeSnippet = bookmark.codeSnippet
                originalSnippet = bookmark.history.originalSnippet
                originalStartLine = bookmark.history.originalStartLine
                originalEndLine = bookmark.history.originalEndLine
                createdAt = bookmark.createdAt
                updatedAt = bookmark.updatedAt
            }
        }
    }
}

/**
 * 标签存储项
 */
@Tag("Tag")
class TagStorageItem {
    var id: String = ""
    var name: String = ""
    var color: String = "#1E88E5"
    var groupId: String? = null
    var description: String = ""
    var order: Int = 0
    var createdAt: Long = 0

    fun toTag(): com.longlong.bookmark.model.Tag {
        return com.longlong.bookmark.model.Tag(
            id = id,
            name = name,
            color = color,
            groupId = groupId,
            description = description,
            order = order,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromTag(tag: com.longlong.bookmark.model.Tag): TagStorageItem {
            return TagStorageItem().apply {
                id = tag.id
                name = tag.name
                color = tag.color
                groupId = tag.groupId
                description = tag.description
                order = tag.order
                createdAt = tag.createdAt
            }
        }
    }
}

/**
 * 标签分组存储项
 */
@Tag("TagGroup")
class TagGroupStorageItem {
    var id: String = ""
    var name: String = ""
    var description: String = ""
    var order: Int = 0

    fun toTagGroup(): TagGroup {
        return TagGroup(
            id = id,
            name = name,
            description = description,
            order = order
        )
    }

    companion object {
        fun fromTagGroup(group: TagGroup): TagGroupStorageItem {
            return TagGroupStorageItem().apply {
                id = group.id
                name = group.name
                description = group.description
                order = group.order
            }
        }
    }
}

/**
 * 导览图存储项
 */
@Tag("Diagram")
class DiagramStorageItem {
    var id: String = ""
    var name: String = ""
    var description: String = ""
    var type: String = "CUSTOM_FLOW"
    var createdAt: Long = 0
    var updatedAt: Long = 0

    @XCollection(style = XCollection.Style.v2)
    var nodes: MutableList<DiagramNodeStorageItem> = mutableListOf()

    @XCollection(style = XCollection.Style.v2)
    var connections: MutableList<DiagramConnectionStorageItem> = mutableListOf()

    fun toDiagram(): Diagram {
        return Diagram(
            id = id,
            name = name,
            description = description,
            type = try { DiagramType.valueOf(type) } catch (e: Exception) { DiagramType.CUSTOM_FLOW },
            nodes = nodes.map { it.toNode() }.toMutableList(),
            connections = connections.map { it.toConnection() }.toMutableList(),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDiagram(diagram: Diagram): DiagramStorageItem {
            return DiagramStorageItem().apply {
                id = diagram.id
                name = diagram.name
                description = diagram.description
                type = diagram.type.name
                nodes = diagram.nodes.map { DiagramNodeStorageItem.fromNode(it) }.toMutableList()
                connections = diagram.connections.map { DiagramConnectionStorageItem.fromConnection(it) }.toMutableList()
                createdAt = diagram.createdAt
                updatedAt = diagram.updatedAt
            }
        }
    }
}

/**
 * 导览图节点存储项
 */
@Tag("Node")
class DiagramNodeStorageItem {
    var id: String = ""
    var bookmarkId: String? = null
    var label: String = ""
    var description: String = ""
    var shape: String = "RECTANGLE"
    var x: Double = 0.0
    var y: Double = 0.0
    var width: Double = 120.0
    var height: Double = 50.0
    var color: String = "#4A90D9"
    var textColor: String = "#FFFFFF"
    var borderColor: String = "#333333"
    var fontSize: Int = 12
    var borderWidth: Float = 1.5f

    fun toNode(): DiagramNode {
        return DiagramNode(
            id = id, bookmarkId = bookmarkId, label = label, description = description,
            shape = try { NodeShape.valueOf(shape) } catch (e: Exception) { NodeShape.RECTANGLE },
            x = x, y = y, width = width, height = height, color = color,
            textColor = textColor, borderColor = borderColor, fontSize = fontSize, borderWidth = borderWidth
        )
    }

    companion object {
        fun fromNode(node: DiagramNode): DiagramNodeStorageItem {
            return DiagramNodeStorageItem().apply {
                id = node.id; bookmarkId = node.bookmarkId; label = node.label; description = node.description
                shape = node.shape.name; x = node.x; y = node.y; width = node.width; height = node.height
                color = node.color; textColor = node.textColor; borderColor = node.borderColor
                fontSize = node.fontSize; borderWidth = node.borderWidth
            }
        }
    }
}

/**
 * 导览图连线存储项
 */
@Tag("Connection")
class DiagramConnectionStorageItem {
    var id: String = ""
    var sourceNodeId: String = ""
    var targetNodeId: String = ""
    var connectionType: String = "ARROW"
    var label: String = ""
    var lineColor: String = "#666666"
    var lineWidth: Float = 2f
    var fontSize: Int = 11
    var curveOffset: Double = 0.0

    fun toConnection(): DiagramConnection {
        return DiagramConnection(
            id = id, sourceNodeId = sourceNodeId, targetNodeId = targetNodeId,
            connectionType = try { ConnectionType.valueOf(connectionType) } catch (e: Exception) { ConnectionType.ARROW },
            label = label, lineColor = lineColor, lineWidth = lineWidth, fontSize = fontSize, curveOffset = curveOffset
        )
    }

    companion object {
        fun fromConnection(conn: DiagramConnection): DiagramConnectionStorageItem {
            return DiagramConnectionStorageItem().apply {
                id = conn.id; sourceNodeId = conn.sourceNodeId; targetNodeId = conn.targetNodeId
                connectionType = conn.connectionType.name; label = conn.label
                lineColor = conn.lineColor; lineWidth = conn.lineWidth; fontSize = conn.fontSize; curveOffset = conn.curveOffset
            }
        }
    }
}

/**
 * 书签持久化存储服务
 */
@Service(Service.Level.PROJECT)
@State(
    name = "LongLongBookmarkStorage",
    storages = [Storage("longlong-bookmarks.xml")]
)
class BookmarkStorage : PersistentStateComponent<BookmarkState> {
    private var state = BookmarkState()

    override fun getState(): BookmarkState = state

    override fun loadState(state: BookmarkState) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    /**
     * 获取所有书签
     */
    fun getBookmarks(): List<Bookmark> {
        return state.bookmarks.map { it.toBookmark() }
    }

    /**
     * 保存书签列表
     */
    fun saveBookmarks(bookmarks: List<Bookmark>) {
        state.bookmarks.clear()
        state.bookmarks.addAll(bookmarks.map { BookmarkStorageItem.fromBookmark(it) })
    }

    /**
     * 获取所有标签
     */
    fun getTags(): List<com.longlong.bookmark.model.Tag> {
        return state.tags.map { it.toTag() }
    }

    /**
     * 保存标签列表
     */
    fun saveTags(tags: List<com.longlong.bookmark.model.Tag>) {
        state.tags.clear()
        state.tags.addAll(tags.map { TagStorageItem.fromTag(it) })
    }

    /**
     * 获取所有标签分组
     */
    fun getTagGroups(): List<TagGroup> {
        return state.tagGroups.map { it.toTagGroup() }
    }

    /**
     * 保存标签分组列表
     */
    fun saveTagGroups(groups: List<TagGroup>) {
        state.tagGroups.clear()
        state.tagGroups.addAll(groups.map { TagGroupStorageItem.fromTagGroup(it) })
    }

    /**
     * 获取所有导览图
     */
    fun getDiagrams(): List<Diagram> {
        return state.diagrams.map { it.toDiagram() }
    }

    /**
     * 保存导览图列表
     */
    fun saveDiagrams(diagrams: List<Diagram>) {
        state.diagrams.clear()
        state.diagrams.addAll(diagrams.map { DiagramStorageItem.fromDiagram(it) })
    }

    companion object {
        fun getInstance(project: Project): BookmarkStorage {
            return project.getService(BookmarkStorage::class.java)
        }
    }
}
