package com.pdf.editor.feature.browser

import android.os.Build
import android.view.LayoutInflater
import android.webkit.WebSettings
import android.webkit.WebView
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.databinding.ActivityWebBinding


/** 原类：`com.pdf.read.ui.WebActivity`。 */
class InAppBrowserActivity : BaseActivity<ActivityWebBinding>(){
    override fun getBinding(inflater: LayoutInflater): ActivityWebBinding {
        return ActivityWebBinding.inflate(layoutInflater)
    }

    override fun setListener() {
        aBinding.ivBack.setOneClick {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        setStatusBarTextColor(false)
    }

    var title = ""
    var webUrl = ""
    override fun setShowView() {
        title = intent.getStringExtra("title")?:""
        webUrl = intent.getStringExtra("url")?:""
        initWebView(aBinding.web)
        aBinding.web.loadUrl(webUrl)
        aBinding.tvTitle.text = title
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
        settings.loadWithOverviewMode = false
        settings.builtInZoomControls = true
        settings.displayZoomControls = false         // 隐藏默认缩放按钮（UI 可自定义）
        settings.textZoom = 100                      // 100% 原始大小

        // Mixed content（Android 5.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }

        // 性能/缓存/UA
        settings.cacheMode = WebSettings.LOAD_DEFAULT
    }

}
