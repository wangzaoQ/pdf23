package com.pdf.editor.data

import com.google.gson.annotations.SerializedName

/**
 * 广告每日限额配置。
 *
 * 该配置来自 Firebase Remote Config 的 ad_config 字段，只用于开屏广告频控。
 */
data class ADLimitConfig(
    @SerializedName("ad_show")
    val adShow: Int? = null,
    @SerializedName("ad_click")
    val adClick: Int? = null,
)
