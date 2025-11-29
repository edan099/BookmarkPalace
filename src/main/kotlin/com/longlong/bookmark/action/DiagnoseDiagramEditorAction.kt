package com.longlong.bookmark.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.intellij.ui.jcef.JBCefApp
import com.intellij.openapi.diagnostic.Logger
import com.longlong.bookmark.settings.DiagramEditorSettings

/**
 * 诊断导览图编辑器配置
 */
class DiagnoseDiagramEditorAction : AnAction("诊断导览图编辑器") {

    private val logger = Logger.getInstance(DiagnoseDiagramEditorAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = DiagramEditorSettings.getInstance()
        
        // 收集诊断信息
        val jcefSupported = JBCefApp.isSupported()
        val useDrawio = settings.useDrawioEditor
        val drawioUrl = settings.drawioUrl
        val canUseDrawio = jcefSupported && useDrawio
        
        // 构建诊断报告
        val report = buildString {
            appendLine("🔍 导览图编辑器诊断报告")
            appendLine("=" .repeat(50))
            appendLine()
            
            appendLine("📊 配置状态:")
            appendLine("  useDrawioEditor: ${if (useDrawio) "✅ true" else "❌ false"}")
            appendLine("  Draw.io URL: $drawioUrl")
            appendLine()
            
            appendLine("🖥️ 系统支持:")
            appendLine("  jCEF 支持: ${if (jcefSupported) "✅ 是" else "❌ 否"}")
            if (!jcefSupported) {
                appendLine("  可能原因: IDE 版本 < 2020.2 或 JBR 不支持")
            }
            appendLine()
            
            appendLine("🎯 当前会使用的编辑器:")
            if (canUseDrawio) {
                appendLine("  ✅ DrawioJcefEditor (Draw.io)")
            } else {
                appendLine("  ⚪ DiagramFileEditor (原生 Swing)")
            }
            appendLine()
            
            appendLine("📝 诊断结论:")
            when {
                canUseDrawio -> {
                    appendLine("  ✅ 配置正确，应该能使用 Draw.io 编辑器")
                    appendLine()
                    appendLine("如果打开 .lldiagram 文件后仍然是 Swing 编辑器，")
                    appendLine("请尝试：")
                    appendLine("  1. 关闭所有导览图文件")
                    appendLine("  2. 重新打开")
                    appendLine("  3. 查看控制台日志")
                }
                !useDrawio -> {
                    appendLine("  ⚠️ 未启用 Draw.io 编辑器")
                    appendLine()
                    appendLine("解决方法：")
                    appendLine("  1. 点击菜单: Tools → BookmarkPalace → 切换导览图编辑器")
                    appendLine("  2. 重新打开导览图文件")
                }
                !jcefSupported -> {
                    appendLine("  ❌ 系统不支持 jCEF")
                    appendLine()
                    appendLine("解决方法：")
                    appendLine("  1. 升级 IDE 到 2020.2 或更高版本")
                    appendLine("  2. 确保使用 JetBrains Runtime (JBR)")
                    appendLine("  3. 查看 Help → About 确认 Runtime 版本")
                }
            }
            appendLine()
            appendLine("=" .repeat(50))
            appendLine("💡 更多帮助: 查看 TROUBLESHOOTING.md")
        }
        
        // 显示报告
        Messages.showInfoMessage(project, report, "导览图编辑器诊断")
        
        // 同时输出到控制台
        logger.debug(report)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.text = "诊断导览图编辑器"
        e.presentation.description = "检查 Draw.io 编辑器配置和 jCEF 支持状态"
    }
}
