// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.scan

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStateAtLeast
import androidx.viewpager2.widget.ViewPager2
import com.pdf.editor.R
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.ui.tools.document.file.ToolSessionStore
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.config.PointConfig as EventConfig
import com.pdf.editor.ui.detail.reader.setSequentialClickListener
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.databinding.ToolActivityScanBinding
import com.pdf.editor.ui.tools.document.file.PdfThumbnailGenerator
import com.pdf.editor.ui.tools.document.file.ScannedFile
import com.pdf.editor.ui.detail.reader.ReaderSurfaceActivity
import com.pdf.editor.ui.tools.document.common.FileRenamePop
import com.pdf.editor.ui.tools.document.common.PdfCreator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 预览采集的页面，并按确认后的顺序创建文档。 */
class CaptureReviewActivity : BaseActivity<ToolActivityScanBinding>() {
    // PDF14-MIGRATION: 当前 BaseActivity 的绑定字段；源页面绑定调用保持不变。
    private val binding: ToolActivityScanBinding get() = aBinding

    private val pages = mutableListOf<Uri>()
    private val pageAdapter = ScanPreviewAdapter(pages)
    private var scanAnimator: ObjectAnimator? = null
    private var scanAnimationCancelled = false
    // 扫描转换完成后暂存结果，等待 pr_use_end 广告结束再打开 PDF。
    private var pendingCreatedFile: File? = null
    private var useEndContinuationConsumed = false

    override fun getBinding(inflater: LayoutInflater): ToolActivityScanBinding {
        return ToolActivityScanBinding.inflate(inflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showStatusBarText(false)
    }

    override fun setShowView() {
        pages.addAll(intent.readPages())
        if (pages.isEmpty()) {
            finish()
            return
        }
        EventManager.posePoint(EventConfig.scan_loading_page)
        binding.vp2.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        binding.vp2.adapter = pageAdapter
        showReviewPending()
        runReviewMotion()
    }

    override fun setListener() {
        binding.btnSave.setSequentialClickListener {
            FileRenamePop(this).createPop(createOutputName(), R.string.app_scan_to_pdf, R.string.app_save_pdf, onConfirm = ::assembleDocument)
        }
        binding.ivBack.setSequentialClickListener {
            finish()
        }
    }


    /** 创建输出 PDF，并发布到本地文件列表。 */
    private fun assembleDocument(fileName: String) {
        val snapshot = pages.toList()
        binding.btnSave.isEnabled = false
        binding.tvProgress.setText(R.string.app_pdf_creating_hint)
        addLaunch(
            success = {
                val file = PdfCreator.create(this@CaptureReviewActivity, snapshot, fileName)
                EventManager.posePoint(EventConfig.scan_complete_pdf)
                val created = ScannedFile.fromFile(file)
                    ?.copy(
                        thumbnailPath = PdfThumbnailGenerator.generate(
                            context = this@CaptureReviewActivity,
                            filePath = file.absolutePath
                        )?.absolutePath
                    )
                withContext(Dispatchers.Main) {
                    // PDF14-MIGRATION: 当前列表缓存由主线程维护，沿用源输出后的刷新顺序。
                    created?.let(ToolSessionStore::addCreatedFile)
                    showUseEndAdThenOpenFile(file)
                }
            },
            failBack = {
                showReviewReady()
                Toast.makeText(this@CaptureReviewActivity, R.string.app_create_pdf_failed, Toast.LENGTH_SHORT).show()
            }
        )
    }

    /** 扫描图片成功转换为 PDF 后展示 pr_use_end 广告，再打开生成的文件。 */
    private fun showUseEndAdThenOpenFile(file: File) {
        pendingCreatedFile = file
        useEndContinuationConsumed = false
        runCatching {
            val adShowManager = ADShowManager(this, "CaptureReviewActivity")
            adShowManager.showScreenAD(
                EventConfig.AD_POINT_USE_END,
                ADEnum.INT_AD_USER
            ) { _, _ ->
                continueAfterUseEndAd()
            }
        }.onFailure {
            AppLogs.eLog(aTAG, "showScreenAD error:${it.stackTraceToString()}")
            continueAfterUseEndAd()
        }
    }

    /**
     * 广告结束或被拦截后只打开一次生成结果。
     *
     * 广告关闭回调可能早于 Activity.onResume()，因此不能在回调中立即跳转。
     * 与启动页一致，先等待生命周期恢复到 RESUMED，再检查 Activity 当前状态。
     */
    private fun continueAfterUseEndAd() {
        if (useEndContinuationConsumed) {
            return
        }
        useEndContinuationConsumed = true
        lifecycleScope.launch {
            lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                if (isFinishing || isDestroyed || !getActivityStatus()) {
                    return@withStateAtLeast
                }
                val file = pendingCreatedFile
                pendingCreatedFile = null
                if (file != null) {
                    ReaderSurfaceActivity.open(
                        this@CaptureReviewActivity,
                        file.name,
                        file.absolutePath
                    )
                    finish()
                }
            }
        }
    }

    /** 在页面准备期间运行预览动画。 */
    private fun runReviewMotion() {
        binding.scanLine.doOnLayout {
            val distance = (binding.flRoot.height - binding.scanLine.height).toFloat()
            var lastDotCount = 0
            scanAnimator = ObjectAnimator.ofFloat(binding.scanLine, "translationY", 0f, distance).apply {
                duration = 3000L
                repeatCount = 1
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val dotCount = ((animator.currentPlayTime / 1000L).toInt() % 3) + 1
                    if (dotCount != lastDotCount) {
                        lastDotCount = dotCount
                        updateReviewCaption(dotCount)
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        scanAnimationCancelled = false
                    }

                    override fun onAnimationCancel(animation: Animator) {
                        scanAnimationCancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (!scanAnimationCancelled) {
                            showReviewReady()
                            binding.scanLine.visibility = View.GONE
                        }
                    }
                })
                start()
            }
        }
    }

    /** 将预览控件切换到处理中状态。 */
    private fun showReviewPending() {
        binding.btnSave.isEnabled = false
        updateReviewCaption(dotCount = 1)
    }

    /** 更新动画进度文案。 */
    private fun updateReviewCaption(dotCount: Int) {
        binding.tvProgress.text = buildString {
            append(getString(R.string.app_scan_preview_loading))
            repeat(dotCount) { append('.') }
        }
    }

    /** 页面准备完成后启用文档创建操作。 */
    private fun showReviewReady() {
        binding.tvProgress.setText(R.string.app_scan_preview_ready)
        binding.btnSave.isEnabled = true
    }

    /** 默认输出名称仅保留日期和时间，不添加 PDF14 品牌前缀。 */
    private fun createOutputName(): String {
        return SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    }

    override fun onDestroy() {
        scanAnimator?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PAGE_URIS = "scan_page_uris"

        /** 使用选中的页面 URI 打开采集预览页面。 */
        fun open(context: Context, pages: List<Uri>) {
            context.startActivity(
                Intent(context, CaptureReviewActivity::class.java).apply {
                    putParcelableArrayListExtra(EXTRA_PAGE_URIS, ArrayList(pages))
                }
            )
        }

        /** 读取启动 Intent 中保存的采集页面列表。 */
        @Suppress("DEPRECATION")
        private fun Intent.readPages(): List<Uri> {
            return getParcelableArrayListExtra<Uri>(EXTRA_PAGE_URIS).orEmpty()
        }
    }
}
