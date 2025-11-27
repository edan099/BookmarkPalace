package com.longlong.bookmark.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.longlong.bookmark.model.*
import com.longlong.bookmark.storage.BookmarkStorage

/**
 * 导览图服务 - 管理导览图
 */
@Service(Service.Level.PROJECT)
class DiagramService(private val project: Project) {

    // 内存中的导览图列表
    private val diagrams = mutableListOf<Diagram>()

    // 变更监听器
    private val listeners = mutableListOf<DiagramChangeListener>()

    init {
        loadFromStorage()
        // 如果没有导览图，创建默认导览图
        if (diagrams.isEmpty()) {
            createDefaultDiagrams()
        }
    }

    /**
     * 获取所有导览图
     */
    fun getAllDiagrams(): List<Diagram> {
        return diagrams.toList()
    }

    /**
     * 根据ID获取导览图
     */
    fun getDiagram(id: String): Diagram? {
        return diagrams.find { it.id == id }
    }

    /**
     * 根据类型获取导览图
     */
    fun getDiagramsByType(type: DiagramType): List<Diagram> {
        return diagrams.filter { it.type == type }
    }

    /**
     * 创建导览图
     */
    fun createDiagram(
        name: String,
        type: DiagramType = DiagramType.CUSTOM_FLOW,
        description: String = ""
    ): Diagram {
        val diagram = Diagram(
            name = name,
            type = type,
            description = description
        )
        diagrams.add(diagram)
        saveToStorage()
        notifyDiagramAdded(diagram)
        return diagram
    }

    /**
     * 更新导览图
     */
    fun updateDiagram(diagram: Diagram) {
        val index = diagrams.indexOfFirst { it.id == diagram.id }
        if (index >= 0) {
            diagram.touch()
            diagrams[index] = diagram
            saveToStorage()
            notifyDiagramUpdated(diagram)
        }
    }

    /**
     * 删除导览图
     */
    fun removeDiagram(diagramId: String) {
        val diagram = diagrams.find { it.id == diagramId } ?: return
        diagrams.remove(diagram)
        saveToStorage()
        notifyDiagramRemoved(diagram)
    }

    /**
     * 添加节点到导览图
     */
    fun addNodeToDiagram(diagramId: String, node: DiagramNode) {
        val diagram = getDiagram(diagramId) ?: return
        diagram.addNode(node)
        saveToStorage()
        notifyDiagramUpdated(diagram)
    }

    /**
     * 从书签创建节点并添加到导览图
     */
    fun addBookmarkToDiagram(diagramId: String, bookmark: Bookmark): DiagramNode? {
        val diagram = getDiagram(diagramId) ?: return null

        // 检查是否已存在该书签的节点
        if (diagram.nodes.any { it.bookmarkId == bookmark.id }) {
            return diagram.nodes.find { it.bookmarkId == bookmark.id }
        }

        val node = diagram.createNodeFromBookmark(bookmark)
        // 自动布局位置
        node.x = (diagram.nodes.size % 4) * 200.0 + 50
        node.y = (diagram.nodes.size / 4) * 100.0 + 50

        diagram.addNode(node)
        saveToStorage()
        notifyDiagramUpdated(diagram)
        return node
    }

    /**
     * 从导览图移除节点
     */
    fun removeNodeFromDiagram(diagramId: String, nodeId: String) {
        val diagram = getDiagram(diagramId) ?: return
        diagram.removeNode(nodeId)
        saveToStorage()
        notifyDiagramUpdated(diagram)
    }

    /**
     * 添加连线到导览图
     */
    fun addConnectionToDiagram(
        diagramId: String,
        sourceNodeId: String,
        targetNodeId: String,
        type: ConnectionType = ConnectionType.NORMAL,
        label: String = ""
    ): DiagramConnection? {
        val diagram = getDiagram(diagramId) ?: return null

        // 检查是否已存在相同连线
        if (diagram.connections.any { it.sourceNodeId == sourceNodeId && it.targetNodeId == targetNodeId }) {
            return null
        }

        val connection = DiagramConnection(
            sourceNodeId = sourceNodeId,
            targetNodeId = targetNodeId,
            connectionType = type,
            label = label
        )

        diagram.addConnection(connection)
        saveToStorage()
        notifyDiagramUpdated(diagram)
        return connection
    }

    /**
     * 从导览图移除连线
     */
    fun removeConnectionFromDiagram(diagramId: String, connectionId: String) {
        val diagram = getDiagram(diagramId) ?: return
        diagram.removeConnection(connectionId)
        saveToStorage()
        notifyDiagramUpdated(diagram)
    }

    /**
     * 更新节点位置
     */
    fun updateNodePosition(diagramId: String, nodeId: String, x: Double, y: Double) {
        val diagram = getDiagram(diagramId) ?: return
        val node = diagram.getNode(nodeId) ?: return
        node.x = x
        node.y = y
        diagram.touch()
        saveToStorage()
    }

