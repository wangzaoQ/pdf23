package com.pdf.editor.common.core

import android.annotation.SuppressLint
import android.graphics.Color
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.viewbinding.ViewBinding
import com.blankj.utilcode.util.KeyboardUtils
import com.pdf.editor.MyAPP
import com.pdf.editor.R
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.ui.PdfMainActivity
import com.pdf.editor.ui.start.PdfStartActivity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


abstract class BaseActivity<V : ViewBinding> : AppCompatActivity() {
    lateinit var aBinding: V
    var newsScope = MainScope()
    private var status = false

    private var jumpActivityResultCallback: ((ActivityResult) -> Unit)? = null
    private val jumpActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val callback = jumpActivityResultCallback
        jumpActivityResultCallback = null
        callback?.invoke(activityResult)
    }

    var aTAG = javaClass.simpleName

    @Volatile
    var stayTime = 0L
    var saveStayTime = false

    var life = BaseActivityLife()

    override fun onStart() {
        super.onStart()
        status = false
    }

    override fun onResume() {
        super.onResume()
        status = true
        ADControl.goBackType = ""
    }

    abstract fun getBinding(inflater: LayoutInflater): V

    override fun onPause() {
        status = false
        super.onPause()
    }

    override fun onStop() {
        status = false
        super.onStop()
    }

    fun getActivityStatus(): Boolean {
        return status && (Lifecycle.State.RESUMED == lifecycle.currentState)
    }

    fun launchActivityForResult(
        intent: Intent,
        onResult: (ActivityResult) -> Unit
    ) {
        check(jumpActivityResultCallback == null) {
            "Another activity result request is already in progress"
        }
        jumpActivityResultCallback = onResult
        try {
            jumpActivityResultLauncher.launch(intent)
        } catch (throwable: Throwable) {
            jumpActivityResultCallback = null
            throw throwable
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(life)
        var isVertical = isVertical()
//        if (isVertical) {
//            val displayMetrics = this.resources.displayMetrics
//            (displayMetrics.heightPixels / 779f).let {
//                displayMetrics.density = it
//                displayMetrics.scaledDensity = it
//                displayMetrics.densityDpi = (160 * it).toInt()
//            }
//        }

        aBinding = getBinding(layoutInflater)
        setContentView(aBinding.root)
        setShowView()
        setListener()
        setDataListener()
        if (this is PdfMainActivity){
            showSystemBars(this.window)
        }else{
            hideSystemBars(this.window)
        }
        setStatusBarWithInsets(true, aBinding.root)
        showStatusBarText(true)
    }

    abstract fun setListener()
    open fun setDataListener() {}
    abstract fun setShowView()

    open fun isVertical(): Boolean {
        return true
    }

    fun setStatusBarWithInsets(lightIcons: Boolean, rootView: View) {
        val window = window ?: return

        // 1️⃣ 允许刘海区域绘制
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 2️⃣ 设置 decorFitsSystemWindows = false，让我们可以用 WindowInsets 控制布局
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 3️⃣ 状态栏透明
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
        }

        // 4️⃣ 控制状态栏图标颜色
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = lightIcons


        ViewCompat.setOnApplyWindowInsetsListener(aBinding.root) { v, insets ->
            // insets 是 WindowInsetsCompat 类型
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // 例如给 view 设置 padding
            v.updatePadding(
                left = systemBars.left,
                top = 0,
                right = systemBars.right,
                bottom = systemBars.bottom
            )

            insets // 注意这里返回 WindowInsetsCompat
        }

        // 6️⃣ 请求一次 Insets
        rootView.requestApplyInsets()
    }

    fun setStatusBarTextColor(isDarkText: Boolean, ensureStatusBarVisible: Boolean = false) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = isDarkText
        if (ensureStatusBarVisible) {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }

    fun showStatusBarText(isDarkText: Boolean) {
        setStatusBarTextColor(isDarkText = isDarkText, ensureStatusBarVisible = true)
    }



    @SuppressLint("MissingSuperCall")
    override fun onSaveInstanceState(outState: Bundle) {

    }

    var needAnimal = true

    override fun finish() {
        super.finish()
        if (needAnimal) {
            // 设置结束动画
            overridePendingTransition(R.anim.in_alpha, R.anim.out_alpha)
        }
    }


    override fun onDestroy() {
        jumpActivityResultCallback = null
        newsScope.cancel()
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.getAction() == MotionEvent.ACTION_UP) {
            var v = currentFocus
            if (allowHideKeyboard(v, ev)) {
//                hideKeyBoard(v)
                v?.apply {
                    KeyboardUtils.hideSoftInput(this)
                    clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    fun hideKeyBoard(view: View?) {
        if (view == null) return
        runCatching {
            var imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    /**
     * 根据EditText所在坐标和用户点击的坐标相对比，来判断是否隐藏键盘，因为当用户点击EditText时则不能隐藏
     *
     * @param v
     * @param event
     * @return
     */
    private fun allowHideKeyboard(v: View?, event: MotionEvent): Boolean {
        if (v != null && v is EditText) {
            var l = intArrayOf(0, 0)
            v.getLocationInWindow(l)
            var left = l[0]
            var top = l[1]
            var bottom = top + v.getHeight()
            var right = left + v.getWidth()
            return !(event.x > left && event.x < right
                    && event.y > top && event.y < bottom)
        }
        // 如果焦点不是EditText则忽略，这个发生在视图刚绘制完，第一个焦点不在EditText上，和用户用轨迹球选择其他的焦点
        return false;
    }


    fun View.setOneClick(action: suspend (View) -> Unit) {
        // 启动一个 actor
        val eventActor = MainScope().actor<View>(Dispatchers.Main) { // 执行时不接收新来的消息
//        val eventActor = GlobalScope.actor<View>(Dispatchers.Main, capacity = Channel.CONFLATED) { // 总是执行最新的消息,会停掉之前的操作
            for (event in channel) {
                action(event) // 将事件传递给 action
            }
        }
        // 设置一个监听器来启用 actor
        setOnClickListener {
            eventActor.trySend(it).isSuccess
        }
    }

    fun addLaunch(
        success: suspend CoroutineScope.() -> Unit,
        failBack: suspend CoroutineScope.(errorContent: String) -> Unit?,
        dispatcher: CoroutineDispatcher? = Dispatchers.IO
    ): Job {
        return newsScope.launch(dispatcher ?: Dispatchers.IO) {
            runCatching {
                success()
            }.onFailure {
                withContext(Dispatchers.Main) {
                    failBack(it.stackTraceToString())
                    if (MyAPP.isDebug) {
                        AppLogs.dLog(aTAG, "${aTAG} error:${it.stackTraceToString()}")
                    }
                }
            }
        }
    }


    fun hideSystemBars(window: Window) {
        // 让内容延伸到系统栏
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars()) // 隐藏状态栏 + 导航栏
        // 用户可通过从屏幕边缘滑动临时显示系统栏
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun showSystemBars(window: Window) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, true)
    }

//    var loadingPop:LoadingPop?=null
//
//    fun showPop(){
//        var isShowing = loadingPop?.isShowing?:false
//        if (isShowing.not()){
//            loadingPop = LoadingPop(this)
//            loadingPop!!.createPop()
//        }
//    }
//
//    fun hidePop(){
//        loadingPop?.dismiss()
//    }


}
