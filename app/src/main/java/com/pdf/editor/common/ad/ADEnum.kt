package com.pdf.editor.common.ad

import com.pdf.editor.data.ADRequestData

enum class ADEnum(
    val adName: String,
    val adShowType: Int,
    var adRequestList: MutableList<ADRequestData> = mutableListOf(),
) {
    OPEN_AD_ONLY("pr_new_launch", 0),
    OPEN_AD("pr_launch", 0),
    BANNER_AD_1("pr_ban1", 0),
    BANNER_AD_2("pr_ban2", 0),
    INT_AD_USER("pr_user_use", 0),
    INT_AD_EXIT("pr_exit", 0),
    INT_AD_NEW("pr_new_int", 0)
}
