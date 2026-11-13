package com.pdf.editor.common.config

object DefaultConfig {
    /** Firebase 未下发 ad_config 或配置解析失败时使用的开屏广告限额。 */
    const val DEFAULT_AD_CONFIG = """
{
  "ad_show": 100,
  "ad_click": 30
}
"""

    const val DEFAULT_PUSH_TIME = """
{
  "all_push_cd": 60,
  "local_interval": 3600,
  "action_interval": 600,
  "battery_changed": 1200,
  "push_max_time": 30,
  "push_max_broadcast": 20,
  "push_refresh_1": 2,
  "push_refresh_2": 30
}
"""

    const val DEFAULT_AD_SWITCH = """
{
  "pr_new_open": 1,
  "pr_open_cold": 1,
  "pr_open_hot": 1,
  "pr_open_noti": 1,
  "pr_open_pop": 1,
  "pr_open_file": 1,
  "pr_open_mediapop": 1,
  "pr_permission_open": 1,
  "pr_new_lan_nat": 1,
  "pr_main_banner1": 1,
  "pr_guide1": 1,
  "pr_main_banner2": 1,
  "pr_guide2": 1,
  "pr_new_lan_int": 1,
  "pr_guide_full": 1,
  "pr_guide_int": 1,
  "pr_up_int": 1,
  "pr_down_int": 1,
  "pr_readback": 1,
  "pr_use_end": 1,
  "unload_1": 1,
  "unload_2": 1,
  "unload_nat1": 1
}
"""

    const val DEFAULT_AD_JSON = """
        {
  "pr_new_launch": [
    {
      "jsk": "ca-app-pub-3940256099942544/9257395921",
      "iwk": "admob",
      "iwn": "open",
      "isk": 13800,
      "ipn": 3,
      "grp": [0]
    }
  ],
  "pr_launch": [
    {
      "jsk": "ca-app-pub-3940256099942544/9257395921",
      "iwk": "admob",
      "iwn": "open",
      "isk": 13800,
      "ipn": 3,
      "grp": [0]
    }
  ],
  "pr_permission_open": [
    {
      "jsk": "ca-app-pub-3940256099942544/9257395921",
      "iwk": "admob",
      "iwn": "open",
      "isk": 13800,
      "ipn": 3,
      "grp": [0]
    }
  ],
  "pr_ban1": [
    {
      "jsk": "ca-app-pub-3940256099942544/2247696110",
      "iwk": "admob",
      "iwn": "nat",
      "isk": 3000,
      "ipn": 3,
      "grp": [0]
    }
  ],
  "pr_ban2": [
    {
      "jsk": "ca-app-pub-3940256099942544/2247696110",
      "iwk": "admob",
      "iwn": "nat",
      "isk": 3000,
      "ipn": 3,
      "grp": [0]
    }
  ],
  "pr_ban3": [
    {
      "jsk": "ca-app-pub-3940256099942544/2247696110",
      "iwk": "admob",
      "iwn": "nat",
      "isk": 3000,
      "ipn": 3,
      "grp": [0]
    }
  ],
  "pr_user_use": [
    {
      "jsk": "ca-app-pub-3940256099942544/1033173712",
      "iwk": "admob",
      "iwn": "int",
      "isk": 3000,
      "ipn": 3,
      "grp": [0]
    }
  ],
  "pr_exit": [
    {
      "jsk": "ca-app-pub-3940256099942544/1033173712",
      "iwk": "admob",
      "iwn": "int",
      "isk": 3000,
      "ipn": 3,
      "grp": [0]
    }
  ],
  "pr_new_lan_int": [
    {
      "jsk": "ca-app-pub-3940256099942544/1033173712",
      "iwk": "admob",
      "iwn": "int",
      "isk": 3000,
      "ipn": 3,
      "grp": [0]
    }
  ],
  "pr_new_int": [
    {
      "jsk": "ca-app-pub-3940256099942544/1033173712",
      "iwk": "admob",
      "iwn": "int",
      "isk": 3000,
      "ipn": 3,
      "grp": [0]
    }
  ]
}
"""

    /**
     * {
     *   // 新用户首次开屏广告开关 (0: 不展示, 1: 展示)
     *   "first_open_ad": 0,
     *
     *   // 语言选择页面开关
     *   "language_page": 0,
     *
     *   // 语言选择页面的横幅广告 (Banner) 开关
     *   "language_page_banner": 0,
     *
     *   // 功能介绍页面开关
     *   "feature_page": 0,
     *
     *   // 功能介绍页面的横幅广告 (Banner) 开关
     *   "featuer_page_banner": 0,
     *
     *   // 功能介绍页面的全屏/插屏广告开关
     *   "featuer_page_full_ad": 0,
     *
     *   // AI 悬浮窗权限申请页面开关
     *   "ai_pop": 0
     * }
     */
    const val DEFAULT_SWITCH_CONFIG = """
{
  "first_open_ad": 1,
  "language_page": 1,
  "language_page_banner": 1,
  "feature_page": 1,
  "featuer_page_banner": 1,
  "featuer_page_full_ad": 1,
  "ai_pop": 1
}
"""
    const val DEFAULT_USER_REFER_CONFIG = """
{
   "door": 1,
   "ilve":[
      "fb4a",
      "instagram",
      "ig4a",
      "gclid",
      "not%20set",
      "youtubeads",
      "%7B%22",
      "appsflyer",
      "bytedance",
      "livead"
   ]
}
"""
    const val LTV_DEFAULT = """
{
"reader_oneday_top10":1, 
"reader_oneday_top20":0.8,
"reader_oneday_top30":0.6,
"reader_oneday_top40":0.5,
"reader_oneday_top50":0.1
}
"""

    const val ECPM_DEFAULT = """
{
"Adecpm_OneDay_100":1000000, 
"Adecpm_OneDay_150":1500000,
"Adecpm_OneDay_200":2000000
}
"""
}
