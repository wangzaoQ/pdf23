package com.pdf.editor.common.tools

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import com.pdf.editor.MyAPP
import com.pdf.editor.cache.LocalDataManager
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.Date
import android.provider.Settings
import androidx.activity.result.ActivityResult
import com.pdf.editor.common.core.BaseActivity
import kotlin.math.abs

fun isOtherPkg(context: Context): Boolean {
    runCatching {
        val pid = Process.myPid()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processName = manager.runningAppProcesses.find { it.pid == pid }?.processName ?: ""
        return context.packageName != processName
    }
    return false
}

fun Job?.jobCancel() {
    runCatching {
        if (this == null)return
        if (this.isCancelled.not()) {
            this.cancel()
        }
    }.onFailure {
        AppLogs.eLog("JobCancel", it.stackTraceToString())
    }
}

inline fun <reified T : Activity> Activity.jumpToActivity(
    extra: Bundle? = null,
    flags: Int? = null,
    animalType:Int ?= 0,
    noinline onResult: ((ActivityResult) -> Unit)? = null
) {
    val intent = getIntent<T>(flags, extra)
    if (onResult != null) {
        val baseActivity = this as? BaseActivity<*>
            ?: error("Activity result callbacks require BaseActivity")
        baseActivity.launchActivityForResult(intent, onResult)
    } else {
        startActivity(intent)
    }
    if (animalType == 1){
        overridePendingTransition(0,0)
    }
}

inline fun <reified T : Context> Context.getIntent(flags: Int?, extra: Bundle?): Intent =
    Intent(this, T::class.java).apply {
        flags?.let { setFlags(flags) }
        extra?.let { putExtras(extra) }
    }


fun isAndroid11(): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
}

fun appDataReset(){
    var todayDate = LocalDataManager.dayString
    val tmpDate = SimpleDateFormat("yyyyMMdd").format(Date(System.currentTimeMillis())).toString()
    if (todayDate != tmpDate) {
        AppLogs.dLog(MyAPP.instance.TAG,"每日数据重置开始")
        LocalDataManager.dayString = tmpDate
        LocalDataManager.clickEveryDay = 0
        LocalDataManager.showEveryDay = 0
        // 开屏广告按自然日单独限额，跨天后展示数和点击数同时清零。
        LocalDataManager.openAdClickEveryDay = 0
        LocalDataManager.openAdShowEveryDay = 0
        LocalDataManager.allowToSystem = true
        LocalDataManager.saveNfCountByEnum(com.pdf.editor.common.nf.NFEnum.NF_TIMER_1, 0)
        LocalDataManager.saveNfCountByEnum(com.pdf.editor.common.nf.NFEnum.NF_UNLOCK, 0)
        AppLogs.dLog(MyAPP.instance.TAG,"每日数据重置结束")
    }
    if (isOver24Hours(LocalDataManager.lastIn, System.currentTimeMillis())){
        AppLogs.dLog(MyAPP.instance.TAG,"两次时间超过24小时")
        LocalDataManager.lastIn = System.currentTimeMillis()
        LocalDataManager.adDayValue = 0.0
        LocalDataManager.adDayEcpm = 0
    }
}

fun isOver24Hours(t1: Long, t2: Long): Boolean {
    val diff = abs(t1 - t2)
    return diff > 24 * 60 * 60 * 1000L
}

fun isDefaultMyAPP(context: Context): Boolean {
    val pm = context.packageManager
    val outIntents = ArrayList<IntentFilter>()
    val outActivities = ArrayList<ComponentName>()
    pm.getPreferredActivities(outIntents, outActivities, null)

    for (i in outIntents.indices) {
        val filter = outIntents[i]
        val activity = outActivities[i]

        try {
            // 必须有 VIEW action 和 DEFAULT category
            if (!filter.hasAction(Intent.ACTION_VIEW)) continue
            if (!filter.hasCategory(Intent.CATEGORY_DEFAULT)) continue

            // 强制要求精确 mime 为 application/pdf（不要接受 */* 或 application/*）
            if (!filter.hasDataType("application/pdf")) continue

            // 要求有常见的可打开 scheme（更严格）
            val hasScheme = filter.hasDataScheme("content") ||
                    filter.hasDataScheme("file") ||
                    filter.hasDataScheme("http") ||
                    filter.hasDataScheme("https")
            if (!hasScheme) continue

            if (activity.packageName == context.packageName) {
                return true
            }
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            // 忽略这个 filter，继续下一条
            continue
        }
    }

    // 备用校验：resolveActivity on a fake content URI — 不会实际打开文件
    try {
        val fake = Uri.fromParts("content", "non_exist_pdf", null)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fake, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (resolveInfo?.activityInfo?.packageName == context.packageName) {
            return true
        }
    } catch (e: Exception) {
        // 忽略 resolveActivity 出错 (厂商差异)
    }

    return false
}

fun requestSetAsDefaultMyAPP(activity: Activity) {
    val pm = activity.packageManager

    // 不存在的 content URI —— 不会实际打开文件
    val fakeUri = Uri.fromParts("content", "non_exist_pdf", null)

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(fakeUri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    // 查询能处理该 Intent 的活动（候选列表）
    val candidates = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)

    when {
        candidates.isEmpty() -> {
            // 没有可处理的 app，提示用户安装或打开默认应用设置
            // 可选：跳转到默认应用设置页或应用详情页
            // 跳转到“默认应用”设置（部分设备存在）
            try {
                val defaultApps = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                activity.startActivity(defaultApps)
            } catch (e: Exception) {
                // 回退：打开应用详情页，用户可通过系统设置去操作
                val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivity(appDetails)
            }
        }
        candidates.size == 1 -> {
            // 只有一个匹配项：系统通常会直接打开该 App（没有“始终/仅此一次”选择）
            // 引导用户去 App 信息页手动设置“默认打开”或打开默认应用设置页面
            val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(appDetails)
        }
        else -> {
            // 多个候选：直接 startActivity(intent) —— 系统会弹 resolver，显示“始终/仅此一次”
            activity.startActivity(intent)
        }
    }
}


