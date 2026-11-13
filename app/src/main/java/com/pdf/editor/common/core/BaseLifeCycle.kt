package com.pdf.editor.common.core

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import com.pdf.editor.MyAPP
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.WakeManager
import com.pdf.editor.common.nf.NFEnum
import com.pdf.editor.common.nf.NotifyShow
import com.pdf.editor.common.point.SessionManager
import com.pdf.editor.feature.retention.RetentionOfferActivity
import com.pdf.editor.ui.shortcut.PdfShortcutActivity
import com.pdf.editor.ui.start.PdfStartActivity

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Stack
import kotlin.time.Duration.Companion.milliseconds

class BaseLifeCycle : Application.ActivityLifecycleCallbacks {

    val stack = Stack<Activity>()

    private var cancelJob: Job? = null
    @Volatile
    var isBackstage = false

    @Volatile
    var count = 0
    var tempGoBackType = ""

    /**
     * 清理除了指定 Activity 以外的所有页面
     */
    fun finishAllExcept(current: Activity) {
        val iterator = stack.iterator()
        while (iterator.hasNext()) {
            val activity = iterator.next()
            if (activity != current) {
                activity.finish() // 销毁页面
                iterator.remove() // 从栈中移除
            }
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        stack.add(activity)
        AppLogs.dLog(MyAPP.instance.TAG, "onActivityCreated() activity=" + activity + " stack.size=" + stack.size)
    }

    var startTime = 0L
    override fun onActivityStarted(activity: Activity) {
        count++
        SessionManager.reset()
        AppLogs.dLog(MyAPP.instance.TAG, "onActivityStarted() activity=" + activity + "count:"+count)
        if (startTime == 0L){
            AppLogs.dLog(MyAPP.instance.TAG,"startTime 开始计时:${startTime}")
            startTime = System.currentTimeMillis()
        }
        cancelJob?.cancel()

        var allowContinue = false
        runCatching {
            allowContinue = WakeManager.isScreenOn()
        }
        if (allowContinue.not()){
            AppLogs.dLog(MyAPP.instance.TAG,"onActivityStarted 终止 screen:${WakeManager.isScreenOn()}  devicelock:${WakeManager.isDeviceLocked().not()}")
            return
        }
        AppLogs.dLog(MyAPP.instance.TAG,"onActivityStarted_isBackstage:$isBackstage  ")
        if (isBackstage) {
            val topActivity = stack.lastOrNull() ?: return
            AppLogs.dLog(MyAPP.instance.TAG,"currentActivity:${topActivity.localClassName}  ADControl.fullScreenAdShowing:${ADControl.fullScreenAdShowing}")
            if (topActivity is BaseActivity<*> && !ADControl.fullScreenAdShowing
                && topActivity !is PdfStartActivity && topActivity !is PdfShortcutActivity && topActivity !is RetentionOfferActivity
            ) {
                AppLogs.dLog(MyAPP.instance.TAG,"onActivityStarted start PdfStartActivity")
                if (tempGoBackType == "file"){
                    topActivity.startActivity(Intent(topActivity, PdfStartActivity::class.java).apply {
                        putExtra(PdfStartActivity.EXTRA_FROM_TYPE, 2)
                        putExtra(PdfStartActivity.EXTRA_LAUNCH_SCENE, PdfStartActivity.LAUNCH_SCENE_FILE_PERMISSION)
                    })
                } else  if (tempGoBackType == "system"){
                    //暂时无逻辑
                }else{
                    topActivity.startActivity(Intent(topActivity, PdfStartActivity::class.java).apply {
                        putExtra(PdfStartActivity.EXTRA_FROM_TYPE, 1)
                        putExtra(PdfStartActivity.EXTRA_LAUNCH_SCENE,PdfStartActivity.LAUNCH_SCENE_HOT_START)
                    })
                }
            }
            tempGoBackType = ""
            isBackstage = false
        }
    }

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) {
        val wasForegroundBeforeStop = count == 1
        count--
        AppLogs.dLog(MyAPP.instance.TAG, "onActivityStopped() activity=" + activity + "count:"+count +"cd_hot:${ADControl.cd_hot}")
        if (0 >= count) {
            startTime = 0L
            if (ADControl.goBackType.isNotEmpty()){
                isBackstage = true
                tempGoBackType = ADControl.goBackType
            }
            if (activity.isChangingConfigurations) {   // 配置变更引起的 stop，不是进后台
                AppLogs.dLog(MyAPP.instance.TAG, "onActivityStopped skip: changingConfigurations")
                return
            }
            val cd: Int = if (ADControl.cd_hot == null || ADControl.cd_hot!! <= 0){
                1000
            }else{
                ADControl.cd_hot!!*1000
            }
            cancelJob?.cancel()
            cancelJob = MyAPP.instance.applicationScope.launch{
                delay(cd.toLong().milliseconds)
                isBackstage = true
            }
            NotifyShow.showBackNF(NFEnum.NF_BACKGROUND, "onActivityStopped")
            SessionManager.registerBackListener()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

    }

    override fun onActivityDestroyed(activity: Activity) {
        stack.remove(activity)
        AppLogs.dLog(MyAPP.instance.TAG, "onActivityDestroyed() activity=" + activity + " stack.size=" + stack.size)
//        AppLogs.dLog("VideoListFragment", "onActivityDestroyed() activity=" + activity + " stack.size=" + stack.size)
    }


}
