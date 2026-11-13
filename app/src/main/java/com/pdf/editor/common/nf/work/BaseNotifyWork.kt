package com.pdf.editor.common.nf.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters

abstract class BaseNotifyWork(val appContext: Context, val params: WorkerParameters) : CoroutineWorker(appContext, params) {

    var TAG = "Work"

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return super.getForegroundInfo()
    }

}
