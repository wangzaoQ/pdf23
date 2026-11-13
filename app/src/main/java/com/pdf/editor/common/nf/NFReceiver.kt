package com.pdf.editor.common.nf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pdf.editor.MyAPP
import com.pdf.editor.common.nf.NFControl.scheduleNextAlarm
import com.pdf.editor.common.tools.AppLogs


class NFReceiver: BroadcastReceiver() {

    var TAG = "NFReceiver"


    override fun onReceive(context: Context?, intent: Intent?) {
        val action: String? = intent?.action
        AppLogs.dLog(NFControl.TAG, "NFReceiver onReceive action:${action}")
        // 获取是否为补发标记
        when(action){
            NFControl.ACTION_ALARM_1 ->{
                NotifyShow.showNF(NFEnum.NF_TIMER_1, from = NFControl.ACTION_ALARM_1)
                // 2. 判断是否需要安排下一个闹钟
                // 如果是正常的闹钟触发（准点响的），则需要手动安排下一个 30 分钟。
                AppLogs.dLog(NFControl.TAG, "action:${NFControl.ACTION_ALARM_1} 准点闹钟执行完毕，安排下一个周期")
                scheduleNextAlarm(MyAPP.instance,NFControl.ACTION_ALARM_1)
            }
        }
    }
}
