package com.pdf.editor.common.nf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pdf.editor.common.tools.AppLogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class NFReceiver4: BroadcastReceiver() {

    var TAG = "NFReceiver4"

    override fun onReceive(context: Context?, intent: Intent?) {
        val action: String? = intent?.action
        AppLogs.dLog(NFControl.TAG, "NFReceiver4 onReceive action:$action")
        val appContext = context?.applicationContext ?: return

        when (action) {

            Intent.ACTION_PACKAGE_ADDED -> {
                NotifyShow.showNF(NFEnum.NF_PACKAGE_ADDED, from = "package_added")
            }

            Intent.ACTION_PACKAGE_REMOVED -> {
                NotifyShow.showNF(NFEnum.NF_PACKAGE_REMOVED, from = "package_removed")
            }

            Intent.ACTION_PACKAGE_REPLACED -> {
                NotifyShow.showNF(NFEnum.NF_PACKAGE_REPLACED, from = "package_replaced")
            }

        }
    }
}
