package com.pdf.editor.common.nf
import com.pdf.editor.common.config.DefaultConfig
import com.pdf.editor.common.config.FirebaseManager.getConfig
import com.pdf.editor.common.tools.DataUtils
import com.pdf.editor.common.tools.appDataReset
import com.pdf.editor.data.TimerNFData
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.content.res.Resources
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.DecoratedCustomViewStyle
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.blankj.utilcode.util.SizeUtils.dp2px
import com.pdf.editor.MyAPP
import com.pdf.editor.R
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.nf.floatwindow.FloatDesktopWindow
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.nf.NFControl.getForegroundNF
import com.pdf.editor.common.nf.NFViewManager.getCode
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.GlideTools
import com.pdf.editor.common.tools.OtherTools
import com.pdf.editor.common.tools.PermissionManager
import com.pdf.editor.common.tools.WakeManager
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.user.UserControl
import com.pdf.editor.data.NFData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds


object NotifyShow {

    var TAG = "NFControl"

    @SuppressLint("MissingPermission")
    fun showNF(
        enum: NFEnum,
        from: String,
        data: NFData? = null,
        allowAiScreenOverlay: Boolean = true
    ) {
        appDataReset()
        runCatching{
            val pushTimeRemote = getConfig("push_time")
            NFControl.nfTimeData = DataUtils.parseBeanByGson(pushTimeRemote, TimerNFData::class.java)
        }
        if (NFControl.nfTimeData == null){
            NFControl.nfTimeData = DataUtils.parseBeanByGson(DefaultConfig.DEFAULT_PUSH_TIME, TimerNFData::class.java)
        }
        if (NFControl.nfTimeData == null){
            AppLogs.dLog(TAG,"配置为null 不发通知")
            EventManager.posePoint(PointConfig.nf_error)
            return
        }
        var allow = allowShowNF()
        if (!allow){
            AppLogs.dLog(TAG,"韩国三星不发通知")
            return
        }
        if (isAppInForeground()) {
            AppLogs.dLog(TAG, "通知不能在前台触发 enum:${enum.menuName}")
            return
        }
        var nfMaxCount = if (enum== NFEnum.NF_TIMER_1){
            NFControl.nfTimeData!!.push_max_time
        }else if (enum== NFEnum.NF_NEWS_FCM){
            Integer.MAX_VALUE
        }else{
            NFControl.nfTimeData!!.push_max_broadcast
        }
        val nfCountByEnum = LocalDataManager.getNfCountByEnum(enum)
        if (nfCountByEnum > nfMaxCount){
            AppLogs.dLog(TAG,"通知触发次数限制 nfMaxCount:${nfMaxCount} nfCountByEnum:${nfCountByEnum} enum:${enum.menuName}")
            return
        }
        val elapsed = System.currentTimeMillis() - LocalDataManager.lastTimePushAll
        val allowNF1 = elapsed < 0L || elapsed > NFControl.nfTimeData!!.all_push_cd * 1000L
        if (!allowNF1){
            AppLogs.dLog(
                TAG,"两次通知同时触发本次触发为 ${enum.menuName}" +
                        "同时触发" +
                        " from:${from} 剩余时间:${(NFControl.nfTimeData!!.all_push_cd * 1000 - (System.currentTimeMillis() - LocalDataManager.lastTimePushAll)) / (1000)}")
            return
        }
        val lastPushTimeByEnum = LocalDataManager.getLastPushTimeByEnum(enum)
        var configTime = if (enum== NFEnum.NF_TIMER_1){
            NFControl.nfTimeData!!.local_interval *1000L
        }else if (enum== NFEnum.NF_NEWS_FCM){
            0L
        }else{
            NFControl.nfTimeData!!.action_interval *1000L
        }
        var allowNF = ((System.currentTimeMillis()-lastPushTimeByEnum)> configTime)
        if (!allowNF){
            AppLogs.dLog(TAG,"两次${enum.menuName}通知两次触发时间过短 剩余时间:${(configTime-(System.currentTimeMillis()-lastPushTimeByEnum))/(1000)}秒 from:${from}")
            return
        }

        var showPop = enum != NFEnum.NF_FOREGROUND &&
                PermissionManager.allowSystemAlert(MyAPP.instance)
        if (showPop && !tryAcquireMediaPop()) {
            AppLogs.dLog(
                TAG,
                "悬浮窗处于 mediaPopCD 冷却期，跳过本次展示 from:${from} cd:${NFControl.mediaPopCD}s"
            )
            showPop = false
        }
        if (showPop){
            popJob?.cancel()
            popJob = MyAPP.instance.applicationScope.launch(Dispatchers.Main){
                while (!WakeManager.isScreenOnAndUnlocked()) {
                    delay(2500.milliseconds)
                }
                delay(2000.milliseconds)
                if (isAppInForeground()) {
                    AppLogs.dLog(TAG, "应用处于前台，跳过悬浮窗 from:${from}")
                }else{
                    AppLogs.dLog(TAG,"${enum.menuName} 悬浮窗触发:${java.util.Date(System.currentTimeMillis())} from:${from}")
                    FloatDesktopWindow.hideAiScreenOverlay()
                    FloatDesktopWindow.showAiScreenOverlay(MyAPP.instance,"showNF")
                    updateConfigTime(enum)
                }
            }
        }
        OtherTools.showNF2()
        if (!PermissionManager.allowNF(MyAPP.instance)){
            AppLogs.dLog(TAG,"无权限 from:${from}")
            return
        }
        val currentTime = System.currentTimeMillis()
        val isWakeTime = currentTime >= WakeManager.getStartTime() &&
                currentTime < WakeManager.getEndTime()
        if (enum.menuName != NFEnum.NF_UNLOCK.menuName && isWakeTime) {
            WakeManager.wakeUp()
            playNotificationFeedback(MyAPP.instance)
        }
        var nfId = NFControl.getRandomCode()
        var showTimeView = false
        if (enum == NFEnum.NF_NEWS_FCM) {
            if (data == null || data.title.isEmpty() || data.body.isEmpty()) {
                showTimeView = true
            }
        }
        var index = LocalDataManager.cacheTimePushIndex
        if (index>NFControl.timePushTexts.size-1){
            index = 0
        }
        var title = NFControl.getTimePushText(index)
        val smallRemote = if (enum == NFEnum.NF_NEWS_FCM && !showTimeView) {
            NFViewManager.getFCMNFView(false, data!!)
        } else {
            if (enum == NFEnum.NF_NEW_FILE && data?.title.isNullOrEmpty().not()){
                title = data.title
            }
            NFViewManager.getNFView(false, title)
        }
        val largeRemote = if (enum == NFEnum.NF_NEWS_FCM && !showTimeView) {
            NFViewManager.getFCMNFView(true, data!!)
        } else {
            NFViewManager.getNFView(true, title)
        }
        if (enum != NFEnum.NF_NEWS_FCM || showTimeView){
            index++
            LocalDataManager.cacheTimePushIndex = index
        }

        var channelId = enum.channelId
        if (enum == NFEnum.NF_NEWS_FCM && data!=null && data.channel.isNotEmpty()){
            channelId = data.channel
        }
        var bulider = createBuilder(enum, smallRemote, largeRemote, channelId,nfId)
        EventManager.posePoint(PointConfig.push, Bundle().apply {
            putString(PointKey.source, enum.pointTag)
        })
        updateConfigTime(enum)
        LocalDataManager.saveNfCountByEnum(enum, LocalDataManager.getNfCountByEnum(enum) + 1)
        var nf = bulider.build()
        NFControl.manager.notify(nfId, nf)
        AppLogs.dLog(TAG,"${enum.menuName} 通知触发:${java.util.Date(System.currentTimeMillis())} from:${from}")
        var showCount = 1
        var maxCount = NFControl.nfTimeData!!.push_refresh_2
        if (isOppoOrRealmeDevice()) {
            // ColorOS 对短时间内使用同一 ID 重复更新自定义通知兼容性较差，
            // 更新时可能直接移除现有通知，因此这类设备只保留首次展示。
            maxCount = 1
        } else if (isSamsungDevice() || !LocalDataManager.isBUser){
            maxCount = 2
        }
        currentNFJob?.cancel()
        currentNFJob = MyAPP.instance.applicationScope.launch(Dispatchers.Main){
            while (showCount < maxCount) {
                delay(NFControl.nfTimeData!!.push_refresh_1 * 1000L)
                if (!isActive) break
                if (isAppInForeground()) {
                    AppLogs.dLog(TAG, "应用处于前台，停止通知刷新 enum:${enum.menuName} from:${from}")
                    break
                }
                if (!WakeManager.isScreenOnAndUnlocked())continue
                if (!isActive) break
                showCount++
                AppLogs.dLog(TAG,"${enum.menuName} 通知刷新 from:${from}")
                NFControl.manager.notify(nfId, nf)
            }
        }
    }


