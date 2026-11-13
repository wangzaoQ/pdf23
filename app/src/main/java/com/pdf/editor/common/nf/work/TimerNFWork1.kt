package com.pdf.editor.common.nf.work

import android.content.Context
import androidx.work.WorkerParameters
import com.pdf.editor.common.nf.NFEnum
import com.pdf.editor.common.nf.NotifyShow


class TimerNFWork1(appContext: Context, params: WorkerParameters) : BaseNotifyWork(appContext, params){
    override suspend fun doWork(): Result {
        NotifyShow.showNF(NFEnum.NF_TIMER_1,from ="work")
        NotifyShow.startForeground(tag = "NFWork")
        return Result.success()
    }
}
