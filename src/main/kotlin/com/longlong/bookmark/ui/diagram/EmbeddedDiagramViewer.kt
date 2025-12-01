package com.longlong.bookmark.ui.diagram

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import com.longlong.bookmark.i18n.Messages
import com.longlong.bookmark.model.Diagram
import com.longlong.bookmark.service.BookmarkService
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLifeSpanHandlerAdapter
import javax.swing.JComponent

/**
 * 嵌入式导览图查看器 - 用于侧边栏中显示导览图
 * 复用同一个 browser，切换时只更新 XML 数据
 */
class EmbeddedDiagramViewer(
    private val project: Project,
    private val onReadyCallback: ((Boolean) -> Unit)? = null
) {
    private val logger = Logger.getInstance(EmbeddedDiagramViewer::class.java)
    private val bookmarkService = BookmarkService.getInstance(project)
    
    private val browser = JBCefBrowser()
    private var drawioReady = false
    private var pendingXml: String? = null
    private var currentDiagramId: String? = null
    
    val component: JComponent get() = browser.component
    val isReady: Boolean get() = drawioReady
    
    init {
        setupPopupHandler()
        loadViewerPage()
    }
    
    /**
     * 加载指定导览图
     */
    fun loadDiagram(diagram: Diagram) {
        if (currentDiagramId == diagram.id) return
        currentDiagramId = diagram.id
        
        val xml = (diagram.metadata["drawioXml"] as? String)
            ?: """<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel>"""
        
        if (drawioReady) {
            sendXmlToDrawio(xml)
        } else {
            pendingXml = xml
        }
    }
    
    /**
     * 刷新当前导览图
     */
    fun refresh(diagram: Diagram) {
        currentDiagramId = null // 强制重新加载
        loadDiagram(diagram)
    }
    
    /**
     * 清空显示
     */
    fun clear() {
        currentDiagramId = null
        if (drawioReady) {
            sendXmlToDrawio("""<mxGraphModel><root><mxCell id="0"/><mxCell id="1" parent="0"/></root></mxGraphModel>""")
        }
    }
    
    /**
     * 释放资源
     */
    fun dispose() {
        try {
            browser.dispose()
        } catch (e: Exception) {
            logger.warn("Error disposing embedded viewer", e)
        }
    }
    
    private fun setupPopupHandler() {
        browser.jbCefClient.addLifeSpanHandler(object : CefLifeSpanHandlerAdapter() {
            override fun onBeforePopup(
                browser: CefBrowser?,
                frame: CefFrame?,
                targetUrl: String?,
                targetFrameName: String?
            ): Boolean {
                if (targetUrl?.startsWith("bookmark://") == true) {
                    val bookmarkId = targetUrl.removePrefix("bookmark://")
                        .let { if (it.contains("/")) it.substringAfterLast("/") else it }
                    ApplicationManager.getApplication().invokeLater {
                        bookmarkService.getBookmark(bookmarkId)?.let {
                            bookmarkService.navigateToBookmark(it)
                        }
                    }
                }
                return true
            }
        }, browser.cefBrowser)
    }
    
    private fun sendXmlToDrawio(xml: String) {
        val escaped = escapeJS(xml)
        browser.cefBrowser.executeJavaScript(
            "window.loadDiagramXml && window.loadDiagramXml('$escaped');",
            browser.cefBrowser.url, 0
        )
    }
    
    private fun loadViewerPage() {
        val drawioLang = if (Messages.isEnglish()) "en" else "zh"
        val loadingText = "⏳ Loading Draw.io... | 加载中..."
        // 使用 embed 只读查看模式
        val drawioUrl = "https://embed.diagrams.net/?embed=1&proto=json&spin=1&nav=1&lang=$drawioLang&chrome=0&edit=_blank"
        
        // 添加 JS 查询处理
        val jsQuery = JBCefJSQuery.create(browser)
        jsQuery.addHandler { request ->
            when {
                request == "ready" -> {
                    drawioReady = true
                    logger.info("Draw.io is ready!")
                    onReadyCallback?.invoke(true)
                    pendingXml?.let {
                        sendXmlToDrawio(it)
                        pendingXml = null
                    }
                }
                request.startsWith("bookmark://") -> {
                    // 处理书签链接点击
                    val bookmarkId = request.removePrefix("bookmark://")
                        .let { if (it.contains("/")) it.substringAfterLast("/") else it }
                    logger.info("Navigating to bookmark: $bookmarkId")
                    ApplicationManager.getApplication().invokeLater {
                        bookmarkService.getBookmark(bookmarkId)?.let {
                            bookmarkService.navigateToBookmark(it)
                        }
                    }
                }
            }
            null
        }
        
        val readyCallback = jsQuery.inject("'ready'")
        val linkCallback = jsQuery.inject("url")
        
        val html = """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body { margin: 0; padding: 0; overflow: hidden; background: #fafafa; }
        #drawio-frame { width: 100%; height: 100vh; border: none; }
        #loading { 
            position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
            color: #666; font-size: 12px; font-family: system-ui;
        }
    </style>
</head>
<body>
    <div id="loading">$loadingText</div>
    <iframe id="drawio-frame" src="$drawioUrl"></iframe>
    
    <script>
        const iframe = document.getElementById('drawio-frame');
        const loading = document.getElementById('loading');
        let ready = false;
        let pendingXml = null;
        
        // 暴露给 Java 调用的函数
        window.loadDiagramXml = function(xml) {
            if (ready && iframe.contentWindow) {
                iframe.contentWindow.postMessage(JSON.stringify({
                    action: 'load',
                    xml: xml
                }), '*');
            } else {
                pendingXml = xml;
            }
        };
        
        window.addEventListener('message', function(evt) {
            if (!evt.data) return;
            try {
                const msg = typeof evt.data === 'string' ? JSON.parse(evt.data) : evt.data;
                
                // Draw.io 初始化完成
                if ((msg.event === 'init' || msg.event === 'load' || msg.event === 'configure') && !ready) {
                    ready = true;
                    loading.style.display = 'none';
                    $readyCallback
                    // 加载待处理的 XML
                    if (pendingXml) {
                        window.loadDiagramXml(pendingXml);
                        pendingXml = null;
                    }
                }
                
                // 处理链接点击（viewer 模式使用 click 事件）
                if (msg.event === 'openLink' || msg.event === 'click') {
                    const url = msg.link || msg.url || msg.href;
                    if (url && url.startsWith('bookmark://')) {
                        $linkCallback
                    }
                }
            } catch (e) { console.log('Parse error:', e); }
        });
    </script>
</body>
</html>
        """.trimIndent()
        
        browser.loadHTML(html)
    }
    
    private fun escapeJS(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
