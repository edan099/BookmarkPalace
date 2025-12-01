package com.longlong.bookmark.ui.dialog

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.longlong.bookmark.model.Bookmark
import com.longlong.bookmark.model.BookmarkColor
import com.longlong.bookmark.model.BookmarkStatus
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.service.BookmarkService
import com.longlong.bookmark.service.TagService
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.*

/**
 * 编辑书签对话框
 * 所有修改在点击确定时统一保存
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
    
    // 位置编辑字段
    private val filePathField = JBTextField()
    private val startLineField = JBTextField(5)
    private val endLineField = JBTextField(5)
    private lateinit var statusLabel: JLabel
    
    // 记录是否位置有变化
    private var locationChanged = false
    private var pendingFilePath: String = ""
    private var pendingStartLine: Int = 0
    private var pendingEndLine: Int = 0
    private var pendingCodeSnippet: String = ""

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
        
        // 位置数据
        filePathField.text = bookmark.filePath
        startLineField.text = (bookmark.startLine + 1).toString()
        endLineField.text = (bookmark.endLine + 1).toString()
        
        // 初始化待保存的位置数据
        pendingFilePath = bookmark.filePath
        pendingStartLine = bookmark.startLine
        pendingEndLine = bookmark.endLine
        pendingCodeSnippet = bookmark.codeSnippet
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
        tagField.toolTipText = if (Messages.isEnglish()) 
            "Separate multiple tags with commas. Existing: $existingTags"
        else 
            "多个标签用逗号分隔。已有标签: $existingTags"

        // 代码预览
        val codePanel = JPanel()
        codePanel.layout = BoxLayout(codePanel, BoxLayout.Y_AXIS)
        codePanel.border = BorderFactory.createTitledBorder(Messages.codePreview)
        val codeScrollPane = JBScrollPane(codePreview)
        codeScrollPane.preferredSize = Dimension(450, 100)
        codePanel.add(codeScrollPane)

        // 状态显示
        statusLabel = JLabel()
        updateStatusLabel()

        // 如果书签失效，显示原始代码
        if (bookmark.status == BookmarkStatus.MISSING && bookmark.history.originalSnippet.isNotEmpty()) {
            val prefix = if (Messages.isEnglish()) "【Original (deleted)】\n" else "【原代码（已删除）】\n"
            codePreview.text = prefix + bookmark.history.originalSnippet
        }

        // 注释
        commentArea.lineWrap = true
        val commentScrollPane = JBScrollPane(commentArea)
        commentScrollPane.preferredSize = Dimension(450, 80)

        // ===== 位置编辑面板 =====
        val locationPanel = JPanel(GridBagLayout())
        locationPanel.border = BorderFactory.createTitledBorder(Messages.location)
        val gbc = GridBagConstraints().apply {
            insets = Insets(2, 5, 2, 5)
            anchor = GridBagConstraints.WEST
        }
        
        // 文件路径
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE
        locationPanel.add(JLabel("${Messages.filePath}:"), gbc)
        
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL
        locationPanel.add(filePathField, gbc)
        
        gbc.gridx = 2; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE
        val browseButton = JButton(Messages.browseFile)
        browseButton.addActionListener { browseFile() }
        locationPanel.add(browseButton, gbc)
        
        // 行号
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        locationPanel.add(JLabel("${Messages.startLine}:"), gbc)
        
        gbc.gridx = 1; gbc.weightx = 0.0; gbc.fill = GridBagConstraints.NONE
        val linePanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        linePanel.add(startLineField)
        linePanel.add(JLabel("-"))
        linePanel.add(endLineField)
        locationPanel.add(linePanel, gbc)
        
        // 按钮
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        
        // 预览按钮 - 验证位置并更新代码预览
        val previewButton = JButton(if (Messages.isEnglish()) "👁 Preview" else "👁 预览")
        previewButton.toolTipText = if (Messages.isEnglish()) 
            "Preview code at the specified location" 
        else 
            "预览指定位置的代码"
        previewButton.addActionListener { previewLocationChange() }
        buttonPanel.add(previewButton)
        
        // 跳转按钮
        val jumpButton = JButton(Messages.goToLocation)
        jumpButton.addActionListener {
            BookmarkService.getInstance(project).navigateToBookmark(bookmark)
        }
        buttonPanel.add(jumpButton)
        
        locationPanel.add(buttonPanel, gbc)

        val panel = FormBuilder.createFormBuilder()
            .addComponent(locationPanel)
            .addLabeledComponent("${if (Messages.isEnglish()) "Status" else "状态"}:", statusLabel)
            .addLabeledComponent("${if (Messages.isEnglish()) "Alias" else "别名"}:", aliasField)
            .addLabeledComponent("${if (Messages.isEnglish()) "Color" else "颜色"}:", colorCombo)
            .addLabeledComponent("${if (Messages.isEnglish()) "Tags" else "标签"}:", tagField)
            .addLabeledComponent("${if (Messages.isEnglish()) "Comment" else "注释"}:", commentScrollPane)
            .addComponent(codePanel)
            .panel

        panel.border = JBUI.Borders.empty(10)
        panel.preferredSize = Dimension(500, 500)
        return panel
    }
    
    private fun updateStatusLabel() {
        when (bookmark.status) {
            BookmarkStatus.VALID -> {
                statusLabel.text = Messages.statusValid
                statusLabel.foreground = java.awt.Color(67, 160, 71)
            }
            BookmarkStatus.MISSING -> {
                statusLabel.text = Messages.statusMissing
                statusLabel.foreground = java.awt.Color.RED
            }
            BookmarkStatus.OUTDATED -> {
                statusLabel.text = Messages.statusOutdated
                statusLabel.foreground = java.awt.Color.ORANGE
            }
        }
    }
    
    private fun browseFile() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
        val projectDir = ProjectRootManager.getInstance(project).contentRoots.firstOrNull()
        if (projectDir != null) {
            descriptor.setRoots(projectDir)
        }
        val file = FileChooser.chooseFile(descriptor, project, null)
        if (file != null) {
            val basePath = project.basePath ?: ""
            val relativePath = if (file.path.startsWith(basePath)) {
                file.path.removePrefix(basePath).removePrefix("/")
            } else {
                file.path
            }
            filePathField.text = relativePath
        }
    }
    
    /**
     * 预览位置变更 - 只更新预览，不保存
     */
    private fun previewLocationChange(): Boolean {
        val filePath = filePathField.text.trim()
        val startLineText = startLineField.text.trim()
        val endLineText = endLineField.text.trim()
        
        // 验证行号
        val startLine = startLineText.toIntOrNull()
        val endLine = endLineText.toIntOrNull() ?: startLine
        
        if (startLine == null || startLine < 1) {
            JOptionPane.showMessageDialog(
                contentPane,
                Messages.invalidLineNumber,
                if (Messages.isEnglish()) "Error" else "错误",
                JOptionPane.ERROR_MESSAGE
            )
            return false
        }
        
        // 查找文件
        val basePath = project.basePath ?: ""
        val absolutePath = if (filePath.startsWith("/")) filePath else "$basePath/$filePath"
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(absolutePath)
        
        if (virtualFile == null || !virtualFile.exists()) {
            JOptionPane.showMessageDialog(
                contentPane,
                Messages.fileNotFound + ": $filePath",
                if (Messages.isEnglish()) "Error" else "错误",
                JOptionPane.ERROR_MESSAGE
            )
            return false
        }
        
        // 获取文档并读取代码
        val document = ReadAction.compute<com.intellij.openapi.editor.Document?, Throwable> {
            FileDocumentManager.getInstance().getDocument(virtualFile)
        }
        
        if (document == null) {
            JOptionPane.showMessageDialog(
                contentPane,
                Messages.locationNotFound,
                if (Messages.isEnglish()) "Error" else "错误",
                JOptionPane.ERROR_MESSAGE
            )
            return false
        }
        
        // 验证行号范围
        val zeroStartLine = startLine - 1
        val zeroEndLine = (endLine ?: startLine) - 1
        
        if (zeroStartLine >= document.lineCount || zeroEndLine >= document.lineCount || zeroStartLine < 0 || zeroEndLine < 0) {
            JOptionPane.showMessageDialog(
                contentPane,
                Messages.invalidLineNumber + " (max: ${document.lineCount})",
                if (Messages.isEnglish()) "Error" else "错误",
                JOptionPane.ERROR_MESSAGE
            )
            return false
        }
        
        // 获取代码片段
        val startOffset = document.getLineStartOffset(zeroStartLine)
        val endOffset = document.getLineEndOffset(zeroEndLine)
        val codeSnippet = document.getText(TextRange(startOffset, endOffset))
        
        // 保存到待保存变量
        pendingFilePath = filePath
        pendingStartLine = zeroStartLine
        pendingEndLine = zeroEndLine
        pendingCodeSnippet = codeSnippet
        locationChanged = true
        
        // 更新 UI 预览
        codePreview.text = codeSnippet
        statusLabel.text = if (Messages.isEnglish()) "📝 Location previewed (save on OK)" else "📝 位置已预览（确定时保存）"
        statusLabel.foreground = java.awt.Color(0, 120, 215)
        
        return true
    }

    override fun doOKAction() {
        // 检查位置是否有变更但未预览
        val currentFilePath = filePathField.text.trim()
        val currentStartLine = startLineField.text.trim().toIntOrNull()?.minus(1) ?: bookmark.startLine
        val currentEndLine = endLineField.text.trim().toIntOrNull()?.minus(1) ?: bookmark.endLine
        
        // 如果位置字段有修改但未点击预览，自动验证
        if (currentFilePath != pendingFilePath || currentStartLine != pendingStartLine || currentEndLine != pendingEndLine) {
            if (!previewLocationChange()) {
                return // 验证失败，不关闭对话框
            }
        }
        
        // 更新书签基本数据
        bookmark.alias = aliasField.text.trim()
        bookmark.color = colorCombo.selectedItem as BookmarkColor
        bookmark.tags = tagField.text
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toMutableList()
        bookmark.comment = commentArea.text.trim()
        
        // 如果位置有变更，更新位置数据和 history
        if (locationChanged) {
            bookmark.filePath = pendingFilePath
            bookmark.startLine = pendingStartLine
            bookmark.endLine = pendingEndLine
            bookmark.codeSnippet = pendingCodeSnippet
            // 更新 history 为新位置，防止刷新时恢复旧位置
            bookmark.history = bookmark.history.copy(
                originalSnippet = pendingCodeSnippet,
                originalStartLine = pendingStartLine,
                originalEndLine = pendingEndLine,
                updatedAt = System.currentTimeMillis()
            )
            bookmark.markAsValid()
        }
        
        // 统一保存
        BookmarkService.getInstance(project).updateBookmark(bookmark)

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
