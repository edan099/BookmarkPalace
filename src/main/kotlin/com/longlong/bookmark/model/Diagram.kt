package com.longlong.bookmark.model

import java.util.UUID

/**
 * 导览图类型
 */
enum class DiagramType(val displayName: String, val displayNameEn: String) {
    MAIN_FLOW("主流程", "Main Flow"),
    CALL_FLOW("调用流程", "Call Flow"),
    TAG_FLOW("标签视图", "Tag View"),
    CUSTOM_FLOW("自定义", "Custom")
}

/**
 * 节点形状
 */
enum class NodeShape(val displayName: String, val displayNameEn: String) {
    RECTANGLE("矩形", "Rectangle"),
    ROUNDED_RECT("圆角矩形", "Rounded Rect"),
    CIRCLE("圆形", "Circle"),
    ELLIPSE("椭圆", "Ellipse"),
    DIAMOND("菱形", "Diamond")
}

/**
 * 节点连线类型
 */
enum class ConnectionType(val displayName: String, val displayNameEn: String) {
    NORMAL("普通", "Normal"),
    DASHED("虚线", "Dashed"),
    ARROW("箭头", "Arrow")
}

/**
 * 导览图节点
 */
data class DiagramNode(
    val id: String = UUID.randomUUID().toString(),
    var bookmarkId: String? = null,      // 关联的书签ID（可选）
    var label: String = "",              // 节点显示标签
    var description: String = "",        // 节点描述
    var shape: NodeShape = NodeShape.RECTANGLE,
    var x: Double = 0.0,                 // X 坐标
    var y: Double = 0.0,                 // Y 坐标
    var width: Double = 120.0,           // 宽度
    var height: Double = 50.0,           // 高度
    var color: String = "#4A90D9",       // 节点填充颜色
    var textColor: String = "#FFFFFF",   // 文字颜色
    var borderColor: String = "#333333", // 边框颜色
    var fontSize: Int = 12,              // 文字大小
    var borderWidth: Float = 1.5f        // 边框粗细
) {
    /**
     * 是否为书签节点
     */
    fun isBookmarkNode(): Boolean = bookmarkId != null
    
    /**
     * 获取4个边的中点（用于连线）
     */
    fun getEdgeMidpoints(): List<Pair<Double, Double>> = listOf(
        Pair(x + width / 2, y),              // 上
        Pair(x + width, y + height / 2),     // 右
        Pair(x + width / 2, y + height),     // 下
        Pair(x, y + height / 2)              // 左
    )
    
    /**
     * 获取4个顶点（用于调整大小）
     */
    fun getCorners(): List<Pair<Double, Double>> = listOf(
        Pair(x, y),                          // 左上
        Pair(x + width, y),                  // 右上
        Pair(x + width, y + height),         // 右下
        Pair(x, y + height)                  // 左下
    )
    
    /**
     * 获取最近的边中点
     */
    fun getNearestEdgeMidpoint(px: Double, py: Double): Pair<Double, Double> {
        return getEdgeMidpoints().minByOrNull { (ex, ey) ->
            Math.sqrt((px - ex) * (px - ex) + (py - ey) * (py - ey))
        } ?: Pair(x + width / 2, y + height / 2)
    }
}

/**
 * 导览图连线
 */
data class DiagramConnection(
    val id: String = UUID.randomUUID().toString(),
    var sourceNodeId: String = "",       // 源节点ID
    var targetNodeId: String = "",       // 目标节点ID
    var connectionType: ConnectionType = ConnectionType.ARROW,
    var label: String = "",              // 连线标签
    var lineColor: String = "#666666",   // 线条颜色
    var lineWidth: Float = 2f,           // 线条粗细
    var fontSize: Int = 11,              // 标签文字大小
    var curveOffset: Double = 0.0        // 曲线偏移（用于双向连线避让）
)

/**
 * 导览图
 */
