// PDF14-MIGRATION: 从 pdf14 复制；仅包名及已标记宿主接口/资源适配，未优化转换算法。
package com.pdf.editor.ui.tools.document.common

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.util.Base64
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 离线 DOCX 导出器：复用本地 docx-preview 渲染，通过公开的 PdfDocument Canvas 绘制 PDF。
 * WebView 只持有 Application Context，在主线程创建和释放；不依赖预览页实例，
 * 因而页面重建不会打断 ViewModel 中的任务。复杂排版以浏览器渲染结果为准。
 * WebView 软件绘制可能栅格化文字；输出不保证具有可搜索、可选择的文本层。
 */
object WordPdfConverter {
    /** 读取文件在 IO 线程完成，设置体积上限避免 Base64 和浏览器同时占用过多内存。 */
    suspend fun convert(context: Context, source: File, destination: File) {
        val payload = withContext(Dispatchers.IO) {
            require(source.extension.equals("docx", true) && source.isFile && source.canRead())
            require(source.length() in 1..(30L * 1024 * 1024)) { "DOCX exceeds 30 MB" }
            Base64.encodeToString(source.readBytes(), Base64.NO_WRAP)
        }
        try {
            withContext(Dispatchers.Main) {
                withTimeout(120_000) { renderAndExport(context.applicationContext, payload, destination) }
            }
        } catch (error: Throwable) {
            destination.delete()
            // 超时属于转换失败，不能让 CoreViewModel 当作正常取消而把页面一直留在处理中。
            if (error is TimeoutCancellationException) throw IllegalStateException("DOCX export timed out", error)
            throw error
        }
    }

    /** 独立 WebView 禁止网络访问，仅允许加载导出页与本地渲染脚本，文档资源使用内嵌数据。 */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun renderAndExport(context: Context, payload: String, destination: File) {
        val webView = WebView(context)
        try {
            webView.setBackgroundColor(Color.WHITE)
            webView.isVerticalScrollBarEnabled = false
            webView.isHorizontalScrollBarEnabled = false
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = false
            val density = context.resources.displayMetrics.density
            layoutWebView(webView, ceil(794 * density).toInt(), ceil(1123 * density).toInt())
            webView.settings.javaScriptEnabled = true
            webView.settings.allowFileAccess = false
            webView.settings.allowContentAccess = false
            webView.settings.blockNetworkLoads = true
            webView.webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url.toString()
                    // 虚拟 HTTPS 同源地址直接映射 assets，不访问网络；避免 file:// 模块的跨域限制。
                    val asset = when (url) {
                        "https://docx.local/doc/export.html" -> "tool/doc/export.html" // PDF14-MIGRATION: 隔离源导出 assets，避免覆盖当前 DOCX 预览。
                        "https://docx.local/doc/assets/doc.js" -> "doc/assets/doc.js" // 与预览页共用同一份 doc.js（已含 renderDocxAsync 导出），避免 170 KB 重复。
                        else -> null
                    }
                    if (asset != null) return WebResourceResponse(
                        if (asset.endsWith(".js")) "application/javascript" else "text/html",
                        "UTF-8", context.assets.open(asset)
                    )
                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                }
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest) = true
            }
            webView.loadUrl("https://docx.local/doc/export.html")
            awaitStatus(webView, "ready")
            evaluate(webView, "window.exportDocx(${JSONObject.quote(payload)}); void 0;")
            awaitStatus(webView, "complete")
            // 屏幕绘制不应用 @media print；导出页提供去掉预览装饰后的纸张范围。
            val metrics = JSONObject(evaluate(webView, "window.exportMetrics()"))
            drawPdf(webView, metrics, destination, density)
            check(destination.length() > 0) { "Empty PDF output" }
        } finally {
            webView.stopLoading()
            webView.destroy()
        }
    }

    private fun layoutWebView(webView: WebView, width: Int, height: Int) {
        require(width > 0 && height > 0) { "Invalid DOCX page dimensions" }
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        webView.layout(0, 0, width, height)
        webView.scrollTo(0, 0)
    }

    /** 等待 Chromium 提交渲染结果，而不是用固定延迟猜测 draw() 是否已经可用。 */
    private suspend fun awaitVisualState(webView: WebView) = suspendCancellableCoroutine<Unit> { continuation ->
        webView.postVisualStateCallback(0, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        })
    }

    private suspend fun drawPdf(webView: WebView, metrics: JSONObject, destination: File, density: Float) {
        val pages = metrics.getJSONArray("pages")
        check(pages.length() > 0) { "No DOCX pages rendered" }
        // DOM 使用 CSS px，PDF 使用 point；屏幕密度由实际 viewport 宽度换算。
        val pointsPerCssPixel = 72.0 / 96.0
        val document = PdfDocument()
        try {
            for (index in 0 until pages.length()) {
                coroutineContext.ensureActive()
                val bounds = pages.getJSONObject(index)
                val width = bounds.getDouble("width")
                val height = bounds.getDouble("height")
                val contentHeight = bounds.getDouble("contentHeight")
                // 把当前页移到视口内绘制，不启用影响全应用的整篇文档慢速绘制模式。
                // WebView 软件绘制可能生成位图；每次只给一页大小，避免分配整篇长图。
                layoutWebView(webView, ceil(width * density).toInt(), ceil(height * density).toInt())
                val viewPixelsPerCssPixel = evaluate(webView, "window.exportShowPage($bounds)").toDouble()
                val canvasScale = (pointsPerCssPixel / viewPixelsPerCssPixel).toFloat()
                awaitVisualState(webView)
                val pageInfo = PdfDocument.PageInfo.Builder(
                    (width * pointsPerCssPixel).roundToInt().coerceAtLeast(1),
                    (height * pointsPerCssPixel).roundToInt().coerceAtLeast(1),
                    index + 1
                ).create()
                val page = document.startPage(pageInfo)
                try {
                    page.canvas.apply {
                        drawColor(Color.WHITE)
                        translate(0f, (bounds.getDouble("insetTop") * pointsPerCssPixel).toFloat())
                        // 最后一个切片只绘制本节内容，避免把下一节画到当前页的底部。
                        clipRect(0f, 0f, (width * pointsPerCssPixel).toFloat(),
                            (contentHeight * pointsPerCssPixel).toFloat())
                        scale(canvasScale, canvasScale)
                        webView.draw(this)
                    }
                } finally {
                    document.finishPage(page)
                }
                // 每页之间允许取消和超时生效，也让主线程处理界面消息。
                yield()
            }
            withContext(Dispatchers.IO) {
                destination.parentFile?.mkdirs()
                destination.outputStream().use { document.writeTo(it) }
            }
        } finally {
            document.close()
        }
    }

    /** 等待明确的渲染状态，不使用 onPageFinished 或固定延迟猜测 DOCX 是否完成。 */
    private suspend fun awaitStatus(webView: WebView, expected: String) {
        while (true) {
            val status = evaluate(webView, "window.exportStatus")
            check(status != "\"failed\"") { "DOCX rendering failed" }
            if (status == "\"$expected\"") return
            delay(100)
        }
    }

    /** 将主线程 JavaScript 回调转为可取消挂起函数；任务取消后忽略迟到结果。 */
    private suspend fun evaluate(webView: WebView, script: String): String = suspendCancellableCoroutine { continuation ->
        webView.evaluateJavascript(script) { value -> if (continuation.isActive) continuation.resume(value) }
    }
}
