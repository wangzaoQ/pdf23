// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.file

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.pdf.editor.R
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.config.PointConfig as EventConfig
import com.pdf.editor.ui.detail.reader.setSequentialClickListener
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.ui.tools.document.file.FileCategory
import com.pdf.editor.databinding.ToolActivityFile2Binding
import org.json.JSONObject
import java.io.File
import com.pdf.editor.ui.tools.document.common.DocumentProcessActivity
import com.pdf.editor.ui.tools.document.common.DocumentTaskRequest
import com.pdf.editor.ui.tools.document.common.FileRenamePop

/** 在内嵌查看器中展示支持的办公文档和文本文件。 */
class ContentSurfaceActivity : BaseActivity<ToolActivityFile2Binding>() {
    // PDF14-MIGRATION: 当前 BaseActivity 的绑定字段；源页面绑定调用保持不变。
    private val binding: ToolActivityFile2Binding get() = aBinding


    private lateinit var previewFile: File
    private lateinit var fileCategory: FileCategory

    override fun getBinding(inflater: LayoutInflater): ToolActivityFile2Binding {
        return ToolActivityFile2Binding.inflate(inflater)
    }

    var fromType= ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showStatusBarText(false)
    }

    override fun setShowView() {
        fromType = intent.getStringExtra("fromType")?:""
        if (fromType.isEmpty()){
            binding.btnSave.visibility = View.GONE
        }else{
            binding.btnSave.visibility = View.VISIBLE
            if (fromType == FileSelectAction.WORD_TO_PDF.name){
                binding.btnSave.text = getString(R.string.app_convert_to_pdf)
            }
        }
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val file = filePath?.let(::File)
        val category = file?.let { FileCategory.fromFileName(it.name) }

        if (
            file == null ||
            !file.isFile ||
            !file.canRead() ||
            category == null ||
            category !in PREVIEW_CATEGORIES
        ) {
            Toast.makeText(this, R.string.app_other_file_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        previewFile = file
        fileCategory = category
        EventManager.posePoint(EventConfig.other_page)
        binding.previewTitle.text = intent.getStringExtra(EXTRA_FILE_NAME)
            ?.takeIf(String::isNotBlank)
            ?: file.name

        configureSurface(binding.webView)
        loadViewerShell()
        bindPayloadChannel(file)
    }

    override fun setListener() {
        // Word 工具复用当前预览页确认文件和命名，真正转换交给公共任务页，避免持有预览视图。
        binding.btnSave.setSequentialClickListener {
            if (fromType != FileSelectAction.WORD_TO_PDF.name || !::previewFile.isInitialized) return@setSequentialClickListener
            if (!previewFile.extension.equals("docx", true)) {
                Toast.makeText(this, R.string.app_docx_only, Toast.LENGTH_SHORT).show()
                return@setSequentialClickListener
            }
            FileRenamePop(this).createPop(previewFile.nameWithoutExtension, R.string.app_word_to_pdf, R.string.app_convert_to_pdf) { name ->
                startActivity(DocumentProcessActivity.intent(this,
                    DocumentTaskRequest.WordToPdf(previewFile.absolutePath, name)))
            }
        }
        binding.ivBack.setSequentialClickListener {
            EventManager.posePoint(EventConfig.other_page_back)
            ADShowManager(this@ContentSurfaceActivity, "ContentSurfaceActivity").showScreenAD(EventConfig.AD_POINT_READ_BACK, ADEnum.INT_AD_EXIT){ type, hasValue ->
                finish()
            }
        }
    }

    /** 根据当前文档类型加载对应的查看器页面。 */
    private fun loadViewerShell() {
        binding.webView.apply {
            with(settings) {
                javaScriptEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                domStorageEnabled = true
            }
            when (fileCategory) {
                FileCategory.WORD -> loadUrl("file:///android_asset/doc/index.html")
                FileCategory.PPT -> loadUrl("file:///android_asset/ppt/index.html")
                FileCategory.EXCEL -> loadUrl("file:///android_asset/excel_viewer.html")
                FileCategory.TEXT -> loadUrl("file:///android_asset/txt/index.html")
                else -> Unit
            }
        }
    }

    /** 配置查看本地文档所需的 WebView 能力。 */
    private fun configureSurface(webView: WebView) {
        // 可选：根据 BuildConfig 或 debug 控制是否开启远程调试

        val settings = webView.settings

        // 基础功能
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true            // localStorage / sessionStorage
        settings.databaseEnabled = true
        settings.loadsImagesAutomatically = true
        settings.blockNetworkImage = false

//        // 视口与缩放
        settings.useWideViewPort = true
//        settings.loadWithOverviewMode = false
//        settings.builtInZoomControls = true
//        settings.displayZoomControls = false         // 隐藏默认缩放按钮（UI 可自定义）
//        settings.textZoom = 100                      // 100% 原始大小

        // 3. 启用缩放功能 (关键)
        settings.setSupportZoom(true);       // 允许支持缩放手势
        settings.setBuiltInZoomControls(true); // 启用内置的缩放控件（例如双击缩放，或缩放按钮）
        settings.setDisplayZoomControls(false); // 通常设置为 false，隐藏屏幕上的放大/缩小按钮

// 4. 优化显示
// 允许加载文档的原始大小，不强制适应屏幕宽度
        settings.loadWithOverviewMode = fileCategory != FileCategory.TEXT

        // 文件访问（本地 assets 需要）
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // file:// 页面跨域（慎用，仅当必须从 file:// 调用远端/其他 file://）
        settings.allowFileAccessFromFileURLs = true
        settings.allowUniversalAccessFromFileURLs = true // 默认为 false，必要时再打开

        // Mixed content（Android 5.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        // 性能/缓存/UA
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.userAgentString = settings.userAgentString + " MyApp/1.0"

        // 媒体自动播放（API ？可能需要 user gesture，视业务）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.mediaPlaybackRequiresUserGesture = false
        }

        // 安全相关
        settings.savePassword = false
        settings.saveFormData = false
    }

    /** 建立内容传输桥接，在查看器加载完成后发送文档数据。 */
    private fun bindPayloadChannel(file: File) {
        // 读取 docx → base64
        val bytes = file.readBytes()
        Base64.encodeToString(bytes, Base64.NO_WRAP)
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pushPayload(binding.webView, previewFile)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                request?.url?.toString()?.let { url ->
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        view?.context?.startActivity(intent)
                        return true // 已处理，不在 WebView 内加载
                    }
                }
                return false // 其他情况由 WebView 处理
            }
        }
    }

    /** 编码选中的文档，并发送给对应的 JavaScript 渲染器。 */
    private fun pushPayload(webView: WebView, file: File) {
        val bytes = file.readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val safe = JSONObject.quote(base64) // 自动加引号、转义
        val ext = file.extension.lowercase()
        webView.post {
            val jsCommand = when (fileCategory) {
                FileCategory.WORD -> "javascript:renderDOCX('$base64')"
                FileCategory.PPT -> "javascript:renderPPT('$base64')"
                FileCategory.TEXT -> {
                    val text = file.readText() // 直接读取为文本（注意编码，默认 UTF-8）
                    val safeText = JSONObject.quote(text) // 自动转义并加引号
                    "javascript:showTxt($safeText)"
                }
                else -> "javascript:renderExcel($safe)"
            }
            // 必须在主线程执行 JS 调用
            webView.evaluateJavascript(jsCommand, null)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        binding.ivBack.performClick()
    }

    companion object {
        private const val EXTRA_FILE_NAME = "preview_file_name"
        private const val EXTRA_FILE_PATH = "preview_file_path"
        private val PREVIEW_CATEGORIES = setOf(
            FileCategory.WORD,
            FileCategory.EXCEL,
            FileCategory.PPT,
            FileCategory.TEXT
        )

        /** 打开可读取本地文件的文档查看页面。 */
        fun open(context: Context, fileName: String, absolutePath: String,type: String?="") {
            context.startActivity(
                Intent(context, ContentSurfaceActivity::class.java).apply {
                    putExtra(EXTRA_FILE_NAME, fileName)
                    putExtra(EXTRA_FILE_PATH, absolutePath)
                    putExtra("fromType", type)
                }
            )
        }
    }
}