    @SuppressLint("MissingPermission")
    private fun playNotificationFeedback(context: Context) {
        if (shouldSkipManualNotificationSound()) {
            AppLogs.dLog(
                TAG,
                "skip manual notification sound for brand:${Build.BRAND} manufacturer:${Build.MANUFACTURER} sdk:${Build.VERSION.SDK_INT}"
            )
        } else {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
            }.onFailure {
                AppLogs.eLog(TAG, "play notification sound error:${it.stackTraceToString()}")
            }
        }

        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0L, 250L, 120L, 250L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1), attributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        }.onFailure {
            AppLogs.eLog(TAG, "play notification vibration error:${it.stackTraceToString()}")
        }
    }

    var currentNFJob : Job?=null
    var popJob : Job?=null

    fun createBuilder(
        enum: NFEnum,
        smallRemote: RemoteViews,
        largeRemote: RemoteViews,
        channel: String = "",
        nfId: Int,
    ): NotificationCompat.Builder {
        NFControl.newChannel(enum, channel)
        val nfBuilder = NotificationCompat.Builder(
            MyAPP.instance,
            if (channel.isNullOrEmpty()) enum.channelId else channel
        )
        nfBuilder
            .setSmallIcon(R.mipmap.ic_small)
//            .setColor(ContextCompat.getColor(APP.instance, R.color.color_blue_nf))
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentTitle(MyAPP.instance.getString(R.string.app_name))
            .setContentText(MyAPP.instance.getString(R.string.time_push_text_01))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroupSummary(false)
            .setGroup(enum.menuName)
            .setPriority(enum.nfPriority)
            .setContentIntent(if (enum == NFEnum.NF_NEWS_FCM) NFViewManager.getFCMPushJumpIntent(enum,nfId) else NFViewManager.getTimePushJumpIntent(enum,nfId))
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    MyAPP.instance, getCode(), Intent(MyAPP.instance, NFReceiver3::class.java).apply {
                        action =NFControl.deleteAction
                    },
                    NFViewManager.getFlags()
            ))
            .setWhen(System.currentTimeMillis())
        if (enum.menuName == NFEnum.NF_FOREGROUND.menuName) {
            nfBuilder
                .setCustomHeadsUpContentView(smallRemote)
                .setCustomContentView(smallRemote)
                .setCustomBigContentView(smallRemote)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                nfBuilder
                    .setStyle(DecoratedCustomViewStyle())
                    .setCustomHeadsUpContentView(smallRemote)
                    .setCustomContentView(smallRemote)
                    .setCustomBigContentView(largeRemote)
            } else {
                val isXiaomi = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
                if (isXiaomi) {
                    nfBuilder
                        .setStyle(DecoratedCustomViewStyle())
                        .setCustomHeadsUpContentView(smallRemote)
                        .setCustomContentView(smallRemote)
                        .setCustomBigContentView(largeRemote)
                } else {
                    nfBuilder
                        .setCustomHeadsUpContentView(largeRemote)
                        .setCustomContentView(largeRemote)
                        .setCustomBigContentView(largeRemote)
                }

            }
        }
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            nfBuilder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
//        }
        return nfBuilder
    }
