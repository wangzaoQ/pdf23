package com.pdf.editor.common.user

import android.content.Context
import com.google.android.gms.time.TrustedTime
import com.google.android.gms.time.TrustedTimeClient
import com.pdf.editor.MyAPP
import com.pdf.editor.common.tools.AppLogs
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

/** 与 pdf14 一致的可信时间客户端持有与检测逻辑。 */
object TrustedTimeHolder {

    private var isSendEvent = false

    @Volatile
    private var client: TrustedTimeClient? = null

    suspend fun getClient(context: Context): TrustedTimeClient? =
        client ?: suspendCancellableCoroutine { continuation ->
            TrustedTime.createClient(context.applicationContext)
                .addOnCompleteListener { task ->
                    if (!continuation.isActive) return@addOnCompleteListener
                    val result = if (task.isSuccessful) task.result else null
                    client = result
                    continuation.resume(result)
                }
        }

    fun clear() {
        client?.dispose()
        client = null
    }

    private const val FIVE_MINUTES_MILLIS = 5 * 60 * 1000L

    fun isWithinFiveMinutesOfNow(time: Long): Boolean {
        val now = System.currentTimeMillis()
        return abs(time - now) <= FIVE_MINUTES_MILLIS
    }

    suspend fun fetchServiceTime(): Long {
        if (!isSendEvent) logEvent("time_real_ask")
        val trustedTimeClient = getClient(MyAPP.instance) ?: return 0
        var time = runCatching { trustedTimeClient.computeCurrentUnixEpochMillis() }.getOrNull()
        if (time != null && time != 0L) {
            if (!isSendEvent) logEvent("time_real_suss")
            isSendEvent = true
        } else {
            time = System.currentTimeMillis()
        }
        return time
    }

    private fun logEvent(name: String) {
        AppLogs.dLog(UserControl.TAG, name)
    }
}
