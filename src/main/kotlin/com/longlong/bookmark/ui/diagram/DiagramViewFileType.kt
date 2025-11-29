package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * 导览图查看模式文件类型
 */
object DiagramViewFileType : FileType {
    override fun getName(): String = "LongLong Diagram View"
    override fun getDescription(): String = "BookmarkPalace Diagram View File (Read-only)"
    override fun getDefaultExtension(): String = "lldiagramview"
    override fun getIcon(): Icon? = null
    override fun isBinary(): Boolean = false
    override fun isReadOnly(): Boolean = true
}
