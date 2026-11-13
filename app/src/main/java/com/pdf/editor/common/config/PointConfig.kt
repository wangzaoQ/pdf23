package com.pdf.editor.common.config

object PointConfig {
    const val nf_error = "nf_error"
    //"新人首次开屏
    const val AD_POINT_LAUNCH = "pr_new_open"
    //冷启动开屏
    const val AD_POINT_OPEN_COLD ="pr_open_cold"
    //热启动开屏
    const val AD_POINT_OPEN_HOT ="pr_open_hot"
    //通知点击开屏
    const val AD_POINT_OPEN_NOTI ="pr_open_noti"
    //媒体通知开屏
    const val AD_POINT_OPEN_MEDIA_POP ="pr_open_mediapop"
    //悬浮开屏
    const val AD_POINT_OPEN_POP ="pr_open_pop"
    //外部文件开屏
    const val AD_POINT_OPEN_FILE ="pr_open_file"
    //新用户权限开屏
    const val AD_POINT_LAUNCH3 ="pr_permission_open"
    //新用户语言选择⚠️
    const val AD_POINT_NAT ="pr_new_lan_nat"
    //文件列表原生1⚠️
    const val AD_POINT_BANNER1 = "pr_main_banner1"
    //新用户流程引导1⚠️
    const val AD_POINT_GUIDE1 = "pr_guide1"
    const val AD_POINT_GUIDE2 = "pr_guide2"
    //文件列表原生2⚠️
    const val AD_POINT_BANNER2 = "pr_main_banner2"
    //切换底部tab⚠️
    const val AD_POINT_INT1 = "pr_down_int"
    //切换tab⚠️
    const val AD_POINT_INT2 = "pr_up_int"
    //退出文件⚠️
    const val AD_POINT_READ_BACK = "pr_readback"
    //退出应用广告⚠️

    //1. 用户进入“卸载选择页” 展示
    const val AD_POINT_UNLOAD_NAT1 = "unload_nat1"
    //1. 用户进入“问题反馈页”展示
    //1. 用户长按logo点击“卸载”后展示
    const val AD_POINT_UNLOAD1 = "unload_1"
    //1. 用户在“卸载选择页” 点击“坚持卸载”后展示
    const val AD_POINT_UNLOAD2 = "unload_2"

    //新用户语言选择页插屏
    const val AD_POINT_LAN_INT = "pr_new_lan_int"
    //新用户引导页全屏原生⚠️
    const val AD_POINT_GUIDE_FULL = "pr_guide_full"
    //引导页插屏
    const val AD_POINT_GUIDE_INT = "pr_guide_int"
    //功能使用完毕
    const val AD_POINT_USE_END = "pr_use_end"

    const val sdk_initialization = "sdk_initialization"
    const val ad_request = "ad_request"
    const val ad_load_success = "ad_load_success"
    const val ad_load_fail = "ad_load_fail"
    const val ad_chance = "ad_chance"
    const val ad_show = "ad_show"

    const val ad_close = "ad_close"
    const val ad_click = "ad_click"
    const val ad_impression_fail = "ad_show_fail"
    //文件管理权限授权成功
    const val ad_permission_file = "storage_system_result"
    //通知权限授权成功
    const val ad_permission_notice = "ad_permission_notice"
    //reffer判断 reffer_user：0（a包）、1（b包）
    const val reffer_suc = "reffer_suc"
    //cloak_user：0（a包）、1（b包）
    const val cloak_suc = "cloak_suc"
    // AppsFlyer 判断结果，af_user：0（A 包）、1（B 包）
    const val af_suc = "af_suc"
    //cloak请求
    const val cloak_req = "cloak_req"

    const val pr_total_001_revenue = "pr_total_001_revenue"


