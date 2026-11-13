package com.pdf.editor.common.nf.floatwindow

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.DisplayMetrics
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatImageView
import com.pdf.editor.MyAPP
import com.pdf.editor.R
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.nf.NFControl
import com.pdf.editor.common.nf.NFEnum
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.FileManager
import com.pdf.editor.common.tools.PermissionManager
import com.pdf.editor.common.tools.setOneClick
import com.pdf.editor.data.FilesData
import com.pdf.editor.databinding.PopSystemR1Binding
import com.pdf.editor.databinding.PopSystemR2Binding
import com.pdf.editor.ui.start.PdfStartActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

@SuppressLint("StaticFieldLeak")
object FloatDesktopWindow {
    private const val TAG = "FloatDesktopWindow"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var appContext: Context? = null
    private var windowManager: WindowManager? = null
    private var aiScreenView: View? = null
    private var aiScreenParams: Any? = null
    private var aiScreenAdded = false
    private var aiScreenHiding = false

    private fun hasOverlayPermission(context: Context): Boolean {
        return PermissionManager.allowSystemAlert(context)
    }

    private fun shouldSkipAiScreenOverlay(): Boolean {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.VANILLA_ICE_CREAM) return false
        return Build.BRAND.contains("tecno", ignoreCase = true) ||
                Build.MANUFACTURER.contains("tecno", ignoreCase = true)
    }


    private fun getWindowManagerByReflection(context: Context): Any? {
        return runCatching {
            val method = Context::class.java.getMethod(
                "getSystemService",
                String::class.java
            )
            method.invoke(
                context,
                "window"
            )
        }.getOrNull() ?: windowManager
    }

    private fun createAiScreenLayoutParamsByReflection(
        width: Int,
        height: Int,
        gravity: Int,
        yOffset: Int
    ): Any? {
        return runCatching {
            val layoutParamsClass = Class.forName(
                "android.view.WindowManager\$LayoutParams"
            )
            val constructor = layoutParamsClass.getConstructor(
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 2038 else 2002
            constructor.newInstance(width, height, type, 264, -3).apply {
                layoutParamsClass.getField(
                    "gravity"
                ).setInt(this, gravity)
                layoutParamsClass.getField("y").setInt(this, yOffset)
            }
        }.getOrNull()
    }

    private fun addAiOverlayViewByReflection(windowManager: Any?, view: View?, layoutParams: Any?) {
        if (windowManager == null || view == null || layoutParams == null) return
        val layoutParamsClass = Class.forName(
            "android.view.ViewGroup\$LayoutParams"
        )
        val windowManagerClass = Class.forName(
            "android.view.WindowManager"
        )
        val addViewMethod = windowManagerClass.getMethod(
            "addView",
            View::class.java,
            layoutParamsClass
        )
        addViewMethod.invoke(windowManager, view, layoutParams)
    }

    private fun updateAiOverlayViewByReflection(windowManager: Any?, view: View?, layoutParams: Any?) {
        if (windowManager == null || view == null || layoutParams == null) return
        val layoutParamsClass = Class.forName(
            "android.view.ViewGroup\$LayoutParams"
        )
        val windowManagerClass = Class.forName(
            "android.view.WindowManager"
        )
        val updateViewLayoutMethod = windowManagerClass.getMethod(
            "updateViewLayout",
            View::class.java,
            layoutParamsClass
        )
        updateViewLayoutMethod.invoke(windowManager, view, layoutParams)
    }

    private fun removeAiOverlayViewImmediateByReflection(windowManager: Any?, view: View?) {
        if (windowManager == null || view == null) return
        val windowManagerClass = Class.forName(
            "android.view.WindowManager"
        )
        val removeViewImmediateMethod = windowManagerClass.getMethod(
            "removeViewImmediate",
            View::class.java
        )
        removeViewImmediateMethod.invoke(windowManager, view)
    }

    fun showAiScreenOverlay(context: Context,tag: String) {
        if (shouldSkipAiScreenOverlay()) {
            AppLogs.dLog(
                TAG,
                "skip AiScreen for Tecno Android 15, brand:${Build.BRAND}, manufacturer:${Build.MANUFACTURER}"
            )
            return
        }
        if (!LocalDataManager.isBUser)return
        val appCtx = context.applicationContext
        MyAPP.instance.applicationScope.launch (Dispatchers.Main){
            showAiScreenOverlayInternal(appCtx, tag)
        }
    }

    fun hideAiScreenOverlay() {
        runOnMain {
            hideAiScreenOverlayInternal()
        }
    }

    private fun showAiScreenOverlayInternal(context: Context,tag: String) {
        appContext = context
        if (aiScreenAdded || aiScreenView?.parent != null) {
            aiScreenAdded = true
            AppLogs.dLog(TAG,"AiScreen 已经添加了 from:${tag}")
            refreshAiScreenOverlay("already_added")

            return
        }
        if (!hasOverlayPermission(context)){
            return
        }
        val wm = if (windowManager == null) {
            getWindowManagerByReflection(context)?.also {
                windowManager = it as? WindowManager
            }
        } else {
            windowManager
        } ?: return
        aiScreenView = createAiScreenView(context)
        val panel = aiScreenView ?: return

        panel.animate().cancel()
        panel.visibility = View.VISIBLE
        panel.alpha = 1f
        panel.scaleX = 1f
        panel.scaleY = 1f
        panel.translationY = 0f

        val isR3Panel = panel.id == R.id.r3Root
        val screenWidth = getScreenSize().first
        val horizontalMargin = dp(context, 9) * 2
        aiScreenParams = createAiScreenLayoutParamsByReflection(
            width = if (isR3Panel) screenWidth else (screenWidth - horizontalMargin).coerceAtLeast(0),
            height = if (isR3Panel) dp(context, 96) else -2,
            gravity = if (isR3Panel) Gravity.TOP or Gravity.CENTER_HORIZONTAL else Gravity.CENTER,
            yOffset = if (isR3Panel) getStatusBarHeight(context) + dp(context, 9) else 0
        )
        val params = aiScreenParams ?: return

        runCatching {
            addAiOverlayViewByReflection(wm, panel, params)
            aiScreenAdded = true
            aiScreenHiding = false
            refreshAiScreenOverlay("add_view")
        }.onFailure {
            AppLogs.eLog(TAG, "showAiScreenOverlay addView error:${it.stackTraceToString()}")
            runCatching { aiScreenView?.let { view -> removeAiOverlayViewImmediateByReflection(wm, view) } }
            aiScreenAdded = false
            aiScreenHiding = false
        }
        EventManager.posePoint(PointConfig.pop_up_show)
        LocalDataManager.lastMediaPopTime = System.currentTimeMillis()
    }

    private fun refreshAiScreenOverlay(from: String) {
        val panel = aiScreenView ?: return
        val params = aiScreenParams ?: return
        if (panel.parent == null) return
        AppLogs.dLog(TAG, "refreshAiScreenOverlay from:$from")
        panel.visibility = View.VISIBLE
        panel.requestLayout()
        panel.invalidate()
        panel.postInvalidateOnAnimation()
        val wm = windowManager ?: return
        runCatching {
            updateAiOverlayViewByReflection(wm, panel, params)
        }.onFailure {
            AppLogs.eLog(TAG, "refreshAiScreenOverlay update error:${it.stackTraceToString()}")
        }
        panel.postDelayed({
            if (!aiScreenAdded || aiScreenHiding || panel.parent == null) return@postDelayed
            panel.requestLayout()
            panel.invalidate()
            panel.postInvalidateOnAnimation()
            runCatching {
                updateAiOverlayViewByReflection(wm, panel, params)
            }
        }, 120L)
    }

    private fun hideAiScreenOverlayInternal() {
        if (!aiScreenAdded || aiScreenHiding) return
        aiScreenHiding = true
        val wm = windowManager ?: return
        runCatching {
            aiScreenView?.let {
                it.animate().cancel()
                it.visibility = View.INVISIBLE
                removeAiOverlayViewImmediateByReflection(wm, it)
            }
        }
        aiScreenAdded = false
        aiScreenHiding = false
    }

    private fun createAiScreenView(context: Context): View {
        val themedContext = ContextThemeWrapper(context, R.style.Theme_Pdf)
        return when (Random.nextInt(3)) {
            0 -> {
                EventManager.posePoint(PointConfig.pop_up_show, Bundle().apply {
                    putString("state", "big")
                })
                createAiScreenR1View(context, themedContext)
            }
            1 -> {
                EventManager.posePoint(PointConfig.pop_up_show, Bundle().apply {
                    putString("state", "small")
                })
                createAiScreenR2View(context, themedContext)
            }
            else -> {
                EventManager.posePoint(PointConfig.pop_up_show, Bundle().apply {
                    putString("state", "top")
                })
                createAiScreenR3View(context, themedContext)
            }
        }
    }

    private fun createAiScreenR1View(context: Context, themedContext: Context): View {
        val binding = PopSystemR1Binding.inflate(LayoutInflater.from(themedContext))
        val pdfData = FileManager.resolveRecentPdfData(context)
        binding.ivClose.setOneClick {
            EventManager.posePoint(PointConfig.float_click,Bundle().apply {
                putString("state", "big")
                putString("click_type", "close")
            })
            stopCommitBreathAnimation(binding)
            stopCommitShineAnimation(binding)
            if (allowJump()) {
                openStartActivity(context, 4)
            }
            hideAiScreenOverlay()
        }
        binding.commit.setOneClick {
            EventManager.posePoint(PointConfig.float_click,Bundle().apply {
                putString("state", "big")
                putString("click_type", "open")
            })
            stopCommitBreathAnimation(binding)
            stopCommitShineAnimation(binding)
            openStartActivity(context, 4)
            hideAiScreenOverlay()
        }
        binding.rlRoot.setOnClickListener {
            binding.commit.performClick()
        }
        binding.ivPreview.post {
            val previewWidth = binding.ivPreview.width.takeIf { it > 0 } ?: dp(
                context,
                274
            )
            val previewHeight = binding.ivPreview.height.takeIf { it > 0 } ?: dp(
                context,
                102
            )
            renderPdfFirstPageInto(binding.ivPreview, pdfData?.filePath, previewWidth, previewHeight)
        }
        startCommitBreathAnimation(binding)
        startCommitShineAnimation(binding)
        return binding.root
    }

    private fun renderPdfFirstPageInto(imageView: AppCompatImageView, filePath: String?, width: Int, height: Int) {
        if (filePath.isNullOrBlank()) return
        val targetFile = File(filePath)
        if (!targetFile.exists()) return
        MyAPP.instance.applicationScope.launch(Dispatchers.IO) {
            val bitmap = renderPdfFirstPage(targetFile, width, height)
            withContext(Dispatchers.Main){
                if (bitmap != null && imageView.isAttachedToWindow) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        }
    }


    private fun renderPdfFirstPage(file: File, width: Int, height: Int): Bitmap? {
        return runCatching {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor.use {
                val renderer = PdfRenderer(descriptor)
                renderer.use {
                    if (renderer.pageCount <= 0) return@runCatching null
                    val page = renderer.openPage(0)
                    page.use {
                        val targetWidth = width.coerceAtLeast(1)
                        val targetHeight = height.coerceAtLeast(1)
                        val pageWidth = page.width.coerceAtLeast(1)
                        val pageHeight = page.height.coerceAtLeast(1)
                        val scale = max(
                            targetWidth.toFloat() / pageWidth,
                            targetHeight.toFloat() / pageHeight
                        )
                        val renderWidth = ceil(pageWidth * scale).toInt().coerceAtLeast(targetWidth)
                        val renderHeight = ceil(pageHeight * scale).toInt().coerceAtLeast(targetHeight)

                        val bitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val cropLeft = ((renderWidth - targetWidth) / 2).coerceAtLeast(0)
                        val cropTop = ((renderHeight - targetHeight) / 2).coerceAtLeast(0)
                        val croppedBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                        croppedBitmap.eraseColor(Color.WHITE)
                        Canvas(croppedBitmap).drawBitmap(
                            bitmap,
                            Rect(cropLeft, cropTop, cropLeft + targetWidth, cropTop + targetHeight),
                            Rect(0, 0, targetWidth, targetHeight),
                            null
                        )
                        bitmap.recycle()
                        croppedBitmap
                    }
                }
            }
        }.getOrNull()
    }

    private var commitBreathAnimator: ObjectAnimator? = null
    private var commitShineAnimator: ObjectAnimator? = null

    private fun startCommitBreathAnimation(binding :PopSystemR1Binding) {
        stopCommitBreathAnimation(binding)
        binding.commit.post {
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.04f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.04f)
            binding.commit.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val animator = ObjectAnimator.ofPropertyValuesHolder(binding.commit, scaleX, scaleY).apply {
                duration = 500L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            commitBreathAnimator = animator
            animator.start()
        }
    }

    private fun stopCommitBreathAnimation(binding :PopSystemR1Binding) {
        commitBreathAnimator?.cancel()
        commitBreathAnimator = null
        binding.commit.scaleX = 1f
        binding.commit.scaleY = 1f
        binding.commit.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    private fun startCommitShineAnimation(binding : PopSystemR1Binding) {
        stopCommitShineAnimation(binding)
        binding.commit.post {
            val shineWidth = binding.vCommitShine.width
            val startX = -shineWidth.toFloat()
            val endX = binding.commit.width + shineWidth.toFloat()
            binding.vCommitShine.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            val animator = ObjectAnimator.ofFloat(binding.vCommitShine, View.TRANSLATION_X, startX, endX).apply {
                duration = 1800L
                startDelay = 250L
                repeatMode = ValueAnimator.RESTART
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
            binding.vCommitShine.translationX = startX
            commitShineAnimator = animator
            animator.start()
        }
    }

    private fun stopCommitShineAnimation(binding :PopSystemR1Binding) {
        commitShineAnimator?.cancel()
        commitShineAnimator = null
        binding.vCommitShine.translationX = 0f
        binding.vCommitShine.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    private fun createAiScreenR2View(context: Context, themedContext: Context): View {
        val binding = PopSystemR2Binding.inflate(LayoutInflater.from(themedContext))
        binding.tvContent.text = context.getString(nextSystemR2ContentResId())
        binding.tvNext.setOneClick {
            EventManager.posePoint(PointConfig.float_click,Bundle().apply {
                putString("state", "small")
                putString("click_type", "close")
            })
            if (allowJump()) {
                openStartActivity(context, 0)
            }
            hideAiScreenOverlay()
        }
        binding.commit.setOneClick {
            EventManager.posePoint(PointConfig.float_click,Bundle().apply {
                putString("state", "small")
                putString("click_type", "open")
            })
            openStartActivity(context, 0)
            hideAiScreenOverlay()
        }
        binding.llRoot.setOnClickListener {
            binding.commit.performClick()
        }
        return binding.root
    }

    private fun createAiScreenR3View(context: Context, themedContext: Context): View {
        val panel = LayoutInflater.from(themedContext).inflate(R.layout.pop_system_r3, null, false)
        val actionViews = listOf(
            panel.findViewById<View>(R.id.llHome) to 0,
            panel.findViewById<View>(R.id.llSearch) to 1,
            panel.findViewById<View>(R.id.llScan) to 2,
            panel.findViewById<View>(R.id.llConvert) to 3,
        )

        actionViews.forEach { (view, index) ->
            view.setOneClick {
                EventManager.posePoint(PointConfig.float_click,Bundle().apply {
                    putString("state", "top")
                    putString("click_type", "open")
                })
                openForegroundAction(context, index)
                hideAiScreenOverlay()
            }
        }
        attachSwipeToDismiss(panel, listOf(panel) + actionViews.map { it.first })
        return panel
    }

    private fun attachSwipeToDismiss(panel: View, touchTargets: List<View>) {
        val touchSlop = ViewConfiguration.get(panel.context).scaledTouchSlop
        touchTargets.forEach { target ->
            var downX = 0f
            var downY = 0f
            var swiping = false
            target.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = event.rawX
                        downY = event.rawY
                        swiping = false
                        panel.animate().cancel()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - downX
                        val deltaY = event.rawY - downY
                        if (!swiping && kotlin.math.abs(deltaX) > touchSlop &&
                            kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)
                        ) {
                            swiping = true
                        }
                        if (swiping) {
                            panel.translationX = deltaX
                            val progress = (kotlin.math.abs(deltaX) / panel.width.coerceAtLeast(1))
                                .coerceIn(0f, 1f)
                            panel.alpha = 1f - progress * 0.5f
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = event.rawX - downX
                        val dismissThreshold = max(dp(panel.context, 80), panel.width / 4)
                        if (swiping && kotlin.math.abs(deltaX) >= dismissThreshold) {
                            val direction = if (deltaX >= 0f) 1f else -1f
                            EventManager.posePoint(PointConfig.float_click,Bundle().apply {
                                putString("state", "top")
                                putString("click_type", "close")
                            })
                            panel.animate()
                                .translationX(direction * getScreenSize().first.coerceAtLeast(panel.width))
                                .alpha(0f)
                                .setDuration(180L)
                                .withEndAction { hideAiScreenOverlay() }
                                .start()
                        } else {
                            panel.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setDuration(160L)
                                .start()
                            if (!swiping && kotlin.math.abs(deltaX) <= touchSlop &&
                                kotlin.math.abs(event.rawY - downY) <= touchSlop
                            ) {
                                view.performClick()
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        panel.animate().translationX(0f).alpha(1f).setDuration(160L).start()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    private fun openForegroundAction(context: Context, index: Int) {
        runCatching {
            PdfStartActivity.markRestartFromFloatWindow()
            val intent = Intent(context, PdfStartActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(PdfStartActivity.EXTRA_LAUNCH_SCENE, PdfStartActivity.LAUNCH_SCENE_FLOAT_WINDOW)
                putExtra(NFControl.intentParamsFrom, NFEnum.NF_FOREGROUND.menuName)
                putExtra(NFControl.intentParamsTimeIndex, index)
            }
            context.startActivity(intent)
        }.onFailure {
            AppLogs.eLog(TAG, "openForegroundAction error:${it.stackTraceToString()}")
        }
    }

    fun allowJump(): Boolean {
        var random = Random.nextInt(100)
        var allowJump = NFControl.floatRandom > random
        AppLogs.dLog(TAG,"关闭跳转状态:${allowJump} floatRandom:${NFControl.floatRandom} random:${random}")
        return allowJump
    }

    private fun nextSystemR2ContentResId(): Int {
        val resId = systemR2ContentResIds[systemR2ContentIndex % systemR2ContentResIds.size]
        systemR2ContentIndex = (systemR2ContentIndex + 1) % systemR2ContentResIds.size
        return resId
    }


    private val systemR2ContentResIds = intArrayOf(
        R.string.app_system_r2_content_1,
        R.string.app_system_r2_content_2,
        R.string.app_system_r2_content_3,
        R.string.app_system_r2_content_4,
        R.string.app_system_r2_content_5
    )

    private var systemR2ContentIndex = 0

    private fun openStartActivity(context: Context,fromType:Int) {
        runCatching {
            PdfStartActivity.markRestartFromFloatWindow()
            val intent = Intent(context, PdfStartActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                )
                putExtra(PdfStartActivity.EXTRA_FROM_TYPE, fromType)
                putExtra(PdfStartActivity.EXTRA_LAUNCH_SCENE, PdfStartActivity.LAUNCH_SCENE_FLOAT_WINDOW)
            }
            context.startActivity(intent)
        }.onFailure {
            AppLogs.eLog(TAG, "openDocDialogActivity error:${it.stackTraceToString()}")
        }
    }


    private fun getScreenSize(): Pair<Int, Int> {
        val wm = windowManager ?: return 0 to 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun dp(context: Context, value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resourceId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) context.resources.getDimensionPixelSize(resourceId) else 0
    }


    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }
}
