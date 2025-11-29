package com.longlong.bookmark.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*

/**
 * 文件变更监听器 - 监听文件重命名、移动、删除等事件
 */
@Service(Service.Level.PROJECT)
class FileChangeListener(private val project: Project) : BulkFileListener {

    init {
        // 注册文件变更监听
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this)
    }

    override fun after(events: MutableList<out VFileEvent>) {
        val bookmarkService = BookmarkService.getInstance(project)
        var needRefresh = false

        for (event in events) {
            when (event) {
                is VFileDeleteEvent -> {
                    // 文件被删除，标记相关书签为失效
                    handleFileDeleted(event.file, bookmarkService)
                    needRefresh = true
                }
                is VFileMoveEvent -> {
                    // 文件被移动，更新书签路径
                    handleFileMoved(event, bookmarkService)
                    needRefresh = true
                }
                is VFilePropertyChangeEvent -> {
                    // 文件重命名
                    if (event.propertyName == VirtualFile.PROP_NAME) {
                        handleFileRenamed(event, bookmarkService)
                        needRefresh = true
                    }
                }
                is VFileContentChangeEvent -> {
                    // 文件内容变更，刷新RangeMarker状态
                    // RangeMarker会自动更新，但我们需要同步书签数据
                    needRefresh = true
                }
            }
        }

        if (needRefresh) {
            bookmarkService.refreshBookmarks()
        }
    }

    private fun handleFileDeleted(file: VirtualFile, bookmarkService: BookmarkService) {
        val relativePath = getRelativePath(file)
        val bookmarks = bookmarkService.getAllBookmarks().filter { it.filePath == relativePath }
        bookmarks.forEach { bookmark ->
            bookmark.markAsMissing()
            bookmarkService.updateBookmark(bookmark)
        }
    }

    private fun handleFileMoved(event: VFileMoveEvent, bookmarkService: BookmarkService) {
        val oldPath = getRelativePath(event.oldParent) + "/" + event.file.name
        val newPath = getRelativePath(event.file)

        val bookmarks = bookmarkService.getAllBookmarks().filter { it.filePath == oldPath }
        bookmarks.forEach { bookmark ->
            bookmark.filePath = newPath
            bookmarkService.updateBookmark(bookmark)
        }
    }

    private fun handleFileRenamed(event: VFilePropertyChangeEvent, bookmarkService: BookmarkService) {
        val file = event.file
        val oldName = event.oldValue as? String ?: return
        val parentPath = file.parent?.let { getRelativePath(it) } ?: ""
        val oldPath = if (parentPath.isNotEmpty()) "$parentPath/$oldName" else oldName
        val newPath = getRelativePath(file)

        val bookmarks = bookmarkService.getAllBookmarks().filter { it.filePath == oldPath }
        bookmarks.forEach { bookmark ->
            bookmark.filePath = newPath
            bookmarkService.updateBookmark(bookmark)
        }
    }

    private fun getRelativePath(file: VirtualFile): String {
        val basePath = project.basePath ?: return file.path
        return if (file.path.startsWith(basePath)) {
            file.path.removePrefix(basePath).removePrefix("/")
        } else {
            file.path
        }
    }

    companion object {
        fun getInstance(project: Project): FileChangeListener {
            return project.getService(FileChangeListener::class.java)
        }
    }
}
