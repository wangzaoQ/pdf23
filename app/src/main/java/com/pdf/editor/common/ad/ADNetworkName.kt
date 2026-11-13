package com.pdf.editor.common.ad

import com.google.android.gms.ads.ResponseInfo

object ADNetworkName {
    fun fromAdmob(info: ResponseInfo?): String = normalize(
        info?.loadedAdapterResponseInfo?.adapterClassName
            ?: info?.mediationAdapterClassName
            ?: info?.loadedAdapterResponseInfo?.adSourceName
    )

    fun fromAdmobFail(info: ResponseInfo?): String {
        return info?.adapterResponses
            ?.mapNotNull { normalizeOrNull(it.adapterClassName.ifBlank { it.adSourceName }) }
            ?.distinct()
            ?.joinToString(",")
            ?.ifBlank { null }
            ?: normalize(info?.mediationAdapterClassName)
    }

    private fun normalize(name: String?): String = normalizeOrNull(name) ?: ADControl.AD_PLATFORM_ADMOB

    private fun normalizeOrNull(rawName: String?): String? {
        val name = rawName?.takeIf { it.isNotBlank() } ?: return null
        return when {
            name.contains("admob", true) -> "admob"
            name.contains("pangle", true) || name.contains("bytedance", true) -> "pangle"
            name.contains("applovin", true) -> "applovin"
            name.contains("facebook", true) || name.contains("meta", true) -> "facebook"
            name.contains("mintegral", true) || name.contains("mbridge", true) -> "mintegral"
            name.contains("vungle", true) || name.contains("liftoff", true) -> "liftoff"
            name.contains("unity", true) -> "unity"
            name.contains("ironsource", true) || name.contains("levelplay", true) -> "ironsource"
            else -> null
        }
    }
}
