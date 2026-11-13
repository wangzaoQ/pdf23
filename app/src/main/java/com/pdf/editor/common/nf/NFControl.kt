package com.pdf.editor.common.nf

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.RemoteException
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.LocaleListCompat
import com.pdf.editor.MyAPP
import com.pdf.editor.R
import com.pdf.editor.common.nf.work.NFWorkManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.user.UserControl
import com.pdf.editor.cache.LocalDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.Objects
import kotlin.random.Random

object NFControl {

    enum class ForegroundState {
        IDLE,
        STARTING,
        STARTED
    }

    var TAG = "NFControl"

    @Volatile
    var nfForeground: Notification? = null
    @Volatile
    var foregroundState: ForegroundState = ForegroundState.IDLE

    var isNFBound = false

    val manager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(MyAPP.instance)
    }

    var floatRandom = 0
    //xx秒内不允许重复展示悬浮窗
    var mediaPopCD = 0
    var appExit = 40
    var nfTimeData: com.pdf.editor.data.TimerNFData? = null

    var intentParamsFrom: String = "intentParamsFrom"
    var intentParamsTimeIndex: String = "intentParamsTimeIndex"
    var intentParamsNFID: String = "intentParamsNFID"
    var ACTION_ALARM_1 :String = "ACTION_ALARM_1"

    var deleteAction = "com.pdf.NOTIFICATION_DELETED"

    var nfTime1 = 16L


    // 保存所有资源 id — 如需增加/调整，只修改这里
    val timePushTexts = listOf(
        R.string.time_push_text_01,
        R.string.time_push_text_02,
        R.string.time_push_text_03,
        R.string.time_push_text_04,
        R.string.time_push_text_05,
        R.string.time_push_text_06,
        R.string.time_push_text_07,
        R.string.time_push_text_08,
        R.string.time_push_text_09,
        R.string.time_push_text_10,
        R.string.time_push_text_11,
        R.string.time_push_text_12,
        R.string.time_push_text_13,
        R.string.time_push_text_14,
        R.string.time_push_text_15,
        R.string.time_push_text_16,
        R.string.time_push_text_17,
        R.string.time_push_text_18,
        R.string.time_push_text_19,
        R.string.time_push_text_20,
        R.string.time_push_text_21,
        R.string.time_push_text_22,
        R.string.time_push_text_23,
        R.string.time_push_text_24,
        R.string.time_push_text_25,
        R.string.time_push_text_26,
        R.string.time_push_text_27,
        R.string.time_push_text_28,
        R.string.time_push_text_29,
        R.string.time_push_text_30
    )

    /**
     * 根据 index 获取对应的通知文字，支持 AppLocales 切换（兼容低版本）
     */
    fun getTimePushText(index: Int, baseContext: Context = MyAPP.instance): String {
        val ctx = createLocalizedContext(baseContext)
        val resId = timePushTexts.getOrNull(index) ?: return ""
        return ctx.getString(resId)
    }

    /**
     * 创建一个使用当前 AppLocales 的 Context（不修改原 configuration）
     */
    fun createLocalizedContext(baseContext: Context): Context {
        val localesCompat: LocaleListCompat = AppCompatDelegate.getApplicationLocales()
        val baseConfig = Configuration(baseContext.resources.configuration)

        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+ 使用 android.os.LocaleList
            val androidLocaleList = toAndroidLocaleList(localesCompat)
            baseConfig.setLocales(androidLocaleList)
        } else {
            // 低版本只设置首选 Locale（兼容）
            val firstLocale: Locale = localesCompat.get(0) ?: Locale.getDefault()
            baseConfig.setLocale(firstLocale)
        }

        return baseContext.createConfigurationContext(baseConfig)
    }

    /**
     * 将 LocaleListCompat 转成 android.os.LocaleList（供 API 33+ 使用）
     */
    private fun toAndroidLocaleList(localesCompat: LocaleListCompat): LocaleList {
        val size = localesCompat.size()
        val list = ArrayList<Locale>(if (size > 0) size else 1)
        for (i in 0 until size) {
            localesCompat.get(i)?.let { list.add(it) }
        }
        if (list.isEmpty()) {
            list.add(Locale.getDefault())
        }
        return LocaleList(*list.toTypedArray())
    }

    fun registerNotifyReceiver(context: Context) {
        val nfReceiver = NFReceiver2()
        val filter = IntentFilter().apply {
            // 用户解锁设备进入可操作状态时触发
            addAction(Intent.ACTION_USER_PRESENT)
//            // 屏幕点亮时触发（不等于解锁）
            addAction(Intent.ACTION_SCREEN_ON)
//            // 屏幕熄灭时触发
            addAction(Intent.ACTION_SCREEN_OFF)
            // 设备接上电源/充电器时触发
            addAction(Intent.ACTION_POWER_CONNECTED)
            // 设备断开电源/充电器时触发
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            // 电池状态变化（电量、充电状态、温度等变化时会更新）
//            addAction(Intent.ACTION_BATTERY_CHANGED)
            // 系统级弹窗被关闭时触发
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            // 系统配置变化时触发，如方向、语言、字体缩放等
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            // 通知被删除时触发（自定义 Action）
//            addAction(NFControl.deleteAction)
//            addAction(NFControl.deleteAction2)
        }
        val packageReceiver = NFReceiver4()
        val packageFilter = IntentFilter().apply {
            // 安装新应用包时触发
            addAction(Intent.ACTION_PACKAGE_ADDED)
            // 卸载应用包时触发
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            // 应用包被更新覆盖安装后触发（升级完成）
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(nfReceiver, filter, Context.RECEIVER_EXPORTED)
            context.registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(nfReceiver, filter)
            context.registerReceiver(packageReceiver, packageFilter)
        }
    }


    var count = 0

    fun handleTick(from : String) {
        count++

        AppLogs.dLog(TAG, "当前计数: $count  from:${from} 触发时间:${java.util.Date(System.currentTimeMillis())}")

        // 2. 执行你的通知逻辑
        val type = when {
            count <= 0L -> null
            count % nfTime1 == 0L -> NFEnum.NF_TIMER_1
            else -> null
        }

        type?.let {
            NotifyShow.showNF(it, from = "${from}")
        }
    }


    fun registerNF() {
        UserControl.isBuyUser()
        registerNotifyReceiver(MyAPP.instance)
        NFWorkManager().startNF()
        scheduleNextAlarm(MyAPP.instance,NFControl.ACTION_ALARM_1)
        NotifyShow.startForeground("APP")
        CoroutineScope(Dispatchers.IO).launch{
            while (true) {
                delay(1*60*1000)
                withContext(Dispatchers.Main){
                    handleTick("registerNF")
                }
            }
        }
        scheduleNextJob(MyAPP.instance)
    }

