package com.pdf.editor.common.nf
import android.app.job.JobParameters
import android.app.job.JobService
import com.pdf.editor.MyAPP
import com.pdf.editor.common.nf.NotifyShow.TAG
import com.pdf.editor.common.tools.AppLogs
import kotlinx.coroutines.*

class NFJobService : JobService() {

    // 为这个 JobService 创建一个可取消的 Scope
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    // 保存 params 以便 later 调用 jobFinished
    @Volatile
    private var currentParams: JobParameters? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        currentParams = params
        AppLogs.dLog(TAG, "NFJobService onStartJob")
        // 启动前台 Service（如果需要）
        NotifyShow.startForeground("JobService")

        // 返回 true 表示有后台工作在进行（稍后会调用 jobFinished）
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        AppLogs.dLog(TAG, "NFJobService onStopJob")
        // 系统要求停止当前 job：取消协程，清理状态
        serviceScope.cancel()
        // 返回 true 表示希望系统重新调度（按你需求改为 false）
        return true
    }

    override fun onDestroy() {
        AppLogs.dLog(TAG, "NFJobService onDestroy")
        // Service 销毁时清理 scope
        serviceScope.cancel()
        MyAPP.instance.applicationScope.launch(Dispatchers.Main) {
            delay(7000)
            if (NFControl.foregroundState != NFControl.ForegroundState.STARTED){
                AppLogs.dLog(TAG, "NFJobService onDestroy startJobService")
                NFControl.startJobService()
            }
        }
        super.onDestroy()
    }
}
