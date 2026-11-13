package com.pdf.editor.common.nf

import android.annotation.SuppressLint
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.pdf.editor.MyAPP
import com.pdf.editor.R
import com.pdf.editor.common.tools.AppLogs
import com.hjq.permissions.tools.PermissionVersion.isAndroid14

class NFForegroundService : Service() {
    var TAG = "NFControl"

    override fun onCreate() {
        super.onCreate()
        showNF()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showNF()
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun showNF() {
        if (NFControl.foregroundState == NFControl.ForegroundState.STARTED) return
        // ① 先做占位，防止外部并发调用
        NFControl.foregroundState = NFControl.ForegroundState.STARTED
        val useTempNf = NFControl.nfForeground == null
        val nf: Notification = if (useTempNf) createNotification() else NFControl.getForegroundNF() ?: createNotification()
        try {
            if (isAndroid14()) {
                startForeground(
                    NFEnum.NF_FOREGROUND.position,
                    nf,
                    FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NFEnum.NF_FOREGROUND.position, nf)
            }

            AppLogs.dLog(TAG, "Foreground service started OK")
            if (useTempNf) {
                NFControl.getForegroundNF()?.let {
                    NFControl.manager.notify(NFEnum.NF_FOREGROUND.position, it)
                }
            }

        } catch (e: Exception) {
            // ③ 出现异常时立刻回滚状态，防止假阳性
            NFControl.foregroundState = NFControl.ForegroundState.IDLE
            AppLogs.dLog(TAG, "startForeground failed: ${e.stackTraceToString()}")

            stopSelf() // 必要，防止挂起服务卡住 system_server
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        return dummyBinder
    }

    override fun onDestroy() {
        NFControl.foregroundState = NFControl.ForegroundState.IDLE
        AppLogs.dLog(NFControl.TAG,"NFForegroundService onDestroy")
        NFControl.startJobService()
        super.onDestroy()
    }

    private val dummyBinder = object : Binder() {}

    fun createNotification(): Notification {
        NFControl.newChannel(NFEnum.NF_FOREGROUND)
        return NotificationCompat.Builder(
            MyAPP.instance, NFEnum.NF_FOREGROUND.channelId
        )
            .setSmallIcon(R.mipmap.ic_small)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setSound(null)
            .setAutoCancel(false)
            .setContentTitle(MyAPP.instance.getString(R.string.app_name))
            .setContentText(MyAPP.instance.getString(R.string.app_text_pdf_reader))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setGroupSummary(false)
            .setGroup(NFEnum.NF_FOREGROUND.menuName)
            .setPriority(NFEnum.NF_FOREGROUND.nfPriority)
            .setWhen(System.currentTimeMillis())
            .build()
    }

}
