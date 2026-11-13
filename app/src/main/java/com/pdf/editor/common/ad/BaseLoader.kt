package com.pdf.editor.common.ad

import com.pdf.editor.data.ADFailData
import com.pdf.editor.data.ADRequestData
import com.pdf.editor.data.ADResultData

abstract class BaseLoader(
    val requestBean: ADRequestData,
    val loadAdEnum: ADEnum,
) {
    private lateinit var failedCallback: (ADFailData) -> Unit
    private lateinit var successCallback: (ADResultData) -> Unit

    fun setSuccessCall(callback: (ADResultData) -> Unit) {
        successCallback = callback
    }

    fun setFailedCall(callback: (ADFailData) -> Unit) {
        failedCallback = callback
    }

    protected fun loadSucceeded(data: ADResultData) = successCallback(data)

    protected fun loadFailed(data: ADFailData) = failedCallback(data)

    protected fun loadFailed(errorCode: String, errorMessage: String) {
        loadFailed(ADFailData().apply {
            error_code = errorCode
            error_message = errorMessage
        })
    }

    fun startLoadAD() {
        when (requestBean.iwn) {
            ADControl.AD_TYPE_OPEN -> openAD()
            ADControl.AD_TYPE_INT -> intAD()
            ADControl.AD_TYPE_RV -> rewarded()
            ADControl.AD_TYPE_NATIVE -> nativeAD()
            ADControl.AD_TYPE_BANNER -> bannerAD()
            else -> loadFailed("unsupported_ad_type", requestBean.iwn)
        }
    }

    abstract fun openAD()
    abstract fun intAD()
    abstract fun rewarded()
    abstract fun nativeAD()
    abstract fun bannerAD()
}
