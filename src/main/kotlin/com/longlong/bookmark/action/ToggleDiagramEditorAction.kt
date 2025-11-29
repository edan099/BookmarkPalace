package com.longlong.bookmark.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.jcef.JBCefApp
import com.longlong.bookmark.i18n.Messages as I18n
import com.intellij.openapi.diagnostic.Logger
import com.longlong.bookmark.settings.DiagramEditorSettings

/**
 * 切换导览图编辑器模式（原生 Swing ↔ Draw.io jCEF）
 */
class ToggleDiagramEditorAction : AnAction() {

    private val logger = Logger.getInstance(ToggleDiagramEditorAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = DiagramEditorSettings.getInstance()
        
        val jcefSupported = JBCefApp.isSupported()
        
        logger.debug("=== Toggle Diagram Editor ===")
        logger.debug("Current setting: useDrawioEditor = ${settings.useDrawioEditor}")
        logger.debug("jCEF supported: $jcefSupported")
        
        // 检查 jCEF 支持
        if (!settings.useDrawioEditor && !jcefSupported) {
            val msg = """
                jCEF is not supported in your IDE.
                
                Requirements:
                - IntelliJ IDEA 2020.2 or later
                - JetBrains Runtime with jCEF
                
                Current status: jCEF NOT available
            """.trimIndent()
            
            Messages.showErrorDialog(project, msg, "jCEF Not Supported")
            logger.debug("❌ jCEF not supported, cannot switch to Draw.io editor")
            return
        }
        
        // 切换模式
        settings.useDrawioEditor = !settings.useDrawioEditor
        logger.debug("✅ Switched to: useDrawioEditor = ${settings.useDrawioEditor}")
        
        val newMode = if (settings.useDrawioEditor) "Draw.io (jCEF)" else "Native Swing"
        
        // 提示用户重新打开文件
        val message = """
            ✅ Diagram editor switched to: $newMode
            
            jCEF Support: ${if (jcefSupported) "✅ Available" else "❌ Not Available"}
            
            Please reopen diagram files (.lldiagram) to apply the change.
            
            You can check the Run console for debug logs.
        """.trimIndent()
        
        Messages.showInfoMessage(project, message, "Editor Mode Changed")
        
        // 自动关闭当前打开的导览图文件
        val fileEditorManager = FileEditorManager.getInstance(project)
        var closedCount = 0
        fileEditorManager.openFiles.forEach { file ->
            if (file.extension == "lldiagram") {
                fileEditorManager.closeFile(file)
                closedCount++
                logger.debug("Closed diagram file: ${file.name}")
            }
        }
        logger.debug("Closed $closedCount diagram file(s)")
    }

    override fun update(e: AnActionEvent) {
        val settings = DiagramEditorSettings.getInstance()
        val currentMode = if (settings.useDrawioEditor) "Draw.io" else "Swing"
        e.presentation.text = "Switch Diagram Editor (Current: $currentMode)"
    }
}
