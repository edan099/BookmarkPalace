package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * 导览图文件类型
 */
object DiagramFileType : FileType {
    override fun getName(): String = "LongLong Diagram"
    override fun getDescription(): String = "BookmarkPalace Diagram File"
    override fun getDefaultExtension(): String = "lldiagram"
    override fun getIcon(): Icon? = null
    override fun isBinary(): Boolean = false
    override fun isReadOnly(): Boolean = false
}
