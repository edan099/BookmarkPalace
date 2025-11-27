package com.longlong.bookmark.model

import java.util.UUID

/**
 * 导览图类型
 */
enum class DiagramType(val displayName: String) {
    MAIN_FLOW("主流程"),
    CALL_FLOW("调用流程"),
    TAG_FLOW("标签视图"),
    CUSTOM_FLOW("自定义")
}

/**
 * 导览图布局类型
 */
enum class DiagramLayout(val displayName: String) {
    TREE("树形布局"),
    DAG("有向无环图"),
    HORIZONTAL("水平布局"),
    FORCE("力导向布局")
}

/**
 * 节点连线类型
 */
enum class ConnectionType(val displayName: String) {
    NORMAL("普通"),
    CONDITION_YES("条件-是"),
    CONDITION_NO("条件-否"),
    LOOP("循环"),
    EXCEPTION("异常")
}

/**
 * 节点类型
 */
enum class NodeType(val displayName: String) {
    NORMAL("普通节点"),
    START("起始节点"),
    END("结束节点"),
    DECISION("判断节点"),
    LOOP("循环节点"),
    EXCEPTION("异常节点")
}

/**
 * 导览图节点
 */
data class DiagramNode(
    val id: String = UUID.randomUUID().toString(),
    var bookmarkId: String? = null,     // 关联的书签ID（可选）
    var label: String = "",              // 节点显示标签
    var description: String = "",        // 节点描述
    var nodeType: NodeType = NodeType.NORMAL,
    var x: Double = 0.0,                 // X 坐标
    var y: Double = 0.0,                 // Y 坐标
    var width: Double = 150.0,           // 宽度
    var height: Double = 60.0,           // 高度
    var color: String? = null,           // 节点颜色（继承自书签或自定义）
    var showCode: Boolean = false,       // 是否显示代码片段
    var collapsed: Boolean = true        // 代码片段是否折叠
)

/**
 * 导览图连线
 */
data class DiagramConnection(
    val id: String = UUID.randomUUID().toString(),
    var sourceNodeId: String = "",       // 源节点ID
    var targetNodeId: String = "",       // 目标节点ID
    var connectionType: ConnectionType = ConnectionType.NORMAL,
    var label: String = ""               // 连线标签（如 yes/no）
)

/**
 * 导览图
 */
data class Diagram(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var description: String = "",
    var type: DiagramType = DiagramType.CUSTOM_FLOW,
    var layout: DiagramLayout = DiagramLayout.TREE,
    var nodes: MutableList<DiagramNode> = mutableListOf(),
    var connections: MutableList<DiagramConnection> = mutableListOf(),
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    /**
     * 添加节点
     */
    fun addNode(node: DiagramNode) {
        nodes.add(node)
        touch()
    }

    /**
     * 移除节点
     */
    fun removeNode(nodeId: String) {
        nodes.removeIf { it.id == nodeId }
        // 同时移除相关连线
        connections.removeIf { it.sourceNodeId == nodeId || it.targetNodeId == nodeId }
        touch()
    }

    /**
     * 添加连线
     */
    fun addConnection(connection: DiagramConnection) {
        connections.add(connection)
        touch()
    }

    /**
     * 移除连线
     */
    fun removeConnection(connectionId: String) {
        connections.removeIf { it.id == connectionId }
        touch()
    }

    /**
     * 从书签创建节点
     */
    fun createNodeFromBookmark(bookmark: Bookmark): DiagramNode {
        return DiagramNode(
            bookmarkId = bookmark.id,
            label = bookmark.getDisplayName(),
            description = bookmark.comment,
            color = bookmark.color.hexColor
        )
    }

    /**
     * 更新时间戳
     */
    fun touch() {
        updatedAt = System.currentTimeMillis()
    }

    /**
     * 获取节点
     */
    fun getNode(nodeId: String): DiagramNode? {
        return nodes.find { it.id == nodeId }
    }

    /**
     * 获取节点的出边
     */
    fun getOutgoingConnections(nodeId: String): List<DiagramConnection> {
        return connections.filter { it.sourceNodeId == nodeId }
    }

    /**
     * 获取节点的入边
     */
    fun getIncomingConnections(nodeId: String): List<DiagramConnection> {
        return connections.filter { it.targetNodeId == nodeId }
    }
}

/**
 * 导览图节点 DTO
 */
data class DiagramNodeDto(
    val id: String,
    val bookmarkId: String?,
    val label: String,
    val description: String,
    val nodeType: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val color: String?,
    val showCode: Boolean,
    val collapsed: Boolean
) {
    companion object {
        fun fromNode(node: DiagramNode): DiagramNodeDto {
            return DiagramNodeDto(
                id = node.id,
                bookmarkId = node.bookmarkId,
                label = node.label,
                description = node.description,
                nodeType = node.nodeType.name,
                x = node.x,
                y = node.y,
                width = node.width,
                height = node.height,
                color = node.color,
                showCode = node.showCode,
                collapsed = node.collapsed
            )
        }

        fun toNode(dto: DiagramNodeDto): DiagramNode {
            return DiagramNode(
                id = dto.id,
                bookmarkId = dto.bookmarkId,
                label = dto.label,
                description = dto.description,
                nodeType = NodeType.valueOf(dto.nodeType),
                x = dto.x,
                y = dto.y,
                width = dto.width,
                height = dto.height,
                color = dto.color,
                showCode = dto.showCode,
                collapsed = dto.collapsed
            )
        }
    }
}

/**
 * 导览图连线 DTO
 */
data class DiagramConnectionDto(
    val id: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val connectionType: String,
    val label: String
) {
    companion object {
        fun fromConnection(conn: DiagramConnection): DiagramConnectionDto {
            return DiagramConnectionDto(
                id = conn.id,
                sourceNodeId = conn.sourceNodeId,
                targetNodeId = conn.targetNodeId,
                connectionType = conn.connectionType.name,
                label = conn.label
            )
        }

        fun toConnection(dto: DiagramConnectionDto): DiagramConnection {
            return DiagramConnection(
                id = dto.id,
                sourceNodeId = dto.sourceNodeId,
                targetNodeId = dto.targetNodeId,
                connectionType = ConnectionType.valueOf(dto.connectionType),
                label = dto.label
            )
        }
    }
}

/**
 * 导览图 DTO
 */
data class DiagramDto(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val layout: String,
    val nodes: List<DiagramNodeDto>,
    val connections: List<DiagramConnectionDto>,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        fun fromDiagram(diagram: Diagram): DiagramDto {
            return DiagramDto(
                id = diagram.id,
                name = diagram.name,
                description = diagram.description,
                type = diagram.type.name,
                layout = diagram.layout.name,
                nodes = diagram.nodes.map { DiagramNodeDto.fromNode(it) },
                connections = diagram.connections.map { DiagramConnectionDto.fromConnection(it) },
                createdAt = diagram.createdAt,
                updatedAt = diagram.updatedAt
            )
        }

        fun toDiagram(dto: DiagramDto): Diagram {
            return Diagram(
                id = dto.id,
                name = dto.name,
                description = dto.description,
                type = DiagramType.valueOf(dto.type),
                layout = DiagramLayout.valueOf(dto.layout),
                nodes = dto.nodes.map { DiagramNodeDto.toNode(it) }.toMutableList(),
                connections = dto.connections.map { DiagramConnectionDto.toConnection(it) }.toMutableList(),
                createdAt = dto.createdAt,
                updatedAt = dto.updatedAt
            )
        }
    }
}
