package com.longlong.bookmark.ui.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.longlong.bookmark.model.Bookmark
import com.longlong.bookmark.model.BookmarkColor
import com.longlong.bookmark.model.BookmarkStatus
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.service.TagService
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

/**
 * 编辑书签对话框
 */
class EditBookmarkDialog(
    private val project: Project,
    private val bookmark: Bookmark
) : DialogWrapper(project) {

    private val aliasField = JBTextField()
    private val colorCombo = ComboBox(BookmarkColor.values())
    private val tagField = JBTextField()
    private val commentArea = JBTextArea(3, 40)
    private val codePreview = JBTextArea(5, 40)

    init {
        title = Messages.editBookmark
        init()

        // 填充现有数据
        aliasField.text = bookmark.alias
        colorCombo.selectedItem = bookmark.color
        tagField.text = bookmark.tags.joinToString(", ")
        commentArea.text = bookmark.comment
        codePreview.text = bookmark.codeSnippet
        codePreview.isEditable = false
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

        // 标签提示
        val tagService = TagService.getInstance(project)
        val existingTags = tagService.getAllTags().joinToString(", ") { it.name }
        tagField.toolTipText = "多个标签用逗号分隔。已有标签: $existingTags"

        // 代码预览
        val codePanel = JPanel()
        codePanel.layout = BoxLayout(codePanel, BoxLayout.Y_AXIS)
        codePanel.border = BorderFactory.createTitledBorder("代码预览")
        val codeScrollPane = JBScrollPane(codePreview)
        codeScrollPane.preferredSize = Dimension(400, 100)
        codePanel.add(codeScrollPane)

        // 状态显示
        val statusLabel = JLabel()
        when (bookmark.status) {
            BookmarkStatus.VALID -> {
                statusLabel.text = "✅ 状态正常"
                statusLabel.foreground = java.awt.Color(67, 160, 71)
            }
            BookmarkStatus.MISSING -> {
                statusLabel.text = "❌ 书签失效 - 原代码已删除"
                statusLabel.foreground = java.awt.Color.RED
            }
            BookmarkStatus.OUTDATED -> {
                statusLabel.text = "⚠️ 书签可能过期"
                statusLabel.foreground = java.awt.Color.ORANGE
            }
        }

        // 如果书签失效，显示原始代码
        if (bookmark.status == BookmarkStatus.MISSING && bookmark.history.originalSnippet.isNotEmpty()) {
            codePreview.text = "【原代码（已删除）】\n${bookmark.history.originalSnippet}"
        }

        // 注释
        commentArea.lineWrap = true
        val commentScrollPane = JBScrollPane(commentArea)
        commentScrollPane.preferredSize = Dimension(400, 80)

        // 位置信息
        val locationLabel = JLabel("${bookmark.filePath}:${bookmark.startLine + 1}")
        locationLabel.foreground = java.awt.Color.GRAY

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("文件:", locationLabel)
            .addLabeledComponent("状态:", statusLabel)
            .addLabeledComponent("别名:", aliasField)
            .addLabeledComponent("颜色:", colorCombo)
            .addLabeledComponent("标签:", tagField)
            .addLabeledComponent("注释:", commentScrollPane)
            .addComponent(codePanel)
            .panel

        panel.border = JBUI.Borders.empty(10)
        return panel
    }

    override fun doOKAction() {
        // 更新书签数据
        bookmark.alias = aliasField.text.trim()
        bookmark.color = colorCombo.selectedItem as BookmarkColor
        bookmark.tags = tagField.text
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        bookmark.comment = commentArea.text.trim()

        super.doOKAction()
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
}
