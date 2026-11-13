package com.pdf.editor.data

import com.pdf.editor.common.config.PointConfig

/**
 * 广告场景开关。远端未下发或解析失败时全部默认开启，保持原有展示行为。
 */
data class AdSwitchData(
    val pr_new_open: Int? = null,
    val pr_open_cold: Int? = null,
    val pr_open_hot: Int? = null,
    val pr_open_noti: Int? = null,
    val pr_open_pop: Int? = null,
    val pr_open_file: Int? = null,
    val pr_open_mediapop: Int? = null,
    val pr_permission_open: Int? = null,
    val pr_new_lan_nat: Int? = null,
    val pr_main_banner1: Int? = null,
    val pr_guide1: Int? = null,
    val pr_main_banner2: Int? = null,
    val pr_guide2: Int? = null,
    val pr_new_lan_int: Int? = null,
    val pr_guide_full: Int? = null,
    val pr_guide_int: Int? = null,
    val pr_up_int: Int? = null,
    val pr_down_int: Int? = null,
    val pr_readback: Int? = null,
    val pr_use_end: Int? = null,
    val unload_1: Int? = null,
    val unload_2: Int? = null,
    val unload_nat1: Int? = null,
    val pr_refresh: Int? = null,
) {
    fun isEnabled(pointTag: String): Boolean {
        val switchData = this
        return when (pointTag) {
            PointConfig.AD_POINT_LAUNCH -> (switchData.pr_new_open ?: 1) != 0
            PointConfig.AD_POINT_OPEN_COLD -> (switchData.pr_open_cold ?: 1) != 0
            PointConfig.AD_POINT_OPEN_HOT -> (switchData.pr_open_hot ?: 1) != 0
            PointConfig.AD_POINT_OPEN_NOTI -> (switchData.pr_open_noti ?: 1) != 0
            PointConfig.AD_POINT_OPEN_POP -> (switchData.pr_open_pop ?: 1) != 0
            PointConfig.AD_POINT_OPEN_FILE -> (switchData.pr_open_file ?: 1) != 0
            PointConfig.AD_POINT_OPEN_MEDIA_POP -> (switchData.pr_open_mediapop ?: 1) != 0
            PointConfig.AD_POINT_LAUNCH3 -> (switchData.pr_permission_open ?: 1) != 0
            PointConfig.AD_POINT_NAT -> (switchData.pr_new_lan_nat ?: 1) != 0
            PointConfig.AD_POINT_BANNER1 -> (switchData.pr_main_banner1 ?: 1) != 0
            PointConfig.AD_POINT_GUIDE1 -> (switchData.pr_guide1 ?: 1) != 0
            PointConfig.AD_POINT_BANNER2 -> (switchData.pr_main_banner2 ?: 1) != 0
            PointConfig.AD_POINT_GUIDE2 -> (switchData.pr_guide2 ?: 1) != 0
            PointConfig.AD_POINT_LAN_INT -> (switchData.pr_new_lan_int ?: 1) != 0
            PointConfig.AD_POINT_GUIDE_FULL -> (switchData.pr_guide_full ?: 1) != 0
            PointConfig.AD_POINT_GUIDE_INT -> (switchData.pr_guide_int ?: 1) != 0
            PointConfig.AD_POINT_INT2 -> (switchData.pr_up_int ?: 1) != 0
            PointConfig.AD_POINT_INT1 -> (switchData.pr_down_int ?: 1) != 0
            PointConfig.AD_POINT_READ_BACK -> (switchData.pr_readback ?: 1) != 0
            PointConfig.AD_POINT_USE_END -> (switchData.pr_use_end ?: 1) != 0
            PointConfig.AD_POINT_UNLOAD1 -> (switchData.unload_1 ?: 1) != 0
            PointConfig.AD_POINT_UNLOAD2 -> (switchData.unload_2 ?: 1) != 0
            PointConfig.AD_POINT_UNLOAD_NAT1 -> (switchData.unload_nat1 ?: 1) != 0
            else -> true
        }
    }
}
