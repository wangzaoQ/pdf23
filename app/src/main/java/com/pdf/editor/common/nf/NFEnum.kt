package com.pdf.editor.common.nf

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
//notify_new_file（检测有新文件）、USER_PRESENT、ACTION_POWER_CONNECTED、ACTION_POWER_DISCONNECTED、SCREEN_ON、SCREEN_OFF、CONFIGURATION_CHANGED
/**
 * 类型枚举
 */
enum class NFEnum(val menuName: String, val pointTag: String, val channelId:String, val channelPriority:Int, val nfPriority:Int, val position: Int) {

    NF_NEWS_FCM("fcm","fcm","FCM_PUSH", NotificationManager.IMPORTANCE_MAX, NotificationCompat.PRIORITY_MAX,position = 3399),
    NF_TIMER_1("time_push_1","time","TIME_PUSH_1", NotificationManager.IMPORTANCE_MAX,NotificationCompat.PRIORITY_MAX,position = 3398),
    NF_UNLOCK("unlock_push","USER_PRESENT","UNLOCK_PUSH", NotificationManager.IMPORTANCE_MAX,NotificationCompat.PRIORITY_MAX,position = 3397),
    NF_SCREEN_ON("screen_on_push","SCREEN_ON","SCREEN_ON_PUSH", NotificationManager.IMPORTANCE_MAX,NotificationCompat.PRIORITY_MAX,position = 3391),
    NF_SCREEN_OFF("screen_off_push","SCREEN_OFF","SCREEN_OFF", NotificationManager.IMPORTANCE_MAX, NotificationCompat.PRIORITY_MAX, position = 3335),
    NF_BACKGROUND("background","background","BACKGROUND", NotificationManager.IMPORTANCE_MAX,NotificationCompat.PRIORITY_MAX,position = 3341),
    NF_HOME("home","home","HOME", NotificationManager.IMPORTANCE_MAX,NotificationCompat.PRIORITY_MAX,position = 3343),
    NF_RECENT("recent","recent","RECENT", NotificationManager.IMPORTANCE_MAX,NotificationCompat.PRIORITY_MAX,position = 3344),
    NF_NEW_FILE("new_file","notify_new_file","NEW_FILE", NotificationManager.IMPORTANCE_MAX,NotificationCompat.PRIORITY_MAX,position = 3342),
    NF_FOREGROUND("foreground_nf","fix","FOREGROUND_PUSH", NotificationManager.IMPORTANCE_DEFAULT, NotificationCompat.PRIORITY_DEFAULT,position = 3311),
    NF_MEDIA("media_push","media","MEDIA_PUSH", NotificationManager.IMPORTANCE_MAX, NotificationCompat.PRIORITY_MAX,position = 3312),
    NF_POWER_CONNECTED("power_connected","ACTION_POWER_CONNECTED","POWER_CONNECTED", NotificationManager.IMPORTANCE_MAX, NotificationCompat.PRIORITY_MAX, position = 3338),
    NF_POWER_DISCONNECTED("power_disconnected","ACTION_POWER_DISCONNECTED","POWER_DISCONNECTED", NotificationManager.IMPORTANCE_MAX, NotificationCompat.PRIORITY_MAX, position = 3337),
    NF_CONFIGURATION_CHANGED("configuration_changed","CONFIGURATION_CHANGED","CONFIGURATION_CHANGED", NotificationManager.IMPORTANCE_MAX, NotificationCompat.PRIORITY_MAX, position = 3330),
    NF_PACKAGE_ADDED("package_added","PACKAGE_ADDED","PACKAGE_ADDED", NotificationManager.IMPORTANCE_MAX, NotificationCompat.PRIORITY_MAX, position = 3334),
    NF_PACKAGE_REMOVED("package_removed","PACKAGE_REMOVED","PACKAGE_REMOVED", NotificationManager.IMPORTANCE_MAX, NotificationCompat.PRIORITY_MAX, position = 3333),
    NF_PACKAGE_REPLACED("package_replaced","PACKAGE_REPLACED","PACKAGE_REPLACED", NotificationManager.IMPORTANCE_MAX, NotificationCompat.PRIORITY_MAX, position = 3332),
//    NF_CLOSE_SYSTEM_DIALOGS("close_system_dialogs","system_dialogs","CLOSE_SYSTEM_DIALOGS", NotificationManager.IMPORTANCE_DEFAULT, NotificationCompat.PRIORITY_DEFAULT, position = 6331),
    NF_BATTERY_CHANGED("battery_changed","BATTERY_CHANGED","BATTERY_CHANGED", NotificationManager.IMPORTANCE_MAX, NotificationCompat.PRIORITY_MAX, position = 3336),
}
