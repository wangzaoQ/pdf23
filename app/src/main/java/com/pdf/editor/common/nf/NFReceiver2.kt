package com.pdf.editor.common.nf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pdf.editor.MyAPP
import com.pdf.editor.common.tools.AppLogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class NFReceiver2: BroadcastReceiver() {

    var TAG = "NFReceiver2"

    override fun onReceive(context: Context?, intent: Intent?) {
        val action: String? = intent?.action
        AppLogs.dLog(NFControl.TAG, "NFReceiver2 onReceive action:$action")
        val appContext = context?.applicationContext ?: return

        when (action) {

            Intent.ACTION_USER_PRESENT -> {
                AppLogs.dLog(
                    NFControl.TAG,
                    "NFReceiver2 ACTION_USER_PRESENT "
                )
                NotifyShow.showNF(
                    NFEnum.NF_UNLOCK,
                    from = "unlock"
                )
            }
            Intent.ACTION_SCREEN_ON -> {
                AppLogs.dLog(
                    NFControl.TAG,
                    "NFReceiver2 ACTION_SCREEN_ON "
                )
                NotifyShow.showNF(
                    NFEnum.NF_SCREEN_ON,
                    from = "screen_on"
                )
            }

            Intent.ACTION_SCREEN_OFF -> {
                AppLogs.dLog(
                    NFControl.TAG,
                    "NFReceiver2 screen_off "
                )
//                NotifyShow.showNF(NFEnum.NF_SCREEN_OFF, from = "screen_off")
            }

            Intent.ACTION_POWER_CONNECTED -> {
                NotifyShow.showNF(NFEnum.NF_POWER_CONNECTED, from = "power_connected")
            }

            Intent.ACTION_POWER_DISCONNECTED -> {
                NotifyShow.showNF(NFEnum.NF_POWER_DISCONNECTED, from = "power_disconnected")
            }

            Intent.ACTION_CONFIGURATION_CHANGED -> {
                NotifyShow.showNF(NFEnum.NF_CONFIGURATION_CHANGED, from = "configuration_changed")
            }
//            Intent.ACTION_BATTERY_CHANGED -> {
//                NotifyShow.showNF(NFEnum.NF_BATTERY_CHANGED, from = "battery_changed")
//            }

            Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                val reason = intent?.getStringExtra("reason")
                AppLogs.dLog(NFControl.TAG, "CLOSE_SYSTEM_DIALOGS reason=$reason")
                when (reason) {
                    "homekey" -> NotifyShow.showBackNF(NFEnum.NF_HOME, "homekey")
                    // AOSP 使用 recentapps；其余为部分 ROM 的兼容值。
                    "recentapps", "recent_apps", "app_switch" ->
                        NotifyShow.showBackNF(NFEnum.NF_RECENT, "recentapps")
                }
            }

        }
    }
}
