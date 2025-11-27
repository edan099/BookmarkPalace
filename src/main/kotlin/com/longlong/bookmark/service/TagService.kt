package com.longlong.bookmark.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.longlong.bookmark.model.Tag
import com.longlong.bookmark.model.TagGroup
import com.longlong.bookmark.storage.BookmarkStorage

/**
 * 标签服务 - 标签管理
 */
@Service(Service.Level.PROJECT)
class TagService(private val project: Project) {

    // 内存中的标签列表
    private val tags = mutableListOf<Tag>()

    // 内存中的标签分组列表
    private val tagGroups = mutableListOf<TagGroup>()

    // 变更监听器
    private val listeners = mutableListOf<TagChangeListener>()

    init {
        loadFromStorage()
        // 如果没有标签，初始化预设标签
        if (tags.isEmpty()) {
            initPresetTags()
        }
    }

    /**
     * 获取所有标签
     */
    fun getAllTags(): List<Tag> {
        return tags.toList()
    }

    /**
     * 获取所有标签分组
     */
    fun getAllGroups(): List<TagGroup> {
        return tagGroups.toList()
    }

    /**
     * 根据ID获取标签
     */
    fun getTag(id: String): Tag? {
        return tags.find { it.id == id }
    }

    /**
     * 根据名称获取标签
     */
    fun getTagByName(name: String): Tag? {
        return tags.find { it.name == name }
    }

    /**
     * 获取分组下的标签
     */
    fun getTagsByGroup(groupId: String?): List<Tag> {
        return tags.filter { it.groupId == groupId }
    }

    /**
     * 添加标签
     */
    fun addTag(name: String, color: String = "#1E88E5", groupId: String? = null, description: String = ""): Tag {
        val tag = Tag(
            name = name,
            color = color,
            groupId = groupId,
            description = description,
            order = tags.size
        )
        tags.add(tag)
        saveToStorage()
        notifyTagAdded(tag)
        return tag
    }

    /**
     * 更新标签
     */
    fun updateTag(tag: Tag) {
        val index = tags.indexOfFirst { it.id == tag.id }
        if (index >= 0) {
            tags[index] = tag
            saveToStorage()
            notifyTagUpdated(tag)
        }
    }

    /**
     * 删除标签
     */
    fun removeTag(tagId: String) {
        val tag = tags.find { it.id == tagId } ?: return
        tags.remove(tag)
        saveToStorage()
        notifyTagRemoved(tag)
    }

    /**
     * 添加标签分组
     */
    fun addGroup(name: String, description: String = ""): TagGroup {
        val group = TagGroup(
            name = name,
            description = description,
            order = tagGroups.size
        )
        tagGroups.add(group)
        saveToStorage()
        return group
    }

    /**
     * 更新标签分组
     */
    fun updateGroup(group: TagGroup) {
        val index = tagGroups.indexOfFirst { it.id == group.id }
        if (index >= 0) {
            tagGroups[index] = group
            saveToStorage()
        }
    }

    /**
     * 删除标签分组
     */
    fun removeGroup(groupId: String) {
        tagGroups.removeIf { it.id == groupId }
        // 将该分组下的标签移到未分组
        tags.filter { it.groupId == groupId }.forEach { it.groupId = null }
        saveToStorage()
    }

    /**
     * 搜索标签
     */
    fun searchTags(query: String): List<Tag> {
        val lowerQuery = query.lowercase()
        return tags.filter { 
            it.name.lowercase().contains(lowerQuery) ||
            it.description.lowercase().contains(lowerQuery)
        }
    }

    /**
     * 获取或创建标签
     */
    fun getOrCreateTag(name: String): Tag {
        return getTagByName(name) ?: addTag(name)
    }

    /**
     * 添加变更监听器
     */
    fun addChangeListener(listener: TagChangeListener) {
        listeners.add(listener)
    }

    /**
     * 移除变更监听器
     */
    fun removeChangeListener(listener: TagChangeListener) {
        listeners.remove(listener)
    }

    // === 私有方法 ===

    private fun loadFromStorage() {
        val storage = BookmarkStorage.getInstance(project)
        tags.clear()
        tags.addAll(storage.getTags())
        tagGroups.clear()
        tagGroups.addAll(storage.getTagGroups())
    }

    private fun saveToStorage() {
        val storage = BookmarkStorage.getInstance(project)
        storage.saveTags(tags)
        storage.saveTagGroups(tagGroups)
    }

    private fun initPresetTags() {
        // 创建预设分组
        val businessGroup = addGroup("业务流程", "业务相关标签")
        val technicalGroup = addGroup("技术标记", "技术相关标签")
        val statusGroup = addGroup("状态标记", "代码状态标签")

        // 添加预设标签
        Tag.PRESET_TAGS.forEachIndexed { index, preset ->
            val groupId = when {
                index < 4 -> businessGroup.id
                index < 7 -> technicalGroup.id
                else -> statusGroup.id
            }
            tags.add(preset.copy(groupId = groupId, order = index))
        }

        saveToStorage()
    }

    private fun notifyTagAdded(tag: Tag) {
        listeners.forEach { it.onTagAdded(tag) }
    }

    private fun notifyTagRemoved(tag: Tag) {
        listeners.forEach { it.onTagRemoved(tag) }
    }

    private fun notifyTagUpdated(tag: Tag) {
        listeners.forEach { it.onTagUpdated(tag) }
    }

    companion object {
        fun getInstance(project: Project): TagService {
            return project.getService(TagService::class.java)
        }
    }
}

/**
 * 标签变更监听器接口
 */
interface TagChangeListener {
    fun onTagAdded(tag: Tag) {}
    fun onTagRemoved(tag: Tag) {}
    fun onTagUpdated(tag: Tag) {}
}
