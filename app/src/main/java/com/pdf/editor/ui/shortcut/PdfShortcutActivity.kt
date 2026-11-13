package com.pdf.editor.ui.shortcut

import android.content.Intent
import android.view.LayoutInflater
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStateAtLeast
import com.pdf.editor.common.ReadService
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADControl.preloadAD
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.ad.UmpHelper
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.nf.NotifyShow
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.GlobalConfig.LONG_CLICK_UNINSTALL
import com.pdf.editor.common.tools.PermissionManager
import com.pdf.editor.common.tools.jobCancel
import com.pdf.editor.common.tools.jumpToActivity
import com.pdf.editor.databinding.ActivityPdfStartBinding
import com.pdf.editor.pop.NFGuidePop
import com.pdf.editor.feature.retention.RetentionOfferActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class PdfShortcutActivity : BaseActivity<ActivityPdfStartBinding>() {

    companion object {
        private const val ACTION_SHORTCUT_UNINSTALL =
            "com.test.pdf.action.SHORTCUT_UNINSTALL"
    }

    override fun getBinding(inflater: LayoutInflater): ActivityPdfStartBinding {
        return ActivityPdfStartBinding.inflate(layoutInflater)
    }

    override fun setListener() = Unit

    private var adTime = 15_000L
    private var longClickType = -1
    private var umpInitialized = false
    private var toNext = false
    private var progressJob: Job? = null
    private val adHandled = AtomicBoolean(false)

    override fun setShowView() {
        EventManager.posePoint(PointConfig.uninstall_click)
        ReadService.enableAfterAppLaunch(this)
        adTime = (ADControl.new_op_cd ?: 10) * 1_000L
        PermissionManager.requestNFPermission(this, onGranted = {
            NotifyShow.startForeground("PdfShortcutActivity")
        }, onFail = {
            NFGuidePop(this).createPop {
                NotifyShow.startForeground("PdfShortcutActivity")
            }
        })
        initUMP()
        aBinding.root.post {
            ADControl.width = aBinding.root.width
        }
        AppLogs.dLog(aTAG, "detailIntent: onCreate")
        detailIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        AppLogs.dLog(aTAG, "detailIntent: onNewIntent")
        if (intent == null) return
        detailIntent(intent)
    }

    private fun detailIntent(intent: Intent) {
        val shortcutAction = intent.action.orEmpty()
        val shortcutSource = if (shortcutAction == ACTION_SHORTCUT_UNINSTALL) {
            "desktop_long_press"
        } else {
            intent.getStringExtra("source")
                ?: intent.data?.getQueryParameter("source")
                ?: ""
        }
        AppLogs.dLog(
            aTAG,
            "launchUninstallPage action:$shortcutAction source:$shortcutSource data:${intent.data}"
        )
        if (
            shortcutAction == ACTION_SHORTCUT_UNINSTALL ||
            shortcutSource == "desktop_long_press"
        ) {
            longClickType = LONG_CLICK_UNINSTALL
        }
    }

    private fun initUMP() {
        AppLogs.dLog(aTAG, "start UMP")
        UmpHelper.initUmp(this) { obtained ->
            if (isDestroyed || umpInitialized) return@initUmp
            umpInitialized = true
            AppLogs.dLog(aTAG, "complete UMP")
            preloadAD(ADEnum.INT_AD_EXIT)
            preloadAD(ADEnum.BANNER_AD_2)
            preloadAD(ADEnum.BANNER_AD_1)
            AppLogs.dLog(aTAG, "User consent obtained: $obtained")
            startLoading()
        }
    }

    private fun startLoading() {
        AppLogs.dLog(aTAG, "startLoading")
        val hasCachedAd = ADControl.getCacheAD(ADEnum.INT_AD_EXIT) != null
        val progressTime = if (hasCachedAd) 1_000L else adTime
        startProgress(0, 100, progressTime, update = {
            aBinding.progress.progress = it
            aBinding.tvProgress.text = "$it%"
            if (hasCachedAd) return@startProgress
            if (ADControl.getCacheAD(ADEnum.INT_AD_EXIT) != null) {
                endProgress()
                return@startProgress
            }
        }, complete = {
            endProgress()
        })
    }

    private fun toNext() {
        AppLogs.dLog(aTAG, "toNext")
        aBinding.tvProgress.text = "100%"
        addLaunch(success = {
            while (!getActivityStatus()) {
                delay(100L)
            }
            withContext(Dispatchers.Main) {
                val adShowManager = ADShowManager(
                    this@PdfShortcutActivity,
                    "PdfShortcutActivity"
                )
                adShowManager.showScreenAD(
                    PointConfig.AD_POINT_UNLOAD1,
                    ADEnum.INT_AD_EXIT
                ) { _, _ ->
                    addLaunch(success = {
                        while (!getActivityStatus()) {
                            delay(200L)
                        }
                        withContext(Dispatchers.Main) {
                            continueAfterAd("callback:launch_error1")
                        }
                    }, failBack = {
                        continueAfterAd("callback:launch_error2")
                    })
                }
            }
        }, failBack = {
            continueAfterAd("callback:launch_error3")
        })
    }

    private fun continueAfterAd(reason: String) {
        // 页面恢复后再继续；如果页面被通知清栈销毁，该任务会自动取消。
        lifecycleScope.launch {
            lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                if (isFinishing || isDestroyed || !adHandled.compareAndSet(false, true)) {
                    AppLogs.dLog(aTAG, "continueAfterAd:$reason")
                    return@withStateAtLeast
                }
                AppLogs.dLog(aTAG, "reason=$reason")
                jumpToActivity<RetentionOfferActivity>()
                finish()
            }
        }
    }

    private fun endProgress() {
        if (toNext) return
        toNext = true
        AppLogs.dLog(aTAG, "endProgress")
        progressJob.jobCancel()
        aBinding.progress.progress = 100
        aBinding.tvProgress.text = "100%"
        aBinding.root.postDelayed({
            toNext()
        }, 500L)
    }

    private fun startProgress(
        current: Int = 0,
        max: Int = 100,
        durationMs: Long,
        update: (value: Int) -> Unit = {},
        complete: () -> Unit = {}
    ) {
        AppLogs.dLog(aTAG, "startProgress")
        progressJob.jobCancel()
        progressJob = addLaunch(success = {
            val startTime = System.currentTimeMillis()
            val totalDiff = max - current
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val fraction = (elapsed.toFloat() / durationMs).coerceAtMost(1f)
                val currentValue = (current + totalDiff * fraction).toInt()
                withContext(Dispatchers.Main) {
                    update(currentValue)
                }
                if (fraction >= 1f) {
                    AppLogs.dLog(aTAG, "complete normal")
                    complete()
                    break
                }
                delay(50L)
            }
        }, failBack = {
            AppLogs.dLog(aTAG, "complete error_1")
            complete()
        })
    }

    override fun onBackPressed() = Unit
}