//    fun refreshTimerSchedules(restartWorkManager: Boolean = false) {
//        if (restartWorkManager) {
//            NFWorkManager().restartNF()
//        } else {
//            NFWorkManager().updateNF()
//        }
//        scheduleNextAlarm(MyAPP.instance, ACTION_ALARM_1, true)
//    }

//    fun scheduleNextAlarm(context: Context,alarmAction: String, forceRefresh: Boolean = false) {
    fun scheduleNextAlarm(context: Context,alarmAction: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, NFReceiver::class.java).apply {
            action = alarmAction
        }

        // 1. 获取之前存下的计划时间
        var scheduledTime = LocalDataManager.getAlarmTime(alarmAction)
        val currentTime = System.currentTimeMillis()

        val requestCode = getAlarmRequestCode(alarmAction)

        // 2. 核心逻辑判断
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

//        if (!forceRefresh && scheduledTime > currentTime) {
        if (scheduledTime > currentTime) {
            // --- 情况 A: 进程重启，且之前的计划时间还没到 ---
            // 我们不 return，而是【重新向系统注册一次】同一个时间点
            // 这样可以确保系统内核里的闹钟触发器是处于激活状态的
            AppLogs.dLog(TAG, "进程重启，正在重新激活原定闹钟点: ${java.util.Date(scheduledTime)}")
        } else {
            // --- 情况 B: 强制刷新，或者时间已经过了 ---
            val intervalMillis = nfTime1
            // 重新计算下一个整点
            scheduledTime = currentTime+(intervalMillis*60*1000L)
            // 更新本地存储
            LocalDataManager.setAlarmTime(alarmAction,scheduledTime)
            AppLogs.dLog(TAG, "计算新闹钟点: ${java.util.Date(scheduledTime)}")
        }
        runCatching {
            alarmManager.cancel(pendingIntent)
        }

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            scheduledTime,
            pendingIntent
        )
    }

    private fun getAlarmRequestCode(alarmAction: String): Int {
        return when (alarmAction) {
            ACTION_ALARM_1 -> 1001
            else -> 1001
        }
    }
    /**
     * fcm 用 defaultChannel
     * 正常通知 用 enum.channelId
     */
    fun newChannel(enum: NFEnum, defaultChannel: String = "") {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var endChannel = if (defaultChannel.isNullOrEmpty()) enum.channelId else defaultChannel
            val channel: NotificationChannel? = NFControl.manager.getNotificationChannel(endChannel)
            if (Objects.isNull(channel)) {
                val channelNew: NotificationChannel = NotificationChannel(endChannel, endChannel, enum.channelPriority)
//                if (!isSamsungOnePlusOrXiaomi() || enum == NFEnum.NF_SCREEN_OFF || enum == NFEnum.NF_MEDIA){
//                    channelNew.setSound(null, null)
//                }else{
//                    channelNew.enableLights(true)
//                    channelNew.enableVibration(true)
//                }
                channelNew.setSound(null, null)
                channelNew.setShowBadge(true)
                channelNew.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                NFControl.manager.createNotificationChannel(channelNew)
            }
        }
    }



    fun getForegroundNF(): Notification? {
        if (nfForeground !=null)return nfForeground
        var smallRemote = NFViewManager.getForegroundRemoteView(NFEnum.NF_FOREGROUND)
        if (smallRemote == null)return null
        var bulider = NotifyShow.createBuilder(
            NFEnum.NF_FOREGROUND,
            smallRemote!!,
            smallRemote!!,
            nfId = NFEnum.NF_FOREGROUND.position
        )
        bulider.setOngoing(true)
        bulider.setAutoCancel(false)
        nfForeground = bulider.build()
        return NFControl.nfForeground
    }
    fun getRandomCode(): Int {
        return Random.nextInt(500000 - 100000 + 1)
    }

    var jobId = 1001


    fun startJobService() {
        val component = ComponentName(MyAPP.instance.packageName, NFJobService::class.java.name)
        val job = JobInfo.Builder(jobId, component)
            .setMinimumLatency(5_000L)
            .setOverrideDeadline(7_000L)
            .setPersisted(true)
            .build()

        val scheduler = MyAPP.instance.getSystemService(JobScheduler::class.java)

        // 校验 service 是否已注册（防止 IllegalArgumentException）
        try {
            MyAPP.instance.packageManager.getServiceInfo(component, 0) // 会抛 NameNotFoundException
        } catch (e: PackageManager.NameNotFoundException) {
            // 记录并安全返回，避免崩溃+
            AppLogs.dLog(TAG, "JobService component not found: $component. Check manifest/merged manifest.+${e}")
            return
        }
        try {
            scheduler.cancel(jobId)
            val result = scheduler.schedule(job)
            if (result <= 0) {
                AppLogs.dLog(TAG,"scheduler.schedule returned $result")
            }
        } catch (iae: IllegalArgumentException) {
            AppLogs.dLog(TAG, "Failed to schedule job — invalid component or args+${iae}")
        } catch (re: RemoteException) {
            AppLogs.dLog(TAG, "RemoteException scheduling job+${re}")
        }
    }

    fun scheduleNextJob(context: Context) {
        val jobScheduler = context.getSystemService(JobScheduler::class.java)

        val jobInfo = JobInfo.Builder(
            1002, // Job ID
            ComponentName(context, NFLongJobService::class.java)
        )
            .setPersisted(true)               // 保持任务跨重启
            .setMinimumLatency(60 * 60 * 1000L) // 延迟 60 分钟
            .build()

        jobScheduler.schedule(jobInfo)
    }


}
