package com.longlong.bookmark.ui.dialog

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.longlong.bookmark.export.BookmarkExporter
import com.longlong.bookmark.export.ExportFormat
import java.awt.Component
import javax.swing.*

/**
 * 导出对话框
 */
class ExportDialog(private val project: Project) : DialogWrapper(project) {

    private val formatCombo = JComboBox(ExportFormat.entries.toTypedArray())
    private val includeBookmarksCheck = JCheckBox("书签", true)
    private val includeDiagramsCheck = JCheckBox("导览图", true)
    private val includeTagsCheck = JCheckBox("标签配置", true)

    init {
        title = "导出龙龙书签"
        init()
    }

    override fun createCenterPanel(): JComponent {
        formatCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val format = value as? ExportFormat
                if (format != null) {
                    text = "${format.displayName} (${format.extension})"
                }
                return this
            }
        }

        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.add(includeBookmarksCheck)
        contentPanel.add(includeDiagramsCheck)
        contentPanel.add(includeTagsCheck)
        contentPanel.border = BorderFactory.createTitledBorder("导出内容")

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("格式:", formatCombo)
            .addComponent(contentPanel)
            .panel

        panel.border = JBUI.Borders.empty(10)
        return panel
    }

    override fun doOKAction() {
        val format = formatCombo.selectedItem as ExportFormat
        val exporter = BookmarkExporter(project)

        val descriptor = FileSaverDescriptor(
            "导出书签",
            "选择导出位置",
            format.extension
        )

        val defaultFileName = "longlong-bookmarks.${format.extension}"
        val fileWrapper = FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
            .save(defaultFileName)

        if (fileWrapper != null) {
            try {
                val content = exporter.export(
                    format = format,
                    includeBookmarks = includeBookmarksCheck.isSelected,
                    includeDiagrams = includeDiagramsCheck.isSelected,
                    includeTags = includeTagsCheck.isSelected
                )

                fileWrapper.file.writeText(content)

                NotificationGroupManager.getInstance()
                    .getNotificationGroup("LongLong Bookmark")
                    .createNotification("导出成功: ${fileWrapper.file.path}", NotificationType.INFORMATION)
                    .notify(project)

                super.doOKAction()
            } catch (e: Exception) {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("LongLong Bookmark")
                    .createNotification("导出失败: ${e.message}", NotificationType.ERROR)
                    .notify(project)
            }
        }
    }
}
