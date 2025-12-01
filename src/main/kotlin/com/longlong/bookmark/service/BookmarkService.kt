package com.longlong.bookmark.service

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.longlong.bookmark.editor.BookmarkLineMarkerProvider
import com.longlong.bookmark.model.*
import com.longlong.bookmark.storage.BookmarkStorage
import java.util.concurrent.ConcurrentHashMap

/**
 * 书签服务 - 核心书签管理
 */
@Service(Service.Level.PROJECT)
class BookmarkService(private val project: Project) {

    // 内存中的书签列表
    private val bookmarks = mutableListOf<Bookmark>()

    // 书签ID到RangeMarker的映射（用于动态跟踪）
    private val rangeMarkers = ConcurrentHashMap<String, RangeMarker>()

    // 变更监听器
    private val listeners = mutableListOf<BookmarkChangeListener>()

    init {
        // 从存储加载书签
        loadFromStorage()
    }

    /**
     * 添加书签
     */
    fun addBookmark(
        editor: Editor,
        alias: String = "",
        color: BookmarkColor = BookmarkColor.BLUE,
        tags: List<String> = emptyList(),
        comment: String = ""
    ): Bookmark? {
        val document = editor.document
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return null
        val filePath = getRelativePath(virtualFile)

        val selectionModel = editor.selectionModel
        val startOffset: Int
        val endOffset: Int

        if (selectionModel.hasSelection()) {
            startOffset = selectionModel.selectionStart
            endOffset = selectionModel.selectionEnd
        } else {
            // 选择当前行
            val caretOffset = editor.caretModel.offset
            val lineNumber = document.getLineNumber(caretOffset)
            startOffset = document.getLineStartOffset(lineNumber)
            endOffset = document.getLineEndOffset(lineNumber)
        }

        val startLine = document.getLineNumber(startOffset)
        val endLine = document.getLineNumber(endOffset)
        val codeSnippet = document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset))

        val bookmark = Bookmark(
            filePath = filePath,
            startLine = startLine,
            endLine = endLine,
            startOffset = startOffset,
            endOffset = endOffset,
            alias = alias.ifBlank { generateAlias(codeSnippet) },
            color = color,
            tags = tags.toMutableList(),
            comment = comment,
            codeSnippet = codeSnippet,
            history = BookmarkHistory(
                originalSnippet = codeSnippet,
                originalStartLine = startLine,
                originalEndLine = endLine
            )
        )

        // 创建RangeMarker用于动态跟踪
        createRangeMarker(bookmark, document)

        bookmarks.add(bookmark)
        saveToStorage()
        notifyBookmarkAdded(bookmark)

        return bookmark
    }

    /**
     * 快速添加书签（使用默认设置）
     */
    fun quickAddBookmark(editor: Editor): Bookmark? {
        return addBookmark(editor)
    }

    /**
     * 删除书签
     */
    fun removeBookmark(bookmarkId: String) {
        val bookmark = bookmarks.find { it.id == bookmarkId } ?: return
        bookmarks.remove(bookmark)
        rangeMarkers.remove(bookmarkId)?.dispose()
        saveToStorage()
        notifyBookmarkRemoved(bookmark)
    }

    /**
     * 更新书签
     */
    fun updateBookmark(bookmark: Bookmark) {
        val index = bookmarks.indexOfFirst { it.id == bookmark.id }
        if (index >= 0) {
            bookmark.touch()
            bookmarks[index] = bookmark
            
            // 重新创建 RangeMarker（如果位置变更了）
            recreateRangeMarker(bookmark)
            
            saveToStorage()
            notifyBookmarkUpdated(bookmark)
        }
    }
    
    /**
     * 重新创建书签的 RangeMarker
     */
    private fun recreateRangeMarker(bookmark: Bookmark) {
        // 移除旧的 marker
        rangeMarkers.remove(bookmark.id)?.dispose()
        
        // 创建新的 marker
        val virtualFile = findVirtualFile(bookmark.filePath) ?: return
        val document = ReadAction.compute<Document?, Throwable> {
            FileDocumentManager.getInstance().getDocument(virtualFile)
        } ?: return
        
        if (bookmark.startLine >= 0 && bookmark.startLine < document.lineCount) {
            val endLine = minOf(bookmark.endLine, document.lineCount - 1)
            val startOffset = document.getLineStartOffset(bookmark.startLine)
            val endOffset = document.getLineEndOffset(endLine)
            
            if (endOffset >= startOffset && endOffset <= document.textLength) {
                createRangeMarker(bookmark, document)
            }
        }
    }

    /**
     * 获取所有书签
     */
    fun getAllBookmarks(): List<Bookmark> {
        return bookmarks.toList()
    }

    /**
     * 根据ID获取书签
     */
    fun getBookmark(id: String): Bookmark? {
        return bookmarks.find { it.id == id }
    }

    /**
     * 根据文件获取书签
     */
    fun getBookmarksByFile(filePath: String): List<Bookmark> {
        return bookmarks.filter { it.filePath == filePath }
    }

    /**
     * 根据标签获取书签
     */
    fun getBookmarksByTag(tag: String): List<Bookmark> {
        return bookmarks.filter { it.tags.contains(tag) }
    }

    /**
     * 根据颜色获取书签
     */
    fun getBookmarksByColor(color: BookmarkColor): List<Bookmark> {
        return bookmarks.filter { it.color == color }
    }

    /**
     * 根据状态获取书签
     */
    fun getBookmarksByStatus(status: BookmarkStatus): List<Bookmark> {
        return bookmarks.filter { it.status == status }
    }

    /**
     * 搜索书签
     */
    fun searchBookmarks(query: String): List<Bookmark> {
        val lowerQuery = query.lowercase()
        return bookmarks.filter { bookmark ->
            bookmark.alias.lowercase().contains(lowerQuery) ||
            bookmark.comment.lowercase().contains(lowerQuery) ||
            bookmark.codeSnippet.lowercase().contains(lowerQuery) ||
            bookmark.tags.any { it.lowercase().contains(lowerQuery) } ||
            bookmark.getFileName().lowercase().contains(lowerQuery)
        }
    }

    /**
     * 跳转到书签
     */
    fun navigateToBookmark(bookmark: Bookmark): Boolean {
        val virtualFile = findVirtualFile(bookmark.filePath) ?: run {
            bookmark.markAsMissing()
            updateBookmark(bookmark)
            return false
        }

        val document = ReadAction.compute<Document?, Throwable> {
            FileDocumentManager.getInstance().getDocument(virtualFile)
        } ?: run {
            bookmark.markAsMissing()
            updateBookmark(bookmark)
            return false
        }

        // 检查RangeMarker是否有效
        val rangeMarker = rangeMarkers[bookmark.id]
        val targetOffset = if (rangeMarker != null && rangeMarker.isValid) {
            // 使用RangeMarker的实时位置
            val newStartLine = document.getLineNumber(rangeMarker.startOffset)
            if (newStartLine != bookmark.startLine) {
                // 更新书签位置
                bookmark.startLine = newStartLine
                bookmark.endLine = document.getLineNumber(rangeMarker.endOffset)
                bookmark.startOffset = rangeMarker.startOffset
                bookmark.endOffset = rangeMarker.endOffset
                bookmark.codeSnippet = document.getText(
                    com.intellij.openapi.util.TextRange(rangeMarker.startOffset, rangeMarker.endOffset)
                )
                bookmark.markAsValid()
                updateBookmark(bookmark)
            }
            rangeMarker.startOffset
        } else {
            // RangeMarker无效，尝试使用存储的行号
            if (bookmark.startLine < document.lineCount) {
                document.getLineStartOffset(bookmark.startLine)
            } else {
                bookmark.markAsMissing()
                updateBookmark(bookmark)
                return false
            }
        }

        ApplicationManager.getApplication().invokeLater {
            val descriptor = OpenFileDescriptor(project, virtualFile, targetOffset)
            FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
        }

        return true
    }

    /**
     * 刷新书签状态（同步RangeMarker）
     * 支持分支切换后的书签恢复
     */
    fun refreshBookmarks() {
        bookmarks.forEach { bookmark ->
            val rangeMarker = rangeMarkers[bookmark.id]
            
            // 首先检查 rangeMarker 是否有效
            if (rangeMarker != null && rangeMarker.isValid) {
                val document = rangeMarker.document
                val newStartLine = document.getLineNumber(rangeMarker.startOffset)
                val newEndLine = document.getLineNumber(rangeMarker.endOffset)

                // 同步 rangeMarker 的位置到书签
                if (newStartLine != bookmark.startLine || newEndLine != bookmark.endLine) {
                    bookmark.startLine = newStartLine
                    bookmark.endLine = newEndLine
                    bookmark.startOffset = rangeMarker.startOffset
                    bookmark.endOffset = rangeMarker.endOffset
                    bookmark.codeSnippet = document.getText(
                        com.intellij.openapi.util.TextRange(rangeMarker.startOffset, rangeMarker.endOffset)
                    )
                }
                // 确保状态为有效
                bookmark.markAsValid()
            } else {
                // RangeMarker 失效或不存在，先清理旧的
                if (rangeMarker != null) {
                    rangeMarkers.remove(bookmark.id)?.dispose()
                }
                // 尝试恢复书签
                tryRecoverBookmark(bookmark)
            }
        }
        saveToStorage()
        notifyBookmarksRefreshed()
    }

    /**
     * 尝试恢复书签位置（用于分支切换等场景）
     * 1. 检查文件是否存在
     * 2. 尝试在原位置恢复（无需代码匹配，行号在范围内即可）
     * 3. 如果原位置失效，尝试通过代码片段搜索
     */
    private fun tryRecoverBookmark(bookmark: Bookmark) {
        val virtualFile = findVirtualFile(bookmark.filePath)
        if (virtualFile == null || !virtualFile.exists()) {
            // 文件不存在，标记为失效
            if (bookmark.status != BookmarkStatus.MISSING) {
                bookmark.markAsMissing()
            }
            return
        }

        val document = ReadAction.compute<Document?, Throwable> {
            FileDocumentManager.getInstance().getDocument(virtualFile)
        }
        if (document == null) {
            if (bookmark.status != BookmarkStatus.MISSING) {
                bookmark.markAsMissing()
            }
            return
        }

        // 尝试1：直接在原位置恢复（只要行号在范围内就恢复）
        if (bookmark.startLine >= 0 && bookmark.startLine < document.lineCount) {
            val endLine = minOf(bookmark.endLine, document.lineCount - 1)
            val startOffset = document.getLineStartOffset(bookmark.startLine)
            val endOffset = document.getLineEndOffset(endLine)
            
            if (endOffset >= startOffset && endOffset <= document.textLength) {
                val currentCode = document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset))
                
                // 恢复书签
                bookmark.startOffset = startOffset
                bookmark.endOffset = endOffset
                bookmark.endLine = endLine
                bookmark.codeSnippet = currentCode
                bookmark.markAsValid()
                createRangeMarker(bookmark, document)
                return
            }
        }

        // 尝试2：在文件中搜索原始代码片段
        val originalCode = bookmark.history.originalSnippet.ifEmpty { bookmark.codeSnippet }
        if (originalCode.isNotBlank()) {
            val searchCode = originalCode.trim()
            val documentText = document.text
            val foundIndex = documentText.indexOf(searchCode)
            
            if (foundIndex >= 0) {
                // 找到匹配的代码，更新书签位置
                val newStartOffset = foundIndex
                val newEndOffset = foundIndex + searchCode.length
                val newStartLine = document.getLineNumber(newStartOffset)
                val newEndLine = document.getLineNumber(newEndOffset)
                
                bookmark.startLine = newStartLine
                bookmark.endLine = newEndLine
                bookmark.startOffset = newStartOffset
                bookmark.endOffset = newEndOffset
                bookmark.codeSnippet = searchCode
                bookmark.markAsValid()
                createRangeMarker(bookmark, document)
                return
            }
        }
        
        // 尝试3：如果行号超出范围，尝试定位到文件末尾
        if (document.lineCount > 0) {
            val lastLine = document.lineCount - 1
            val startOffset = document.getLineStartOffset(lastLine)
            val endOffset = document.getLineEndOffset(lastLine)
            val currentCode = document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset))
            
            bookmark.startLine = lastLine
            bookmark.endLine = lastLine
            bookmark.startOffset = startOffset
            bookmark.endOffset = endOffset
            bookmark.codeSnippet = currentCode
            bookmark.markAsOutdated() // 标记为过期而非失效
            createRangeMarker(bookmark, document)
            return
        }

        // 都失败了，标记为失效
        if (bookmark.status != BookmarkStatus.MISSING) {
            bookmark.markAsMissing()
        }
    }

    /**
     * 重新绑定书签到新位置
     */
    fun rebindBookmark(bookmark: Bookmark, editor: Editor): Boolean {
        val document = editor.document
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return false

        val selectionModel = editor.selectionModel
        val startOffset: Int
        val endOffset: Int

        if (selectionModel.hasSelection()) {
            startOffset = selectionModel.selectionStart
            endOffset = selectionModel.selectionEnd
        } else {
            val caretOffset = editor.caretModel.offset
            val lineNumber = document.getLineNumber(caretOffset)
            startOffset = document.getLineStartOffset(lineNumber)
            endOffset = document.getLineEndOffset(lineNumber)
        }

        bookmark.filePath = getRelativePath(virtualFile)
        bookmark.startLine = document.getLineNumber(startOffset)
        bookmark.endLine = document.getLineNumber(endOffset)
        bookmark.startOffset = startOffset
        bookmark.endOffset = endOffset
        bookmark.codeSnippet = document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset))
        bookmark.markAsValid()

        // 重新创建RangeMarker
        rangeMarkers.remove(bookmark.id)?.dispose()
        createRangeMarker(bookmark, document)

        updateBookmark(bookmark)
        return true
    }

    /**
     * 检查当前位置是否有书签
     */
    fun hasBookmarkAt(editor: Editor, line: Int): Boolean {
        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return false
        val filePath = getRelativePath(virtualFile)
        return bookmarks.any { it.filePath == filePath && it.startLine <= line && line <= it.endLine }
    }

    /**
     * 获取当前位置的书签
     */
    fun getBookmarkAt(editor: Editor, line: Int): Bookmark? {
        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        val filePath = getRelativePath(virtualFile)
        return bookmarks.find { it.filePath == filePath && it.startLine <= line && line <= it.endLine }
    }

    /**
     * 批量修改标签
     */
    fun batchUpdateTags(bookmarkIds: List<String>, tagsToAdd: List<String>, tagsToRemove: List<String>) {
        bookmarkIds.forEach { id ->
            val bookmark = bookmarks.find { it.id == id } ?: return@forEach
            bookmark.tags.removeAll(tagsToRemove.toSet())
            bookmark.tags.addAll(tagsToAdd.filter { it !in bookmark.tags })
            bookmark.touch()
        }
        saveToStorage()
        notifyBookmarksRefreshed()
    }

    /**
     * 批量修改颜色
     */
    fun batchUpdateColor(bookmarkIds: List<String>, color: BookmarkColor) {
        bookmarkIds.forEach { id ->
            val bookmark = bookmarks.find { it.id == id } ?: return@forEach
            bookmark.color = color
            bookmark.touch()
        }
        saveToStorage()
        notifyBookmarksRefreshed()
    }

    /**
     * 添加变更监听器
     */
    fun addChangeListener(listener: BookmarkChangeListener) {
        listeners.add(listener)
    }

    /**
     * 移除变更监听器
     */
    fun removeChangeListener(listener: BookmarkChangeListener) {
        listeners.remove(listener)
    }

    /**
     * 从存储重新加载书签（用于导入后刷新）
     */
    fun reloadFromStorage() {
        loadFromStorage()
        notifyBookmarksRefreshed()
    }

    // === 私有方法 ===

    private fun loadFromStorage() {
        val storage = BookmarkStorage.getInstance(project)
        bookmarks.clear()
        bookmarks.addAll(storage.getBookmarks())

        // 为已打开的文档创建RangeMarker
        bookmarks.forEach { bookmark ->
            val virtualFile = findVirtualFile(bookmark.filePath)
            if (virtualFile != null) {
                val document = ReadAction.compute<Document?, Throwable> {
                    FileDocumentManager.getInstance().getDocument(virtualFile)
                }
                if (document != null) {
                    createRangeMarker(bookmark, document)
                }
            }
        }
    }

    private fun saveToStorage() {
        val storage = BookmarkStorage.getInstance(project)
        storage.saveBookmarks(bookmarks)
    }

    private fun createRangeMarker(bookmark: Bookmark, document: Document) {
        if (bookmark.startOffset >= 0 && bookmark.endOffset <= document.textLength) {
            val rangeMarker = document.createRangeMarker(bookmark.startOffset, bookmark.endOffset)
            rangeMarker.isGreedyToLeft = true
            rangeMarker.isGreedyToRight = true
            rangeMarkers[bookmark.id] = rangeMarker
        }
    }

    private fun getRelativePath(virtualFile: VirtualFile): String {
        val basePath = project.basePath ?: return virtualFile.path
        return if (virtualFile.path.startsWith(basePath)) {
            virtualFile.path.substring(basePath.length + 1)
        } else {
            virtualFile.path
        }
    }

    private fun findVirtualFile(filePath: String): VirtualFile? {
        val basePath = project.basePath ?: return null
        val absolutePath = if (filePath.startsWith("/")) filePath else "$basePath/$filePath"
        return LocalFileSystem.getInstance().findFileByPath(absolutePath)
    }

    private fun generateAlias(codeSnippet: String): String {
        val firstLine = codeSnippet.lines().firstOrNull()?.trim() ?: ""
        return if (firstLine.length > 30) {
            firstLine.take(30) + "..."
        } else {
            firstLine.ifBlank { "书签" }
        }
    }

    private fun notifyBookmarkAdded(bookmark: Bookmark) {
        listeners.forEach { it.onBookmarkAdded(bookmark) }
        refreshGutterIcons(bookmark.filePath)
    }

    private fun notifyBookmarkRemoved(bookmark: Bookmark) {
        listeners.forEach { it.onBookmarkRemoved(bookmark) }
        refreshGutterIcons(bookmark.filePath)
    }

    private fun notifyBookmarkUpdated(bookmark: Bookmark) {
        listeners.forEach { it.onBookmarkUpdated(bookmark) }
        refreshGutterIcons(bookmark.filePath)
    }

    private fun notifyBookmarksRefreshed() {
        listeners.forEach { it.onBookmarksRefreshed() }
        refreshAllGutterIcons()
    }

    /**
     * 刷新指定文件的 Gutter 图标
     */
    private fun refreshGutterIcons(filePath: String) {
        // 先清除缓存，确保新书签能被显示
        BookmarkLineMarkerProvider.clearCache(filePath)
        
        val virtualFile = findVirtualFile(filePath) ?: return
        ApplicationManager.getApplication().invokeLater {
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@invokeLater
            // 使用 restart 触发重新分析
            DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
        }
    }

    /**
     * 刷新所有打开文件的 Gutter 图标
     */
    private fun refreshAllGutterIcons() {
        // 清除所有缓存
        BookmarkLineMarkerProvider.clearCache()
        
        ApplicationManager.getApplication().invokeLater {
            val openFiles = FileEditorManager.getInstance(project).openFiles
            openFiles.forEach { virtualFile ->
                val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@forEach
                DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
            }
        }
    }

    companion object {
        fun getInstance(project: Project): BookmarkService {
            return project.getService(BookmarkService::class.java)
        }
    }
}

/**
 * 书签变更监听器接口
 */
interface BookmarkChangeListener {
    fun onBookmarkAdded(bookmark: Bookmark) {}
    fun onBookmarkRemoved(bookmark: Bookmark) {}
    fun onBookmarkUpdated(bookmark: Bookmark) {}
    fun onBookmarksRefreshed() {}
}
