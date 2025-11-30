package com.longlong.bookmark.ui.dialog

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.longlong.bookmark.export.BookmarkImporter
import com.longlong.bookmark.export.IdeBookmarkImporter
import com.longlong.bookmark.i18n.Messages
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*

/**
 * 导入对话框
 */
class ImportDialog(private val project: Project) : DialogWrapper(project) {

    private val contentArea = JBTextArea(15, 50)
    private val mergeRadio = JRadioButton("合并（保留现有书签）", true)
    private val replaceRadio = JRadioButton("替换（清空现有书签）")

    init {
        title = Messages.import
        init()
    }

    override fun createCenterPanel(): JComponent {
        // 导入方式
        val modeGroup = ButtonGroup()
        modeGroup.add(mergeRadio)
        modeGroup.add(replaceRadio)

        val modePanel = JPanel()
        modePanel.layout = BoxLayout(modePanel, BoxLayout.Y_AXIS)
        modePanel.add(mergeRadio)
        modePanel.add(replaceRadio)
        modePanel.border = BorderFactory.createTitledBorder("导入方式")

        // 内容区域
        contentArea.lineWrap = true
        val scrollPane = JBScrollPane(contentArea)
        scrollPane.preferredSize = Dimension(500, 300)

        val contentPanel = JPanel(BorderLayout())
        contentPanel.border = BorderFactory.createTitledBorder("导入内容（JSON 或粘贴 AI 返回的内容）")
        contentPanel.add(scrollPane, BorderLayout.CENTER)

        // 按钮面板
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
        
        // 从文件加载按钮
        val loadButton = JButton(if (Messages.isEnglish()) "Load from File..." else "从文件加载...")
        loadButton.addActionListener {
            loadFromFile()
        }
        buttonPanel.add(loadButton)
        
        // 从 IDE 书签导入按钮
        val ideImportButton = JButton(if (Messages.isEnglish()) "Import from IDE Bookmarks" else "从 IDE 书签导入")
        ideImportButton.addActionListener {
            importFromIde()
        }
        buttonPanel.add(ideImportButton)

        contentPanel.add(buttonPanel, BorderLayout.SOUTH)

        val panel = FormBuilder.createFormBuilder()
            .addComponent(modePanel)
            .addComponent(contentPanel)
            .panel

        panel.border = JBUI.Borders.empty(10)
        return panel
    }

    private fun loadFromFile() {
        val descriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()
            .withFileFilter { it.extension in listOf("json", "md", "txt") }
            .withTitle("选择导入文件")

        val file = FileChooser.chooseFile(descriptor, project, null)
        if (file != null) {
            try {
                contentArea.text = String(file.contentsToByteArray())
            } catch (e: Exception) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("BookmarkPalace")
                    .createNotification("读取文件失败: ${e.message}", NotificationType.ERROR)
                    .notify(project)
            }
        }
    }

    /**
     * 从 IDE 自带书签导入
     */
    private fun importFromIde() {
        val importer = IdeBookmarkImporter(project)
        val count = importer.getIdeBookmarkCount()
        
        if (count == 0) {
            JOptionPane.showMessageDialog(
                contentPane,
                if (Messages.isEnglish()) 
                    "No line bookmarks found in IDE." 
                else 
                    "IDE 中没有找到行书签。",
                if (Messages.isEnglish()) "No Bookmarks" else "无书签",
                JOptionPane.INFORMATION_MESSAGE
            )
            return
        }
        
        val confirm = JOptionPane.showConfirmDialog(
            contentPane,
            if (Messages.isEnglish())
                "Found $count line bookmarks in IDE.\nImport them now?"
            else
                "在 IDE 中找到 $count 个行书签。\n现在导入吗？",
            if (Messages.isEnglish()) "Import from IDE" else "从 IDE 导入",
            JOptionPane.YES_NO_OPTION
        )
        
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                val result = importer.importFromIde(replaceRadio.isSelected)
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("BookmarkPalace")
                    .createNotification(
                        if (Messages.isEnglish())
                            "Successfully imported ${result.bookmarkCount} bookmarks from IDE"
                        else
                            "成功从 IDE 导入 ${result.bookmarkCount} 个书签",
                        NotificationType.INFORMATION
                    )
                    .notify(project)
                super.doOKAction()
            } catch (e: Exception) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("BookmarkPalace")
                    .createNotification(
                        if (Messages.isEnglish()) "Import failed: ${e.message}" else "导入失败: ${e.message}",
                        NotificationType.ERROR
                    )
                    .notify(project)
            }
        }
    }

    override fun doOKAction() {
        val content = contentArea.text.trim()
        if (content.isEmpty()) {
            JOptionPane.showMessageDialog(
                contentPane,
                "请输入导入内容",
                "错误",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        try {
            val importer = BookmarkImporter(project)
            val result = importer.import(content, replaceRadio.isSelected)

            NotificationGroupManager.getInstance()
                .getNotificationGroup("BookmarkPalace")
                .createNotification(
                    "导入成功: ${result.bookmarkCount} 个书签, ${result.diagramCount} 个导览图",
                    NotificationType.INFORMATION
                )
                .notify(project)

            super.doOKAction()
        } catch (e: Exception) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("BookmarkPalace")
                .createNotification("导入失败: ${e.message}", NotificationType.ERROR)
                .notify(project)
        }
    }
}