data class Diagram(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var description: String = "",
    var type: DiagramType = DiagramType.CUSTOM_FLOW,
    var nodes: MutableList<DiagramNode> = mutableListOf(),
    var connections: MutableList<DiagramConnection> = mutableListOf(),
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) {
    fun addNode(node: DiagramNode) {
        nodes.add(node)
        touch()
    }

    fun removeNode(nodeId: String) {
        nodes.removeIf { it.id == nodeId }
        connections.removeIf { it.sourceNodeId == nodeId || it.targetNodeId == nodeId }
        touch()
    }

    fun addConnection(connection: DiagramConnection) {
        // 避免重复连线
        if (connections.none { it.sourceNodeId == connection.sourceNodeId && it.targetNodeId == connection.targetNodeId }) {
            connections.add(connection)
            touch()
        }
    }

    fun removeConnection(connectionId: String) {
        connections.removeIf { it.id == connectionId }
        touch()
    }

    fun createNodeFromBookmark(bookmark: Bookmark): DiagramNode {
        return DiagramNode(
            bookmarkId = bookmark.id,
            label = bookmark.getDisplayName(),
            description = bookmark.comment,
            color = bookmark.color.hexColor
        )
    }

    fun touch() {
        updatedAt = System.currentTimeMillis()
    }

    fun getNode(nodeId: String): DiagramNode? = nodes.find { it.id == nodeId }
    
    fun getOutgoingConnections(nodeId: String): List<DiagramConnection> = 
        connections.filter { it.sourceNodeId == nodeId }
    
    fun getIncomingConnections(nodeId: String): List<DiagramConnection> = 
        connections.filter { it.targetNodeId == nodeId }
}

// ========== DTO 类 ==========

data class DiagramNodeDto(
    val id: String,
    val bookmarkId: String?,
    val label: String,
    val description: String,
    val shape: String,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val color: String,
    val textColor: String = "#FFFFFF",
    val borderColor: String = "#333333",
    val fontSize: Int = 12,
    val borderWidth: Float = 1.5f
) {
    companion object {
        fun fromNode(node: DiagramNode) = DiagramNodeDto(
            id = node.id, bookmarkId = node.bookmarkId, label = node.label,
            description = node.description, shape = node.shape.name,
            x = node.x, y = node.y, width = node.width, height = node.height, 
            color = node.color, textColor = node.textColor, borderColor = node.borderColor,
            fontSize = node.fontSize, borderWidth = node.borderWidth
        )

        fun toNode(dto: DiagramNodeDto) = DiagramNode(
            id = dto.id, bookmarkId = dto.bookmarkId, label = dto.label,
            description = dto.description, 
            shape = try { NodeShape.valueOf(dto.shape) } catch (e: Exception) { NodeShape.RECTANGLE },
            x = dto.x, y = dto.y, width = dto.width, height = dto.height, 
            color = dto.color, textColor = dto.textColor, borderColor = dto.borderColor,
            fontSize = dto.fontSize, borderWidth = dto.borderWidth
        )
    }
}

data class DiagramConnectionDto(
    val id: String,
    val sourceNodeId: String,
    val targetNodeId: String,
    val connectionType: String,
    val label: String,
    val lineColor: String = "#666666",
    val lineWidth: Float = 2f,
    val fontSize: Int = 11,
    val curveOffset: Double = 0.0
) {
    companion object {
        fun fromConnection(conn: DiagramConnection) = DiagramConnectionDto(
            id = conn.id, sourceNodeId = conn.sourceNodeId, targetNodeId = conn.targetNodeId,
            connectionType = conn.connectionType.name, label = conn.label,
            lineColor = conn.lineColor, lineWidth = conn.lineWidth, 
            fontSize = conn.fontSize, curveOffset = conn.curveOffset
        )

        fun toConnection(dto: DiagramConnectionDto) = DiagramConnection(
            id = dto.id, sourceNodeId = dto.sourceNodeId, targetNodeId = dto.targetNodeId,
            connectionType = try { ConnectionType.valueOf(dto.connectionType) } catch (e: Exception) { ConnectionType.ARROW },
            label = dto.label, lineColor = dto.lineColor, lineWidth = dto.lineWidth,
            fontSize = dto.fontSize, curveOffset = dto.curveOffset
        )
    }
}

data class DiagramDto(
    val id: String,
    val name: String,
    val description: String,
    val type: String,
    val nodes: List<DiagramNodeDto>,
    val connections: List<DiagramConnectionDto>,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        fun fromDiagram(diagram: Diagram) = DiagramDto(
            id = diagram.id, name = diagram.name, description = diagram.description,
            type = diagram.type.name,
            nodes = diagram.nodes.map { DiagramNodeDto.fromNode(it) },
            connections = diagram.connections.map { DiagramConnectionDto.fromConnection(it) },
            createdAt = diagram.createdAt, updatedAt = diagram.updatedAt
        )

        fun toDiagram(dto: DiagramDto) = Diagram(
            id = dto.id, name = dto.name, description = dto.description,
            type = try { DiagramType.valueOf(dto.type) } catch (e: Exception) { DiagramType.CUSTOM_FLOW },
            nodes = dto.nodes.map { DiagramNodeDto.toNode(it) }.toMutableList(),
            connections = dto.connections.map { DiagramConnectionDto.toConnection(it) }.toMutableList(),
            createdAt = dto.createdAt, updatedAt = dto.updatedAt
        )
    }
}