    const val push = "push"
    const val inform_c = "inform_c"
    //顶部文件类型tab切换
    const val home_top_tab = "home_top_tab"
    //安装
    const val install = "install"
       const val home_page = "home_page"
    //第一次进首页
    const val ad_permission_home = "ad_permission_home"
//    const val collect_page = "collect_page"
//    const val recent_page = "recent_page"
    //启动页
    const val launch_page = "app_launch_result"
    //语言选择页面
    const val language_page = "lang_select_view"
    //语言选择页面返回
    const val language_page_back = "language_page_back"
    //语言选择页面确认
    const val language_page_confirm = "lang_select_click"
      // 新用户引导页每一页展示时上报；page：page_1 / page_2 / page_3。
    const val guide_page_action = "guide_page_action"
    //page：files/tools
    const val page_view = "page_view"
    //顶部文件筛选
    const val file_filter_click = "file_filter_click"
    //搜索功能点击
    const val search_click = "search_click"
    //排序方式切换
    const val file_sort_change = "file_sort_change"
    // 文件列表点击单个文件的“更多操作”入口、弹出重命名/删除菜单时上报。
    const val file_multi_action_click = "file_multi_action_click"
    //文件删除确认
    const val file_delete_confirm = "file_delete_confirm"
    //进入编辑器
    const val editor_entry = "editor_entry"
    //标注工具使用 tool：highlight / underline / strike / doodle
    const val annotation_tool_click = "annotation_tool_click"
    //撤销
    const val editor_undo_redo = "editor_undo_redo"
    //保存操作
    const val editor_save_click = "editor_save_click"
    // 工具页点击“扫描转 PDF”入口时上报。
    const val tool_scan = "tool_scan"
    // 工具页点击“Word 转 PDF”入口时上报。
    const val tool_word_to_pdf = "tool_word_to_pdf"
    // 工具页点击“图片转 PDF”入口时上报。
    const val tool_image_to_pdf = "tool_image_to_pdf"
    // 工具页点击“合并 PDF”入口时上报。
    const val tool_merge_pdf = "tool_merge_pdf"
    // 工具页点击“拆分 PDF”入口时上报。
    const val tool_split_pdf = "tool_split_pdf"
    // 工具页点击“PDF 转 Word”入口时上报。
    const val tool_pdf_to_word = "tool_pdf_to_word"
    // 工具页点击“提取文本”入口时上报；当前版本尚无该入口，先保留事件定义。
    const val tool_text = "tool_text"
    // 文件首页点击顶部“添加桌面小组件”引导按钮时上报。
    const val widget_banner_click = "widget_banner_click"
    // 工具页点击“添加桌面小组件”卡片或卡片内按钮时上报。
    const val widget_tool_click = "widget_tool_click"
    // 文件列表或工具文件选择页展示存储权限引导状态时上报，同一页面连续展示仅上报一次。
    const val storage_auth_view = "storage_auth_view"
    // 用户点击存储权限引导中的授权按钮、准备跳转系统授权页时上报。
    const val storage_auth_click = "storage_auth_click"
    //通知引导弹窗展示 show_type：system: 系统原生；secondary: 二次引导弹窗
    const val push_guide_view = "push_guide_view"
    //获取悬浮窗权限页（全屏）展示
    const val float_page_view = "float_page_view"
    //button：open(主按钮) / later(次按钮)
    const val float_page_click = "float_page_click"
    //    state：on、off
    const val float = "float"
    //悬浮窗点击
    const val float_click = "float_click"
    const val risk_control = "risk_control"


    //第三方扫描流程结束
    const val scan_save = "scan_save"
    //扫描loading页面展示
    const val scan_loading_page = "scan_loading_page"
    //扫描完成进入PDF页面
    const val scan_complete_pdf = "scan_complete_pdf"

    //首页点击返回
    const val home_page_back = "home_page_back"
    //pdf阅读页面展示
    const val pdf_page = "pdf_page"
    const val other_page = "other_page"
    //pdf阅读页面点击返回
    const val pdf_page_back = "pdf_page_back"
    const val other_page_back = "other_page_back"

    //二次通知请求弹窗点击ok state：turn_on；later
    const val push_guide_click = "push_guide_click"
    //点击卸载
    const val uninstall_click = "uninstall_click"
    //卸载选择页
    const val uninstall_option_page = "uninstall_option_page"
    //卸载选择页-卸载选择
    const val uninstall_option_select = "uninstall_option_select"
    //问题反馈页
    const val uninstall_feedback_page = "uninstall_feedback_page"
    //问题反馈答案
    const val uninstall_feedback = "uninstall_feedback"
    //问题反馈-不卸载
    const val uninstall_feedback_select = "uninstall_feedback_select"

    //悬浮窗展示
    const val pop_up_show = "pop_up_show"

    //后台存活打点：进入后台、后台每小时、保活拉活成功
    const val session_bac = "session_bac"

    //ump请求
    const val ump_form_request = "ump_form_request"
    //ump加载
    const val ump_form_load = "ump_form_load"


    //用户完成隐私协议
    const val consent_status_update = "consent_status_update"
    //是否允许请求广告
    const val can_request_ads = "can_request_ads"
    //进入隐私协议流程
    const val consent_flow_trigger = "consent_flow_trigger"
    //firebase 初始化时间
    const val firebase_time = "firebase_time"
    //用户类型
    const val user_type = "user_type"
}
