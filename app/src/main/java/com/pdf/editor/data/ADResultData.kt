package com.pdf.editor.data

import android.os.SystemClock

class ADResultData {
    val adShowReported = java.util.concurrent.atomic.AtomicBoolean(false)
    var adRequestData: ADRequestData? = null
    var adCreateTime: Long = System.currentTimeMillis()
    var adShowTime: Long = 0L
        private set
    var adAny: Any? = null
    var adType = ""
    var adRequestTime: Long = 0L
    var adNetWork = ""
    var adShowType = 0
    var adPointTag = ""


    fun markShown() {
        adShowTime = SystemClock.elapsedRealtime()
    }

    fun getCloseTimeLong(): Long {
        if (adShowTime <= 0L) return 0L
        return (SystemClock.elapsedRealtime() - adShowTime).coerceAtLeast(0L)
    }

}
