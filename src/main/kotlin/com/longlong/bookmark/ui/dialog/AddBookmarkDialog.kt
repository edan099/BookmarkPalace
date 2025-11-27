package com.longlong.bookmark.ui.dialog

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.longlong.bookmark.model.BookmarkColor
import com.longlong.bookmark.service.TagService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

/**
 * 添加书签对话框
 */
class AddBookmarkDialog(
    private val project: Project,
    private val editor: Editor
) : DialogWrapper(project) {

    private val aliasField = JBTextField()
    private val colorCombo = ComboBox(BookmarkColor.entries.toTypedArray())
    private val tagField = JBTextField()
    private val commentArea = JBTextArea(3, 40)
    private val codePreview = JBTextArea(5, 40)

    init {
        title = "添加龙龙书签"
        init()

        // 初始化代码预览
        val selectionModel = editor.selectionModel
        val document = editor.document

        val codeSnippet = if (selectionModel.hasSelection()) {
            selectionModel.selectedText ?: ""
        } else {
            val caretOffset = editor.caretModel.offset
            val lineNumber = document.getLineNumber(caretOffset)
            val startOffset = document.getLineStartOffset(lineNumber)
            val endOffset = document.getLineEndOffset(lineNumber)
            document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset))
        }

        codePreview.text = codeSnippet
        codePreview.isEditable = false

        // 自动生成别名
        val firstLine = codeSnippet.lines().firstOrNull()?.trim() ?: ""
        aliasField.text = if (firstLine.length > 30) firstLine.take(30) + "..." else firstLine

        // 获取文件名
        val virtualFile = FileDocumentManager.getInstance().getFile(document)
        val fileName = virtualFile?.name ?: "Unknown"

        // 更新标题
        title = "添加龙龙书签 - $fileName"
    }

    override fun createCenterPanel(): JComponent {
        // 颜色选择器渲染
        colorCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val color = value as? BookmarkColor
                if (color != null) {
                    text = "${getColorEmoji(color)} ${color.displayName}"
                }
                return this
            }
        }
        colorCombo.selectedItem = BookmarkColor.BLUE

        // 标签提示
        val tagService = TagService.getInstance(project)
        val existingTags = tagService.getAllTags().joinToString(", ") { it.name }
        tagField.toolTipText = "多个标签用逗号分隔。已有标签: $existingTags"

        // 代码预览
        codePreview.border = BorderFactory.createTitledBorder("代码预览")
        val codeScrollPane = JBScrollPane(codePreview)
        codeScrollPane.preferredSize = Dimension(400, 100)

        // 注释
        commentArea.lineWrap = true
        val commentScrollPane = JBScrollPane(commentArea)
        commentScrollPane.preferredSize = Dimension(400, 80)

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("别名:", aliasField)
            .addLabeledComponent("颜色:", colorCombo)
            .addLabeledComponent("标签:", tagField)
            .addLabeledComponent("注释:", commentScrollPane)
            .addComponent(codeScrollPane)
            .panel

        panel.border = JBUI.Borders.empty(10)
        return panel
    }

    private fun getColorEmoji(color: BookmarkColor): String {
        return when (color) {
            BookmarkColor.RED -> "🔴"
            BookmarkColor.ORANGE -> "🟠"
            BookmarkColor.YELLOW -> "🟡"
            BookmarkColor.GREEN -> "🟢"
            BookmarkColor.BLUE -> "🔵"
            BookmarkColor.PURPLE -> "🟣"
            BookmarkColor.PINK -> "💗"
            BookmarkColor.CYAN -> "🔷"
            BookmarkColor.GRAY -> "⚪"
        }
    }

    fun getAlias(): String = aliasField.text.trim()

    fun getColor(): BookmarkColor = colorCombo.selectedItem as BookmarkColor

    fun getTags(): List<String> = tagField.text
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    fun getComment(): String = commentArea.text.trim()
}
