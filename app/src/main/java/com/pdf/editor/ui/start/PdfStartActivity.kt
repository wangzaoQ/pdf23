package com.pdf.editor.ui.start

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import com.pdf.editor.common.config.FirebaseManager
import android.view.LayoutInflater
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStateAtLeast
import com.pdf.editor.MyAPP
import com.pdf.editor.common.ReadService
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADControl.preloadAD
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.ad.UmpHelper
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.nf.NFControl
import com.pdf.editor.common.nf.NFEnum
import com.pdf.editor.common.nf.NotifyShow
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.SendUtils
import com.pdf.editor.common.tools.PermissionManager
import com.pdf.editor.common.tools.jobCancel
import com.pdf.editor.common.tools.jumpToActivity
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.nf.floatwindow.FloatDesktopWindow
import com.pdf.editor.databinding.ActivityPdfStartBinding
import com.pdf.editor.ui.PdfMainActivity
import com.pdf.editor.feature.preferences.language.LanguagePickerActivity
import com.pdf.editor.feature.onboarding.overlay.OverlayPermissionActivity
import com.pdf.editor.pop.NFGuidePop
import com.pdf.editor.feature.onboarding.OnboardingActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class PdfStartActivity : BaseActivity<ActivityPdfStartBinding>() {

    companion object {
        const val EXTRA_FROM_TYPE = "fromType"
        const val EXTRA_LAUNCH_SCENE = "launchScene"
        private const val EXTRA_ROUTED_TO_APP_TASK = "routedToAppTask"

        const val LAUNCH_SCENE_COLD_START = "cold_start"
        const val LAUNCH_SCENE_HOT_START = "hot_start"
        const val LAUNCH_SCENE_NOTIFICATION = "notification"
        const val LAUNCH_SCENE_FILE_PERMISSION = "file_permission"
        const val LAUNCH_SCENE_WIDGET = "widget"
        const val LAUNCH_SCENE_MEDIA_NOTIFICATION = "media_notification"
        const val LAUNCH_SCENE_FLOAT_WINDOW = "float_window"
        const val LAUNCH_SCENE_EXTERNAL_FILE = "external_file"
        const val LAUNCH_SCENE_UNKNOWN = "unknown"

        @Volatile
        private var restartMarkTime = 0L

        fun markRestartFromFloatWindow() {
            restartMarkTime = System.currentTimeMillis()
        }
    }

    private val activityCreateTime = System.currentTimeMillis()
    private val launchAdShowTimeout = 5_000L
    private var umpInitialized = false
    private var fromType = 0
    private var launchScene = LAUNCH_SCENE_UNKNOWN
    private var enumName = ""
    private var nfIndex = 0
    private var nfID = 0
    private var launchContent = ""
    private var progressJob: Job? = null
    private var adShowTimeoutJob: Job? = null
    private val adHandled = AtomicBoolean(false)

    @Volatile
    private var loadTime = 0

    private var adTime = 2_000L
    private var toNext = false

    override fun getBinding(inflater: LayoutInflater): ActivityPdfStartBinding {
        return ActivityPdfStartBinding.inflate(layoutInflater)
    }

    override fun setListener() = Unit

    override fun setShowView() {
        if (routeExternalFileIntentToAppTaskIfNeeded(intent)) return
        val source = intent.getStringExtra(EXTRA_LAUNCH_SCENE)
        if (source == LAUNCH_SCENE_NOTIFICATION ||
            source == LAUNCH_SCENE_MEDIA_NOTIFICATION ||
            source == LAUNCH_SCENE_FLOAT_WINDOW||
            source == LAUNCH_SCENE_WIDGET
        ) {
            // 通知、悬浮窗入口会主动清空任务栈，之前的全屏广告页面也随之失效。
            ADControl.fullScreenAdShowing = false
        }
        if (ADControl.fullScreenAdShowing) {
            AppLogs.dLog(aTAG, "广告展示中")
            finish()
            overridePendingTransition(0, 0)
            return
        }
        ReadService.enableAfterAppLaunch(this)
        EventManager.session()
        EventManager.posePoint(PointConfig.launch_page)
        readLaunchParams(intent)
        adTime = (ADControl.new_op_cd ?: 10) * 1_000L
        initUMP()
        aBinding.root.post {
            ADControl.width = aBinding.root.width
            PermissionManager.requestNFPermission(this, onGranted = {
                NotifyShow.startForeground("StartActivity")
            }, onFail = {
            })
            EventManager.posePoint(PointConfig.float, Bundle().apply {
                putString("state",if (PermissionManager.allowSystemAlert(this@PdfStartActivity))"on" else "off")
            })
        }
        AppLogs.dLog(aTAG, "detailIntent:onCreate")
        detailIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        AppLogs.dLog(aTAG, "detailIntent:onNewIntent")
        if (isLauncherIntent(intent)) return
        setIntent(intent)
        if (routeExternalFileIntentToAppTaskIfNeeded(intent)) return
        readLaunchParams(intent)
        detailIntent(intent)
    }

    private fun isLauncherIntent(intent: Intent): Boolean =
        intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)


    private fun routeExternalFileIntentToAppTaskIfNeeded(intent: Intent): Boolean {
        if (!isExternalFileIntent(intent)) return false
        if (intent.getBooleanExtra(EXTRA_ROUTED_TO_APP_TASK, false)) return false
        AppLogs.dLog(aTAG, "route external file intent to app task")
        // Do not add FLAG_GRANT_READ_URI_PERMISSION here.  This app does not own
        // a third-party FileProvider URI, so it cannot grant that URI again.  A
        // grant supplied by the original sender is already retained by Intent(intent).
        val routed = runCatching {
            startActivity(Intent(intent).apply {
                setClass(this@PdfStartActivity, PdfStartActivity::class.java)
                setPackage(packageName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra(EXTRA_ROUTED_TO_APP_TASK, true)
                putExtra(EXTRA_LAUNCH_SCENE, LAUNCH_SCENE_EXTERNAL_FILE)
            })
        }.onFailure { error ->
            AppLogs.dLog(aTAG, "route external file intent failed: ${error.message}")
        }.isSuccess
        if (!routed) return false
        overridePendingTransition(0, 0)
        finish()
        return true
    }

    private fun isExternalFileIntent(intent: Intent): Boolean {
        return intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND
    }

    private fun readLaunchParams(intent: Intent) {
        fromType = intent.getIntExtra(EXTRA_FROM_TYPE, 0)
        enumName = intent.getStringExtra(NFControl.intentParamsFrom) ?: ""
        nfIndex = intent.getIntExtra(NFControl.intentParamsTimeIndex, 0)
        nfID = intent.getIntExtra(NFControl.intentParamsNFID, 0)
        launchScene = resolveLaunchScene(intent)
        AppLogs.dLog(aTAG, "launchScene:$launchScene fromType:$fromType enumName:$enumName nfIndex:$nfIndex nfID:$nfID action:${intent.action}")
        pointByNF()
        if (launchScene == LAUNCH_SCENE_WIDGET || launchScene == LAUNCH_SCENE_NOTIFICATION){
            launchContent = if (nfIndex == 1) {
                "search"
            } else if (nfIndex == 2) {
                "scan"
            } else if (nfIndex == 3) {
                "convert"
            } else {
                "home"
            }
        }
    }

    private fun resolveLaunchScene(intent: Intent): String {
        intent.getStringExtra(EXTRA_LAUNCH_SCENE)?.takeIf { it.isNotEmpty() }?.let { return it }
        if (enumName == NFEnum.NF_MEDIA.menuName) return LAUNCH_SCENE_MEDIA_NOTIFICATION
        if (enumName.isNotEmpty()) return LAUNCH_SCENE_NOTIFICATION
        return when (intent.action) {
            Intent.ACTION_MAIN -> LAUNCH_SCENE_COLD_START
            Intent.ACTION_VIEW, Intent.ACTION_SEND -> LAUNCH_SCENE_EXTERNAL_FILE
            else -> if (fromType == 1) LAUNCH_SCENE_HOT_START else LAUNCH_SCENE_UNKNOWN
        }
    }

    private fun pointByNF() {
        if (enumName.isEmpty()) return
        if (enumName == NFEnum.NF_TIMER_1.menuName) {
            clickNF(NFEnum.NF_TIMER_1)
        } else if (enumName == NFEnum.NF_UNLOCK.menuName) {
            clickNF(NFEnum.NF_UNLOCK)
        } else if (enumName == NFEnum.NF_NEWS_FCM.menuName) {
            clickNF(NFEnum.NF_NEWS_FCM)
        } else if (enumName == NFEnum.NF_SCREEN_ON.menuName) {
            clickNF(NFEnum.NF_SCREEN_ON)
        } else if (enumName == NFEnum.NF_SCREEN_OFF.menuName) {
            clickNF(NFEnum.NF_SCREEN_OFF)
        } else if (enumName == NFEnum.NF_BACKGROUND.menuName) {
            clickNF(NFEnum.NF_BACKGROUND)
        } else if (enumName == NFEnum.NF_HOME.menuName) {
            clickNF(NFEnum.NF_HOME)
        } else if (enumName == NFEnum.NF_RECENT.menuName) {
            clickNF(NFEnum.NF_RECENT)
        } else if (enumName == NFEnum.NF_NEW_FILE.menuName) {
            clickNF(NFEnum.NF_NEW_FILE)
        } else if (enumName == NFEnum.NF_POWER_CONNECTED.menuName) {
            clickNF(NFEnum.NF_POWER_CONNECTED)
        } else if (enumName == NFEnum.NF_POWER_DISCONNECTED.menuName) {
            clickNF(NFEnum.NF_POWER_DISCONNECTED)
        } else if (enumName == NFEnum.NF_CONFIGURATION_CHANGED.menuName) {
            clickNF(NFEnum.NF_CONFIGURATION_CHANGED)
        } else if (enumName == NFEnum.NF_FOREGROUND.menuName) {
            EventManager.posePoint(PointConfig.inform_c, Bundle().apply {
                putString(PointKey.source, NFEnum.NF_FOREGROUND.pointTag)
            })
        } else if (enumName == NFEnum.NF_MEDIA.menuName) {
            clickNF(NFEnum.NF_MEDIA)
        }
    }

    private fun clickNF(enum: NFEnum) {
        EventManager.posePoint(PointConfig.inform_c, Bundle().apply {
            putString(PointKey.source, enum.pointTag)
        })
        runCatching {
            NFControl.manager.cancel(nfID)
        }
        NotifyShow.currentNFJob?.jobCancel()
    }

    private fun detailIntent(intent: Intent) {
        val uri = getUriFromIntent(intent) ?: return
        val mimeType = SendUtils.getActualMimeType(uri, intent)
        if (SendUtils.isSupported(mimeType)) {
            AppLogs.dLog(aTAG, "supported mimeType:$mimeType")
            MyAPP.instance.lifecycleApp.finishAllExcept(this)
            SendUtils.parseUri(uri)
        } else {
            AppLogs.dLog(aTAG, "unsupported mimeType:$mimeType")
        }
    }

    private fun getUriFromIntent(intent: Intent): Uri? {
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
    }

    private fun initUMP() {
        UmpHelper.initUmp(this) { obtained ->
            if (isDestroyed || umpInitialized) return@initUmp
            umpInitialized = true
            ADControl.clearShownOpenAdCache()
            FirebaseManager.refreshLatestADConfig()
            adTime = (ADControl.new_op_cd ?: 10) * 1_000L
            AppLogs.dLog(aTAG, "complete UMP obtained:$obtained")
            aBinding.root.post {
                addLaunch(success = {
                    while (true) {
                        delay(1_000L)
                        if (getActivityStatus()) loadTime++
                    }
                }, failBack = {}, dispatcher = Dispatchers.Default)
                startLoading()
            }
        }
    }

    private fun startLoading() {
        val firstShowOpen = LocalDataManager.cacheFirstShowOpenAD
        if (firstShowOpen){
            LocalDataManager.cacheFirstShowOpenAD = false
        }
        // 已达到开屏每日展示或点击限额时，不再等待开屏库存，直接结束启动进度。
        if (ADControl.isOpenAdFrequencyLimitReached("StartActivity")) {
            endProgress()
            return
        }
        if (firstShowOpen) {
            ADControl.addChance(ADEnum.OPEN_AD_ONLY, PointConfig.AD_POINT_LAUNCH)
            preloadAD(ADEnum.OPEN_AD_ONLY)
        } else {
            ADControl.addChance(ADEnum.OPEN_AD, resolveOpenAdPointTag())
        }
        preloadAD(ADEnum.OPEN_AD)
        val hasCachedAd = ADControl.getCacheAD(ADEnum.OPEN_AD_ONLY) != null ||
            (!firstShowOpen && ADControl.getCacheAD(ADEnum.OPEN_AD) != null)
        val progressTime = if (hasCachedAd) 1_000L else adTime
        var lastAdCacheCheckTime = 0L
        startProgress(0, 100, progressTime, update = { progress ->
            aBinding.progress.progress = progress
            aBinding.tvProgress.text = "$progress%"
            if (hasCachedAd) return@startProgress
            val now = SystemClock.elapsedRealtime()
            if (now - lastAdCacheCheckTime < 1_000L) return@startProgress
            lastAdCacheCheckTime = now
            val openOnly = ADControl.getCacheAD(ADEnum.OPEN_AD_ONLY)
            val open = ADControl.getCacheAD(ADEnum.OPEN_AD)
            if (firstShowOpen) {
                if (openOnly != null) {
                    endProgress()
                    return@startProgress
                }else if (loadTime > 5 && open!=null){
                    endProgress()
                    return@startProgress
                } else if (ADControl.newRequestConfig == 1) {
                    preloadAD(ADEnum.OPEN_AD_ONLY)
                }
            } else {
                if (openOnly != null || open != null) {
                    endProgress()
                    return@startProgress
                }
            }
        }, complete = { endProgress() })
    }

    private fun toNext() {
        AppLogs.dLog(aTAG, "toNext")
        if (LocalDataManager.cacheLanguageFirst || LocalDataManager.cacheGuideFirst) {
            preloadAD(ADEnum.BANNER_AD_1)
            preloadAD(ADEnum.BANNER_AD_2)
            preloadAD(ADEnum.INT_AD_NEW)
        } else {
            preloadAD(ADEnum.BANNER_AD_1)
            preloadAD(ADEnum.BANNER_AD_2)
            preloadAD(ADEnum.INT_AD_USER)
        }
        aBinding.tvProgress.text = "100%"
        addLaunch(success = {
            while (!getActivityStatus()) delay(100L)
            AppLogs.dLog(aTAG, "getActivityStatus 通过")
            withContext(Dispatchers.Main) {
                val manager = ADShowManager(this@PdfStartActivity, "StartActivity")
                val newLaunchAd = ADControl.getCacheAD(ADEnum.OPEN_AD_ONLY)
                var adEnum = if (newLaunchAd!=null) {
                    ADEnum.OPEN_AD_ONLY
                } else {
                    ADEnum.OPEN_AD
                }
                var pointTag = PointConfig.AD_POINT_LAUNCH
                if (adEnum == ADEnum.OPEN_AD) {
                    pointTag = resolveOpenAdPointTag()
                }
                runCatching {
                    manager.showScreenAD(pointTag, adEnum) { type, _ ->
                        continueAfterAd("callback:$type")
                    }
                }.onFailure {
                    AppLogs.eLog(aTAG, "showScreenAD error:${it.stackTraceToString()}")
                    continueAfterAd("show_error2")
                }
            }
        }, failBack = {
            continueAfterAd("launch_error")
        })
    }

    private fun resolveOpenAdPointTag(): String = when (launchScene) {
        LAUNCH_SCENE_HOT_START -> PointConfig.AD_POINT_OPEN_HOT
        LAUNCH_SCENE_MEDIA_NOTIFICATION -> PointConfig.AD_POINT_OPEN_MEDIA_POP
        LAUNCH_SCENE_NOTIFICATION -> PointConfig.AD_POINT_OPEN_NOTI
        LAUNCH_SCENE_FLOAT_WINDOW -> PointConfig.AD_POINT_OPEN_POP
        LAUNCH_SCENE_EXTERNAL_FILE -> PointConfig.AD_POINT_OPEN_FILE
        LAUNCH_SCENE_COLD_START -> PointConfig.AD_POINT_OPEN_COLD
        LAUNCH_SCENE_WIDGET -> PointConfig.AD_POINT_OPEN_HOT
        LAUNCH_SCENE_FILE_PERMISSION -> PointConfig.AD_POINT_LAUNCH3
        else -> PointConfig.AD_POINT_OPEN_COLD
    }

    private fun continueAfterAd(reason: String) {
        // 页面恢复后再继续；如果页面被通知清栈销毁，该任务会自动取消。
        lifecycleScope.launch {
            lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                if (isFinishing || isDestroyed || activityCreateTime < restartMarkTime || !adHandled.compareAndSet(false, true)) {
                    AppLogs.dLog(aTAG, "continueAfterAd:$reason")
                    return@withStateAtLeast
                }
                AppLogs.dLog("continueAfterAd", "reason=$reason")
                toMainPage()
            }
        }
    }

    private fun toMainPage() {
        if (fromType == 4) {
            //从全局弹窗进来打开最近的pdf
            jumpToActivity<PdfMainActivity>(Bundle().apply {
                putInt("fromType", fromType)
                putString("nfContent", "recentOpenPDF")
            })
            finish()
            return
        }
        if (LocalDataManager.allowToSystem){
            LocalDataManager.allowToSystem = false
            jumpToActivity<OverlayPermissionActivity>(Bundle().apply {
                putInt("systemType", 1)
                putInt("fromType", fromType)
                putString("nfContent", launchContent)
            })
            AppLogs.dLog(aTAG, "start OverlayPermissionActivity")
        }else if (LocalDataManager.cacheLanguageFirst) {
            jumpToActivity<LanguagePickerActivity>(Bundle().apply {
                putInt("fromType", 1)
                putString("nfContent", launchContent)
            })
            AppLogs.dLog(aTAG, "start LanguagePickerActivity")
        }else if (LocalDataManager.cacheGuideFirst){
            jumpToActivity<OnboardingActivity>(Bundle().apply {
                putString("nfContent", launchContent)
            })
            AppLogs.dLog(aTAG, "start OnboardingActivity")
        }else{
            var size = 0
            for (activity in MyAPP.instance.lifecycleApp.stack) {
                if (activity is BaseActivity<*>) {
                    size++
                }
            }
            if (size == 1 || launchScene == LAUNCH_SCENE_WIDGET) {
                var toSystem = FirebaseManager.getConfig("to_system").toIntOrNull()?:0
                if (!PermissionManager.allowSystemAlert(this)&&toSystem == 1) {
                    jumpToActivity<OverlayPermissionActivity>(Bundle().apply {
                        putInt("systemType", 3)
                        putInt("fromType", fromType)
                        putString("nfContent", launchContent)
                    })
                } else {
                    AppLogs.dLog(aTAG, "start PdfMainActivity")
                    jumpToActivity<PdfMainActivity>(Bundle().apply {
                        putInt("fromType", fromType)
                        putString("nfContent", launchContent)
                    })
                }
            }
        }
        finish()
    }

    private fun endProgress() {
        if (toNext) return
        toNext = true
        progressJob.jobCancel()
        aBinding.root.postDelayed({
            toNext()
        }, 500)
        aBinding.progress.progress = 100
    }

    private fun startProgress(
        current: Int,
        max: Int,
        durationMs: Long,
        update: (Int) -> Unit,
        complete: () -> Unit,
    ) {
        progressJob.jobCancel()
        progressJob = addLaunch(success = {
            val startedAt = System.currentTimeMillis()
            while (true) {
                val fraction = ((System.currentTimeMillis() - startedAt).toFloat() / durationMs).coerceAtMost(1f)
                withContext(Dispatchers.Main) {
                    update((current + (max - current) * fraction).toInt())
                }
                if (fraction >= 1f) break
                delay(16L)
            }
            withContext(Dispatchers.Main) { complete() }
        }, failBack = {})
    }

    override fun onResume() {
        runCatching {
            FloatDesktopWindow.hideAiScreenOverlay()
        }
        super.onResume()
    }

    override fun onDestroy() {
        progressJob.jobCancel()
        adShowTimeoutJob.jobCancel()
        super.onDestroy()
    }
}
