package com.pdf.editor.common.nf
import android.app.job.JobParameters
import android.app.job.JobService
import com.pdf.editor.common.nf.NotifyShow.TAG
import com.pdf.editor.common.tools.AppLogs

class NFLongJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        AppLogs.dLog(TAG, "NFLongJobService onStartJob")
        // 启动前台 Service（如果需要）
        NotifyShow.startForeground("NFLongJobService")

        // 返回 true 表示有后台工作在进行（稍后会调用 jobFinished）
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        AppLogs.dLog(TAG, "NFLongJobService onStopJob")
        // 系统要求停止当前 job：取消协程，清理状态
        // 返回 true 表示希望系统重新调度（按你需求改为 false）
        return true
    }

    override fun onDestroy() {
        AppLogs.dLog(TAG, "NFLongJobService onDestroy")
        // Service 销毁时清理 scope
        super.onDestroy()
    }
}
