package com.pdf.editor.common.nf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pdf.editor.common.nf.NotifyShow.currentNFJob
import com.pdf.editor.common.tools.AppLogs


class NFReceiver3: BroadcastReceiver() {

    var TAG = "NFReceiver3"

    override fun onReceive(context: Context?, intent: Intent?) {
        val action: String? = intent?.action
        AppLogs.dLog(NFControl.TAG, "NFReceiver3 onReceive action:$action")

        when (action) {
            NFControl.deleteAction -> {
                currentNFJob?.cancel()
            }
        }
    }
}
