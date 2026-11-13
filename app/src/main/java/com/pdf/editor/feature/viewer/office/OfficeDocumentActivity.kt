package com.pdf.editor.feature.viewer.office

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.DataUtils
import com.pdf.editor.common.tools.FileManager
import com.pdf.editor.common.tools.ScanConfig
import com.pdf.editor.data.FilesData
import com.pdf.editor.databinding.ActivityFun2Binding
import org.json.JSONObject

import java.io.File

/** 原类：`com.pdf.read.ui.detail.FunActivity2`。 */
class OfficeDocumentActivity : BaseActivity<ActivityFun2Binding>() {


    companion object {
        private const val EXTRA_DATA = "data"
        private const val EXTRA_FROM_TYPE = "from_type"

        /**
         * 启动非 PDF 文件（DOC/PPT/Excel/TXT）的 WebView 预览页，并将 [FilesData] 序列化后传入，
         * 用来在 [OfficeDocumentActivity] 内恢复文件信息。PDF 统一由 ReaderSurfaceActivity 打开。
         */
        fun startFileActivity(activity: Activity, data: FilesData, fromType: Int = 0) {
            activity.startActivity(
                Intent(activity, OfficeDocumentActivity::class.java).apply {
                    putExtra(EXTRA_DATA, DataUtils.toJson(data))
                    putExtra(EXTRA_FROM_TYPE, fromType)
                }
            )
        }
    }

    override fun getBinding(inflater: LayoutInflater): ActivityFun2Binding {
        return ActivityFun2Binding.inflate(layoutInflater)
    }

    override fun setListener() {
        aBinding.ivBack.setOneClick {
            back()
        }
        aBinding.ivShare.setOneClick {
            filesData?.let(FileManager::share)
        }
    }

    override fun onResume() {
        super.onResume()
        setStatusBarTextColor(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showStatusBarText(false)
    }

    private fun back() {
        EventManager.posePoint(PointConfig.other_page_back)
        ADShowManager(this@OfficeDocumentActivity,"FunActivity2").showScreenAD(PointConfig.AD_POINT_READ_BACK, ADEnum.INT_AD_EXIT){type, hasValue ->
            finish()
        }
    }

    var filesData: FilesData? = null

    override fun setShowView() {
        var data = intent.getStringExtra("data")
        filesData = DataUtils.parseBeanByGson(data, FilesData::class.java)
        if (filesData == null){
            finish()
        }
        EventManager.posePoint(PointConfig.other_page)
        aBinding.apply {
            tvTitle.text = filesData?.fileName ?: ""
            initWebView(web)
            loadWeb()
            if (filesData!=null){
                val file = File(filesData!!.filePath)
                loadDocx(file)
            }
        }
    }

    fun loadWeb() {
        aBinding.web.apply {
            with(settings) {
                javaScriptEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                domStorageEnabled = true
            }
            if (filesData!=null){
                if (filesData!!.fileType == ScanConfig.FILE_TYPE_DOC) {
                    loadUrl("file:///android_asset/doc/index.html")
                } else if (filesData!!.fileType == ScanConfig.FILE_TYPE_PPT) {
                    loadUrl("file:///android_asset/ppt/index.html")
                }else if (filesData!!.fileType == ScanConfig.FILE_TYPE_EXCEL){
                    loadUrl("file:///android_asset/excel_viewer.html")
                }else if (filesData!!.fileType == ScanConfig.FILE_TYPE_TEXT){
                    loadUrl("file:///android_asset/txt/index.html")
                }
            }
        }
    }

    fun initWebView(webView: WebView) {
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
        if (filesData!=null){
            settings.loadWithOverviewMode = filesData!!.fileType!= ScanConfig.FILE_TYPE_TEXT
        }

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


    private fun loadDocx(file: File) {
        // 读取 docx → base64
        val bytes = file.readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        aBinding.web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
//                if (filesData!!.fileType == ScanConfig.FILE_TYPE_DOC) {
//                    aBinding.ppt.evaluateJavascript("renderDocx('$base64')", null)
//                }else{
//                    val jsSafeBase64 = JSONObject.quote(base64) // 自动加双引号并转义特殊字符
//                    aBinding.ppt.evaluateJavascript("renderExcel($jsSafeBase64);", null)
//                }
//                val ext = file.extension
//                aBinding.ppt.evaluateJavascript("window.showFile('$base64', '$ext');", null)
                if (filesData!=null){
                    showFileBase64(aBinding.web, File(filesData!!.filePath))
                }
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

    fun showFileBase64(webView: WebView, file: File) {
        val bytes = file.readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val safe = JSONObject.quote(base64) // 自动加引号、转义
        val ext = file.extension.lowercase()
        if (filesData!=null){
            webView.post {
                var jsCommand = ""
                if (filesData!!.fileType == ScanConfig.FILE_TYPE_DOC) {
                    jsCommand = "javascript:renderDOCX('" + base64 + "')"
                } else if (filesData!!.fileType == ScanConfig.FILE_TYPE_PPT) {
                    // c. 调用 JS 函数
                    // window.renderPPT 是我们在 main.js 中定义的全局函数
                    jsCommand = "javascript:renderPPT('" + base64 + "')"
                }else if (filesData!!.fileType == ScanConfig.FILE_TYPE_TEXT){
                    val text = file.readText() // 直接读取为文本（注意编码，默认 UTF-8）
                    val safe = JSONObject.quote(text) // 自动转义并加引号
                    jsCommand = "javascript:showTxt($safe)"
                } else{
                    jsCommand = "javascript:renderExcel($safe)"
                }
                // 必须在主线程执行 JS 调用
                webView.evaluateJavascript(jsCommand, null)
            }
        }
    }

    override fun onBackPressed() {
        aBinding.ivBack.performClick()
    }

}
