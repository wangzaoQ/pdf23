package com.pdf.editor.data

/**
 * {
 * "time_interval":30,// 定时间隔时间30分钟
 * "unlock_interval":30,//解锁通知间隔时间30分钟
 * "first_interval":60//首次通知触发的时间
 *
 * }
 */
class TimerNFData {
    //通知公共间隔
    var all_push_cd = 60
    //定时通知间隔
    var local_interval = 3600
    //广播通知间隔
    var action_interval = 600
    var battery_changed = 1200
    //定时通知最大触发次数
    var push_max_time = 30
    //广播通知最大触发次数
    var push_max_broadcast = 20
    var push_refresh_1 = 2
    var push_refresh_2 = 30

}
