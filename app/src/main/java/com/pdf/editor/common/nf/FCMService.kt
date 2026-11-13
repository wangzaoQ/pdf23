package com.pdf.editor.common.nf

import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.pdf.editor.common.config.FirebaseManager
import com.pdf.editor.common.tools.PermissionManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.DataUtils.toJson
import com.pdf.editor.data.NFData
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class FCMService : FirebaseMessagingService() {
    val TAG: String = "FCMService:"

    /**
     *     {
     *         "channel_id": "ch_a",
     *         "NEWS_ID": "8993843577094145",
     *         "tag": "tg_a",
     *         "body": "33.A driver was brutally attacked by a large group of people after having his vehicle damaged in downtown Los Angeles.",
     *         "image": "https://clevertap.com/wp-content/uploads/2021/05/Push-Notification-Header.png?w\u003d1024",
     *         "title": "33-ck_a-ch_a-lbl_a-tg_a",
     *         "KEY_NOW_NAV_TYPE": "3"
     *     }
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        AppLogs.dLog(TAG,"onMessageReceived:${toJson(message)}")
        val map: Map<String, String> = message.data
        AppLogs.dLog(TAG,"onMessageReceived message.data:${toJson(map)}")
        startForegroundForHighPriorityMessage(message)
        showFCM(map)
    }

    private fun startForegroundForHighPriorityMessage(message: RemoteMessage) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            message.priority != RemoteMessage.PRIORITY_HIGH ||
            !FirebaseManager.isFcmForegroundServiceEnabled() ||
            !PermissionManager.allowNF(this) ||
            NFControl.foregroundState != NFControl.ForegroundState.IDLE
            || !NotifyShow.allowShowNF()
        ) return

        if (NFControl.getForegroundNF() == null) {
            AppLogs.dLog(TAG, "high priority FCM foreground notification unavailable")
            return
        }
        if (NFControl.foregroundState != NFControl.ForegroundState.IDLE) return

        try {
            NFControl.foregroundState = NFControl.ForegroundState.STARTING
            ContextCompat.startForegroundService(this, Intent(this, NFForegroundService::class.java))
            AppLogs.dLog(TAG, "high priority FCM started foreground service")
        } catch (e: Exception) {
            NFControl.foregroundState = NFControl.ForegroundState.IDLE
            AppLogs.dLog(TAG, "high priority FCM foreground start rejected: ${e.stackTraceToString()}")
        }
    }

    fun showFCM(map: Map<String, String>) {
        var chanel = map.get("channel_id")?:""
        var body = map.get("body")?:""
        var image = map.get("image")?:""
        var title = map.get("title")?:""
        var url = map.get("news_url")?:""
        if (chanel.isNullOrEmpty()){
            chanel = NFEnum.NF_NEWS_FCM.channelId
        }
        var data = NFData().apply {
            this.title = title
            this.image = image
            this.url = url
            this.body = body
            this.channel = chanel
        }
        NotifyShow.showNF(NFEnum.NF_NEWS_FCM, from = "FCM",data)
    }

    override fun onNewToken(token: String) {
        AppLogs.dLog(TAG, "onNewToken =$token")
        super.onNewToken(token)
    }

}
