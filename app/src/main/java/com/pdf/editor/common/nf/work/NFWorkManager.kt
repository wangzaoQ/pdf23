package com.pdf.editor.common.nf.work
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.pdf.editor.MyAPP
import com.pdf.editor.common.config.FirebaseManager.getConfig
import com.pdf.editor.common.nf.NFControl
import com.pdf.editor.common.nf.NFEnum
import java.util.concurrent.TimeUnit


class NFWorkManager {

    fun startNF(){
        start(TimerNFWork1::class.java, NFEnum.NF_TIMER_1.menuName, NFControl.nfTime1)
    }

    fun start(clazz: Class<out BaseNotifyWork>, workTag:String, time:Long) {
        val constraints: Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workUniqueName ="${workTag}"
        val inputData = Data.Builder()
            .putString("workName", workTag)
            .build()
        val workRequest =
            PeriodicWorkRequest.Builder(clazz, time, TimeUnit.MINUTES)
                //设置重试退避策略
                .setBackoffCriteria(
                    BackoffPolicy.LINEAR,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .setConstraints(constraints) //设置触发条件
                .setInitialDelay(time, TimeUnit.MINUTES) //延迟10秒执行0
                .addTag(workTag) //设置tag标签
                .setInputData(inputData)
                .build()


        runCatching {
            WorkManager.getInstance(MyAPP.instance).enqueueUniquePeriodicWork(workUniqueName, ExistingPeriodicWorkPolicy.KEEP, workRequest)
        }.onFailure {
            runCatching {
                // 兜底：重新初始化一次
                WorkManager.initialize(
                    MyAPP.instance,
                    Configuration.Builder().build()
                )
                WorkManager.getInstance(MyAPP.instance).enqueueUniquePeriodicWork(workUniqueName, ExistingPeriodicWorkPolicy.KEEP, workRequest)
            }
        }


    }
}