    /**
     * 根据标签生成导览图
     */
    fun generateTagFlowDiagram(tagName: String): Diagram {
        val bookmarkService = BookmarkService.getInstance(project)
        val bookmarks = bookmarkService.getBookmarksByTag(tagName)

        val diagram = Diagram(
            name = "标签视图: $tagName",
            type = DiagramType.TAG_FLOW,
            description = "自动生成的标签视图"
        )

        bookmarks.forEachIndexed { index, bookmark ->
            val node = diagram.createNodeFromBookmark(bookmark)
            node.x = (index % 4) * 200.0 + 50
            node.y = (index / 4) * 100.0 + 50
            diagram.addNode(node)
        }

        diagrams.add(diagram)
        saveToStorage()
        notifyDiagramAdded(diagram)
        return diagram
    }

    /**
     * 自动布局
     */
    fun autoLayout(diagramId: String, layout: DiagramLayout = DiagramLayout.TREE) {
        val diagram = getDiagram(diagramId) ?: return

        when (layout) {
            DiagramLayout.TREE -> applyTreeLayout(diagram)
            DiagramLayout.DAG -> applyDagLayout(diagram)
            DiagramLayout.HORIZONTAL -> applyHorizontalLayout(diagram)
            DiagramLayout.FORCE -> applyForceLayout(diagram)
        }

        diagram.layout = layout
        diagram.touch()
        saveToStorage()
        notifyDiagramUpdated(diagram)
    }

    /**
     * 添加变更监听器
     */
    fun addChangeListener(listener: DiagramChangeListener) {
        listeners.add(listener)
    }

    /**
     * 移除变更监听器
     */
    fun removeChangeListener(listener: DiagramChangeListener) {
        listeners.remove(listener)
    }

    // === 布局算法 ===

    private fun applyTreeLayout(diagram: Diagram) {
        val nodes = diagram.nodes
        if (nodes.isEmpty()) return

        // 找到没有入边的节点作为根节点
        val nodeIds = nodes.map { it.id }.toSet()
        val hasIncoming = diagram.connections.map { it.targetNodeId }.toSet()
        val roots = nodes.filter { it.id !in hasIncoming }

        if (roots.isEmpty() && nodes.isNotEmpty()) {
            // 如果没有根节点，选择第一个节点
            layoutSubtree(diagram, nodes.first(), 0, 0, mutableSetOf())
        } else {
            var yOffset = 0.0
            roots.forEach { root ->
                layoutSubtree(diagram, root, 0, yOffset.toInt(), mutableSetOf())
                yOffset += 150
            }
        }
    }

    private fun layoutSubtree(diagram: Diagram, node: DiagramNode, level: Int, yIndex: Int, visited: MutableSet<String>) {
        if (node.id in visited) return
        visited.add(node.id)

        node.x = level * 220.0 + 50
        node.y = yIndex * 80.0 + 50

        val children = diagram.getOutgoingConnections(node.id)
            .mapNotNull { diagram.getNode(it.targetNodeId) }
            .filter { it.id !in visited }

        children.forEachIndexed { index, child ->
            layoutSubtree(diagram, child, level + 1, yIndex + index, visited)
        }
    }

    private fun applyDagLayout(diagram: Diagram) {
        // 简化的DAG布局，类似树形但处理多入边
        applyTreeLayout(diagram)
    }

    private fun applyHorizontalLayout(diagram: Diagram) {
        diagram.nodes.forEachIndexed { index, node ->
            node.x = index * 200.0 + 50
            node.y = 100.0
        }
    }

    private fun applyForceLayout(diagram: Diagram) {
        // 简化的力导向布局
        val nodes = diagram.nodes
        if (nodes.isEmpty()) return

        // 初始化随机位置
        nodes.forEachIndexed { index, node ->
            node.x = (index % 5) * 180.0 + 50 + (Math.random() * 50)
            node.y = (index / 5) * 120.0 + 50 + (Math.random() * 50)
        }
    }

    // === 私有方法 ===

    private fun loadFromStorage() {
        val storage = BookmarkStorage.getInstance(project)
        diagrams.clear()
        diagrams.addAll(storage.getDiagrams())
    }

    private fun saveToStorage() {
        val storage = BookmarkStorage.getInstance(project)
        storage.saveDiagrams(diagrams)
    }

    private fun createDefaultDiagrams() {
        createDiagram("主流程", DiagramType.MAIN_FLOW, "业务主链路导览图")
        createDiagram("自定义导览", DiagramType.CUSTOM_FLOW, "自定义代码导览图")
    }

    private fun notifyDiagramAdded(diagram: Diagram) {
        listeners.forEach { it.onDiagramAdded(diagram) }
    }

    private fun notifyDiagramRemoved(diagram: Diagram) {
        listeners.forEach { it.onDiagramRemoved(diagram) }
    }

    private fun notifyDiagramUpdated(diagram: Diagram) {
        listeners.forEach { it.onDiagramUpdated(diagram) }
    }

    companion object {
        fun getInstance(project: Project): DiagramService {
            return project.getService(DiagramService::class.java)
        }
    }
}

/**
 * 导览图变更监听器接口
 */
interface DiagramChangeListener {
    fun onDiagramAdded(diagram: Diagram) {}
    fun onDiagramRemoved(diagram: Diagram) {}
    fun onDiagramUpdated(diagram: Diagram) {}
}
