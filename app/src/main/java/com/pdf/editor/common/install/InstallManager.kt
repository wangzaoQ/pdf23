package com.pdf.editor.common.install

import android.content.Context
import android.text.TextUtils
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.android.installreferrer.api.ReferrerDetails
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.user.UserControl

object InstallManager {
    var TAG = "InstallManager"
    private const val REFER_MAX = 10

    var isLoading = false
    fun requestRefer(context: Context, count: Int = 0, onComplete: (String?) -> Unit) {
        AppLogs.dLog(TAG,"requestRefer 方法执行")
        if (LocalDataManager.installRefer.isNotEmpty() &&
            LocalDataManager.installReferNet.isNotEmpty()
        ) {
            AppLogs.dLog(TAG,"refer 已有缓存")
            return
        }
        if (count >= REFER_MAX) {
            onComplete(null)
            return
        }
        AppLogs.dLog(TAG,"开始请求 refer")
        runCatching {
            val referrerClient = InstallReferrerClient.newBuilder(context).build()
            referrerClient.startConnection(object : InstallReferrerStateListener {

                override fun onInstallReferrerSetupFinished(responseCode: Int) {
                    runCatching {
                        when (responseCode) {
                            InstallReferrerClient.InstallReferrerResponse.OK -> {
                                // Connection established.
                                runCatching {
                                    val response: ReferrerDetails = referrerClient.installReferrer
                                    val referrerUrl: String = response.installReferrer?:""
                                    AppLogs.dLog(TAG, "requestRefer count=$count refer=$referrerUrl")
                                    var refer = referrerClient.installReferrer?.installReferrer ?: ""
                                    if (TextUtils.isEmpty(refer)){
                                        requestRefer(context, count + 1, onComplete)
                                        return
                                    }
                                    LocalDataManager.installRefer = refer
                                    AppLogs.dLog(TAG,"install打点")
                                    EventManager.install(response)
                                    UserControl.isBuyUser(refer)
                                    UserControl.poseUserStatusByRefer(refer)
                                }
                            }
                            else -> {
                                requestRefer(context, count + 1, onComplete)
                            }
                        }
                    }
                    runCatching {
                        referrerClient.endConnection()
                    }
                }

                override fun onInstallReferrerServiceDisconnected() {
                    // Try to restart the connection on the next request to
                    // Google Play by calling the startConnection() method.
                    AppLogs.eLog(TAG,"onInstallReferrerServiceDisconnected")
                }
            })
        }.onFailure {
            AppLogs.eLog(TAG,it.stackTraceToString())
            requestRefer(context, count + 1, onComplete)
        }
    }
}
