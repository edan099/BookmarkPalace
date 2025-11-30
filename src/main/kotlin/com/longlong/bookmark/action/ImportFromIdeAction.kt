package com.longlong.bookmark.action

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.longlong.bookmark.export.IdeBookmarkImporter
import com.longlong.bookmark.i18n.Messages as I18nMessages
import javax.swing.*

/**
 * 从 IDE 自带书签导入的 Action
 */
class ImportFromIdeAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 先检查有多少可导入的书签
        val importer = IdeBookmarkImporter(project)
        val count = importer.getIdeBookmarkCount()

        if (count == 0) {
            Messages.showInfoMessage(
                project,
                if (I18nMessages.isEnglish()) 
                    "No line bookmarks found in IDE.\nPlease add some bookmarks first using IDE's bookmark feature (F11)."
                else 
                    "IDE 中没有找到行书签。\n请先使用 IDE 的书签功能（F11）添加一些书签。",
                if (I18nMessages.isEnglish()) "No Bookmarks" else "无书签"
            )
            return
        }

        // 显示导入对话框
        val dialog = ImportFromIdeDialog(project, count)
        if (dialog.showAndGet()) {
            val result = importer.importFromIde(dialog.isReplaceMode())

            val message = if (I18nMessages.isEnglish()) {
                if (result.errors.isEmpty()) {
                    "Successfully imported ${result.bookmarkCount} bookmarks from IDE"
                } else {
                    "Imported ${result.bookmarkCount} bookmarks, ${result.errors.size} errors"
                }
            } else {
                if (result.errors.isEmpty()) {
                    "成功从 IDE 导入 ${result.bookmarkCount} 个书签"
                } else {
                    "导入了 ${result.bookmarkCount} 个书签，${result.errors.size} 个错误"
                }
            }

            NotificationGroupManager.getInstance()
                .getNotificationGroup("BookmarkPalace")
                .createNotification(
                    message,
                    if (result.errors.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING
                )
                .notify(project)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
        e.presentation.text = if (I18nMessages.isEnglish()) 
            "Import from IDE Bookmarks" 
        else 
            "从 IDE 书签导入"
    }
}

/**
 * 从 IDE 书签导入的对话框
 */
class ImportFromIdeDialog(
    private val project: com.intellij.openapi.project.Project,
    private val bookmarkCount: Int
) : DialogWrapper(project) {

    private val mergeRadio = JRadioButton(
        if (I18nMessages.isEnglish()) "Merge (keep existing bookmarks)" else "合并（保留现有书签）",
        true
    )
    private val replaceRadio = JRadioButton(
        if (I18nMessages.isEnglish()) "Replace (clear existing bookmarks)" else "替换（清空现有书签）"
    )

    init {
        title = if (I18nMessages.isEnglish()) "Import from IDE Bookmarks" else "从 IDE 书签导入"
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
        modePanel.border = BorderFactory.createTitledBorder(
            if (I18nMessages.isEnglish()) "Import Mode" else "导入方式"
        )

        // 提示信息
        val infoLabel = JLabel(
            if (I18nMessages.isEnglish())
                "<html>Found <b>$bookmarkCount</b> line bookmarks in IDE.<br><br>" +
                "• Bookmarks will be imported with default blue color<br>" +
                "• Bookmark group names will be converted to tags<br>" +
                "• Only line bookmarks are supported (file/folder bookmarks are skipped)</html>"
            else
                "<html>在 IDE 中找到 <b>$bookmarkCount</b> 个行书签。<br><br>" +
                "• 书签将以默认蓝色导入<br>" +
                "• 书签组名将转换为标签<br>" +
                "• 仅支持行书签（文件/文件夹书签将被跳过）</html>"
        )
        infoLabel.border = JBUI.Borders.empty(10)

        val panel = FormBuilder.createFormBuilder()
            .addComponent(infoLabel)
            .addComponent(modePanel)
            .panel

        panel.border = JBUI.Borders.empty(10)
        return panel
    }

    fun isReplaceMode(): Boolean = replaceRadio.isSelected
}
