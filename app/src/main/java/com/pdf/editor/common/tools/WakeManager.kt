package com.pdf.editor.common.tools

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.pdf.editor.MyAPP
import java.time.LocalDate
import java.time.ZoneId

object WakeManager {
    fun isScreenOn(): Boolean {
        runCatching {
            return (MyAPP.instance.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        }.onFailure {
            AppLogs.eLog(MyAPP.instance.TAG, "isScreenOn失败:${it.stackTraceToString()}")
        }
        return true
    }

    fun isDeviceLocked(): Boolean {
        runCatching {
            return (MyAPP.instance.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceLocked
        }.onFailure {
            AppLogs.eLog(MyAPP.instance.TAG, "isDeviceLocked失败:${it.stackTraceToString()}")
        }
        return true
    }

    fun isScreenOnAndUnlocked(): Boolean {
        return isScreenOn() && !isDeviceLocked()
    }

    @SuppressLint("InvalidWakeLockTag")
    fun wakeUp() {
        if (isScreenOn().not()) {
            AppLogs.dLog(MyAPP.instance.TAG, "当前为黑屏")
            runCatching {
                val powerManager =
                    ContextCompat.getSystemService(MyAPP.instance, PowerManager::class.java)
                // 判断是否支持 CPU 唤醒
                val isWakeLockLevelSupported = powerManager!!.isWakeLockLevelSupported(
                    PowerManager.PARTIAL_WAKE_LOCK
                )
//             = powerManager!!.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WAKE_LOCK");
                AppLogs.dLog(MyAPP.instance.TAG, "是否支持唤醒：${isWakeLockLevelSupported}")
                if (isWakeLockLevelSupported) {
                    var mWakeLock = powerManager.newWakeLock(805306394, "WAKE_LOCK")
                    mWakeLock.acquire(100L)
                    mWakeLock.release()
                }
            }.onFailure {
                AppLogs.eLog(MyAPP.instance.TAG, "唤醒失败:${it.stackTraceToString()}")
            }
        } else {
            AppLogs.dLog(MyAPP.instance.TAG, "当前为亮屏")
        }
    }

    fun getStartTime(): Long {
        val today7am = LocalDate.now()
            .atTime(7, 0)
            .atZone(ZoneId.systemDefault())
        return today7am.toInstant().toEpochMilli()
    }

    fun getEndTime(): Long {
        val today11pm = LocalDate.now()
            .atTime(23, 0)
            .atZone(ZoneId.systemDefault())
        return today11pm.toInstant().toEpochMilli()
    }
}