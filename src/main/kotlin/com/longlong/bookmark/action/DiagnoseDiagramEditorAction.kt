package com.longlong.bookmark.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages as IdeMessages
import com.intellij.ui.jcef.JBCefApp
import com.intellij.openapi.diagnostic.Logger
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.settings.DiagramEditorSettings

/**
 * 诊断导览图编辑器配置
 */
class DiagnoseDiagramEditorAction : AnAction() {

    private val logger = Logger.getInstance(DiagnoseDiagramEditorAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = DiagramEditorSettings.getInstance()
        val isEnglish = Messages.isEnglish()
        
        // 收集诊断信息
        val jcefSupported = JBCefApp.isSupported()
        val useDrawio = settings.useDrawioEditor
        val canUseDrawio = jcefSupported && useDrawio
        
        // 构建诊断报告
        val report = if (isEnglish) {
            buildEnglishReport(jcefSupported, useDrawio, canUseDrawio)
        } else {
            buildChineseReport(jcefSupported, useDrawio, canUseDrawio)
        }
        
        // 显示报告
        val title = if (isEnglish) "Diagram Editor Diagnostic" else "导览图编辑器诊断"
        IdeMessages.showInfoMessage(project, report, title)
        
        // 同时输出到控制台
        logger.debug(report)
    }
    
    private fun buildChineseReport(jcefSupported: Boolean, useDrawio: Boolean, canUseDrawio: Boolean): String {
        return buildString {
            appendLine("📊 环境检测")
            appendLine("─────────────────────────")
            appendLine("• jCEF 浏览器支持: ${if (jcefSupported) "✅ 支持" else "❌ 不支持"}")
            appendLine("• Draw.io 编辑器: ${if (useDrawio) "✅ 已启用" else "⚪ 未启用"}")
            appendLine()
            
            appendLine("🎯 当前编辑器")
            appendLine("─────────────────────────")
            if (canUseDrawio) {
                appendLine("✅ Draw.io 可视化编辑器")
            } else {
                appendLine("⚪ 原生 Swing 编辑器")
            }
            appendLine()
            
            when {
                canUseDrawio -> {
                    appendLine("✅ 一切正常！")
                    appendLine()
                    appendLine("💡 如遇问题，请尝试：")
                    appendLine("   关闭导览图 → 重新打开")
                }
                !jcefSupported -> {
                    appendLine("⚠️ 当前 IDE 不支持 jCEF")
                    appendLine()
                    appendLine("💡 解决方法：")
                    appendLine("   升级到 IntelliJ IDEA 2020.2+")
                }
                !useDrawio -> {
                    appendLine("💡 如需使用 Draw.io 编辑器：")
                    appendLine("   Tools → 书签宫殿 → 切换导览图编辑器")
                }
            }
        }
    }
    
    private fun buildEnglishReport(jcefSupported: Boolean, useDrawio: Boolean, canUseDrawio: Boolean): String {
        return buildString {
            appendLine("📊 Environment Check")
            appendLine("─────────────────────────")
            appendLine("• jCEF Browser: ${if (jcefSupported) "✅ Supported" else "❌ Not Supported"}")
            appendLine("• Draw.io Editor: ${if (useDrawio) "✅ Enabled" else "⚪ Disabled"}")
            appendLine()
            
            appendLine("🎯 Current Editor")
            appendLine("─────────────────────────")
            if (canUseDrawio) {
                appendLine("✅ Draw.io Visual Editor")
            } else {
                appendLine("⚪ Native Swing Editor")
            }
            appendLine()
            
            when {
                canUseDrawio -> {
                    appendLine("✅ All Good!")
                    appendLine()
                    appendLine("💡 If issues occur:")
                    appendLine("   Close diagram → Reopen")
                }
                !jcefSupported -> {
                    appendLine("⚠️ jCEF not supported in this IDE")
                    appendLine()
                    appendLine("💡 Solution:")
                    appendLine("   Upgrade to IntelliJ IDEA 2020.2+")
                }
                !useDrawio -> {
                    appendLine("💡 To enable Draw.io editor:")
                    appendLine("   Tools → BookmarkPalace → Toggle Diagram Editor")
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.text = Messages.diagnoseDiagramEditor
        e.presentation.description = if (Messages.isEnglish()) {
            "Check Draw.io editor configuration and jCEF support status"
        } else {
            "检查 Draw.io 编辑器配置和 jCEF 支持状态"
        }
    }
}
