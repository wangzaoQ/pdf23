package com.pdf.editor.common.ad

import android.app.Activity
import android.os.Bundle

import com.google.android.ump.*
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs
import java.util.Locale

object UmpHelper {
    val countryCodes = mutableListOf(
        "AT", "BE", "BG", "HR", "CY", "CZ", "DK", "EE", "FI", "FR",
        "DE", "GR", "HU", "IE", "IT", "LV", "LT", "LU", "MT", "NL",
        "PL", "PT", "RO", "SK", "SI", "ES", "SE", "NO", "IS", "LI",
        "CH", "GB"
    )
    fun initUmp(
        activity: Activity,
        onFinished: ( Boolean) -> Unit
    ) {
        if (ADControl.allowAds){
            onFinished(true)
            return
        }
        //1、最开始 ump_form_request
        EventManager.posePoint(PointConfig.ump_form_request)

        var localCountry = Locale.getDefault().country
        if (!countryCodes.contains(localCountry.uppercase())){
            ADControl.allowAds = true
            onFinished(true)
            return
        }
        EventManager.posePoint(PointConfig.ump_form_load)

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)

        // 测试阶段可 reset，正式版本不要调用
        // consentInformation.reset()

        val params = ConsentRequestParameters.Builder()
//            .setConsentDebugSettings(debugSettings)
            .build()
        AppLogs.dLog("UMP", "Consent start")

        // 第一步，请求 Consent 信息
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                AppLogs.dLog("UMP", "Consent info updated.")
                val needPopup = (consentInformation.consentStatus == ConsentInformation.ConsentStatus.REQUIRED)
                if (needPopup){
                    EventManager.posePoint(PointConfig.consent_flow_trigger)
                }
                // 第二步，自动加载并显示表单（如果需要）
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                    activity,
                    ConsentForm.OnConsentFormDismissedListener { formError ->
                        // 获取 consent 状态
                        val status = consentInformation.consentStatus
                        val allowAds = consentInformation.canRequestAds()
                        ADControl.allowAds = allowAds
                        EventManager.posePoint(PointConfig.consent_status_update, Bundle().apply {
                            putString(PointKey.status, getConsentStatusKey(status))
                        })
                        EventManager.posePoint(PointConfig.can_request_ads, Bundle().apply {
                            putInt(PointKey.type,if (allowAds) 1 else 0)
                        })
                        logStatus(status, allowAds)

                        // 业务层通过 allowAds 判断是否可以加载广告
                        onFinished( allowAds)
                    }
                )
            },
            { error ->
                // 出错情况也要返回状态
                val allowAds = consentInformation.canRequestAds()
                ADControl.allowAds = allowAds
                EventManager.posePoint(PointConfig.consent_status_update, Bundle().apply {
                    putString(PointKey.status,getConsentStatusKey(consentInformation.consentStatus))
                })
                EventManager.posePoint(PointConfig.can_request_ads, Bundle().apply {
                    putInt(PointKey.type, if (allowAds) 1 else 0)
                })
                AppLogs.dLog("UMP", "requestConsentInfoUpdate error: ${error.message} allowAds:${allowAds}")
                onFinished(allowAds)
            }
        )
    }

    private fun getConsentStatusKey(status: Int): String {
        return when (status) {
            ConsentInformation.ConsentStatus.UNKNOWN -> "UNKNOWN"
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> "NOT_REQUIRED"
            ConsentInformation.ConsentStatus.REQUIRED -> "REQUIRED"
            ConsentInformation.ConsentStatus.OBTAINED -> "OBTAINED"
            else -> "UNKNOWN"
        }
    }

    // 状态打印
    private fun logStatus(status: Int, allowAds: Boolean) {
        val statusStr = when (status) {
            ConsentInformation.ConsentStatus.UNKNOWN -> "UNKNOWN"
            ConsentInformation.ConsentStatus.REQUIRED -> "REQUIRED (需要征求同意)"
            ConsentInformation.ConsentStatus.OBTAINED -> "OBTAINED (用户已同意)"
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> "NOT_REQUIRED (不需要同意)"
            else -> "UNKNOWN"
        }

        AppLogs.dLog("UMP", "Consent status = $status ($statusStr)")
        AppLogs.dLog("UMP", "canRequestAds = $allowAds")
    }

}