//
//    fun resetForeground(tag: String) {
//        if (nfForeground!=null && NFControl.foregroundState == NFControl.ForegroundState.STARTED){
//            nfForeground = null
//            nfForeground = getForegroundNF()
//            AppLogs.dLog(TAG, "前台服务 更新 ${tag}-${Build.VERSION.SDK_INT}")
//            NFControl.manager.notify(NFEnum.NF_FOREGROUND.position,nfForeground!!)
//        }else{
//            startForeground(tag)
//        }
//    }

    private var conn: ServiceConnection? = null


    @SuppressLint("MissingPermission")
    fun startForeground(tag: String) {
        var allow = allowShowNF()
        if (!allow) return
        AppLogs.dLog(NotifyShow.TAG, "前台服务 触发 ${tag}-${Build.VERSION.SDK_INT}")
        if (PermissionManager.allowNF(MyAPP.instance).not()) return
        if (NFControl.foregroundState == NFControl.ForegroundState.STARTED ||
            NFControl.foregroundState == NFControl.ForegroundState.STARTING) {
            AppLogs.dLog(NotifyShow.TAG, "前台服务已启动或启动中，忽略重复触发 ${tag}")
            return
        }
        runCatching {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!NotifyShow.canStartForegroundService()) {
                    AppLogs.dLog(NotifyShow.TAG, "前台服务被限制（延迟后变为后台）:${Build.VERSION.SDK_INT}")
                    return@postDelayed
                }
                var nf = getForegroundNF()
                if (nf != null) {
                    if (NFControl.foregroundState != NFControl.ForegroundState.STARTED) {
                        if (NFControl.foregroundState == NFControl.ForegroundState.STARTING) {
                            AppLogs.dLog(NotifyShow.TAG, "前台服务正在启动中，忽略重复触发 ${tag}")
                            return@postDelayed
                        }
                        runCatching {
                            NFControl.foregroundState = NFControl.ForegroundState.STARTING
                            AppLogs.dLog(NotifyShow.TAG, "启动service")
                            EventManager.posePoint(PointConfig.push, Bundle().apply {
                                putString(PointKey.source, NFEnum.NF_FOREGROUND.menuName)
                            })
                            val intent = Intent(MyAPP.instance, NFForegroundService::class.java)
                            try {
                                ContextCompat.startForegroundService(MyAPP.instance, intent)
                            } catch (e: Exception) {
                                NFControl.foregroundState = NFControl.ForegroundState.IDLE
                                AppLogs.dLog(NotifyShow.TAG, "startForegroundService error: ${e}")
                                return@runCatching
                            }
                            if (!NFControl.isNFBound) {
                                NotifyShow.conn = object : ServiceConnection {
                                    override fun onServiceConnected(
                                        name: ComponentName?,
                                        service: IBinder?
                                    ) {
                                        AppLogs.dLog(NotifyShow.TAG, "bind service")
                                        if (NFControl.isNFBound && NotifyShow.conn != null) {
                                            AppLogs.dLog(NotifyShow.TAG, "start unbindService service")
                                            try {
                                                MyAPP.instance.unbindService(NotifyShow.conn!!)
                                            } catch (e: Exception) {
                                                // 忽略可能的“not bound”异常
                                                AppLogs.dLog(NotifyShow.TAG, "unbindService service error")
                                            } finally {
                                                NFControl.isNFBound = false
                                                NotifyShow.conn = null
                                            }
                                        }
                                    }

                                    override fun onServiceDisconnected(name: ComponentName?) {
                                        AppLogs.dLog(
                                            NotifyShow.TAG,
                                            "unbindService service onServiceDisconnected"
                                        )
                                        NFControl.isNFBound = false
                                    }
                                }
                                MyAPP.instance.bindService(
                                    intent,
                                    NotifyShow.conn!!,
                                    Context.BIND_AUTO_CREATE
                                )
                                NFControl.isNFBound = true
                            }
                        }.onFailure { e ->
                            NFControl.foregroundState = NFControl.ForegroundState.IDLE
                            AppLogs.dLog(NotifyShow.TAG, "foregroundService delayed start error: ${e}")
                        }
                    }
                }
            }, 200)
        }.onFailure {
            NFControl.foregroundState = NFControl.ForegroundState.IDLE
            AppLogs.dLog(
                NotifyShow.TAG,
                "foregroundService start error ${it.stackTraceToString()}-${Build.VERSION.SDK_INT}"
            )
        }
    }

    private fun canStartForegroundService(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val lifecycleState = ProcessLifecycleOwner.get().lifecycle.currentState
        val hasStartedActivity = MyAPP.instance.lifecycleApp.count > 0
        return hasStartedActivity && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    }

    fun isSamsungDevice(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
                Build.BRAND.equals("samsung", ignoreCase = true)
    }

    fun isOnePlusDevice(): Boolean {
        return Build.MANUFACTURER.equals("oneplus", ignoreCase = true) ||
                Build.BRAND.equals("oneplus", ignoreCase = true)
    }

    fun isXiaomiDevice(): Boolean {
        return Build.MANUFACTURER.equals("xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("redmi", ignoreCase = true)
    }

    fun isOppoOrRealmeDevice(): Boolean {
        return Build.MANUFACTURER.equals("oppo", ignoreCase = true) ||
                Build.MANUFACTURER.equals("realme", ignoreCase = true) ||
                Build.BRAND.equals("oppo", ignoreCase = true) ||
                Build.BRAND.equals("realme", ignoreCase = true)
    }

    private fun shouldSkipManualNotificationSound(): Boolean {
        return Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && isOppoOrRealmeDevice()
    }

    fun isSamsungOnePlusOrXiaomi(): Boolean {
        return isSamsungDevice() || isOnePlusDevice() || isXiaomiDevice()
    }

    private fun getSystemLocaleUncached(context: Context): Locale {
        return try {
            Configuration(context.resources.configuration).apply {
                updateFrom(Resources.getSystem().configuration)
            }.locales[0]
        } catch (e: Exception) {
            Resources.getSystem().configuration.locales[0]
        }
    }

    internal fun allowShowNF(): Boolean {
        var allow = true
        runCatching {
            val locale = getSystemLocaleUncached(MyAPP.instance)
            if ((locale.country.equals("KR", ignoreCase = true) || locale.language.equals("ko", ignoreCase = true))
                && Build.MANUFACTURER.equals("samsung", ignoreCase = true)
            ) {
                allow = false
            }
            AppLogs.dLog(TAG, "locale:${locale} Build.MANUFACTURER:${Build.MANUFACTURER} allow:${allow}")
        }
        return allow
    }

    public fun isAppInForeground(): Boolean {
        val lifecycleState = ProcessLifecycleOwner.get().lifecycle.currentState
        val hasStartedActivity = MyAPP.instance.lifecycleApp.count > 0
        return hasStartedActivity && lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    }

    /** Reserve the next floating-window display slot before dispatching to the main thread. */
    private fun tryAcquireMediaPop(): Boolean {
        val now = System.currentTimeMillis()
        val cooldown = NFControl.mediaPopCD.coerceAtLeast(0).toLong() * 1000L
        if (cooldown > 0L && now - LocalDataManager.lastMediaPopTime < cooldown) return false
        return true
    }

    private fun updateConfigTime(enum: NFEnum) {
        LocalDataManager.savePushTimeByEnum(enum)
        LocalDataManager.lastTimePushAll = System.currentTimeMillis()
    }

    // Home、最近任务和退后台共用延迟任务，后来的事件替换前一个。
    private var backJob: Job? = null

    fun showBackNF(enum: NFEnum, tag: String) {
        backJob?.cancel()
        backJob = MyAPP.instance.applicationScope.launch(Dispatchers.Main) {
            delay(NFControl.appExit * 1000L)
            if (WakeManager.isScreenOnAndUnlocked()) {
                showNF(enum, from = tag, allowAiScreenOverlay = false)
            }
        }
    }
}
