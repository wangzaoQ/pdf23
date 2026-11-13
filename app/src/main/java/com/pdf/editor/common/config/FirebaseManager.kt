package com.pdf.editor.common.config

import android.os.Bundle
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.data.AdSwitchData

import android.os.Handler
import android.os.Looper
import com.pdf.editor.MyAPP
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.install.InstallManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.DataUtils
import com.pdf.editor.common.user.UserControl
import com.pdf.editor.data.FBData
import com.pdf.editor.data.ADData
import com.pdf.editor.data.ADLimitConfig
import com.pdf.editor.data.RiskData
import com.pdf.editor.data.UserReferData
import com.facebook.FacebookSdk
import com.facebook.appevents.AppEventsLogger
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.messaging.messaging
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.pdf.editor.common.nf.NFControl
import java.util.Locale

object FirebaseManager {

    private const val AD_CONFIG_REFRESH_INTERVAL = 1_000L

    var TAG = "FirebaseManager"

    val fb by lazy { AppEventsLogger.newLogger(MyAPP.instance) }

    val firebaseAnalytics: FirebaseAnalytics by lazy {
        FirebaseAnalytics.getInstance(MyAPP.instance)
    }

    var ltvConfig = ""

    var fbData: FBData?=null

    var riskData: RiskData? = null
    private var lastADConfigRefreshTime = 0L

    private val mConfigChange: ConfigUpdateListener = object : ConfigUpdateListener {
        override fun onUpdate(configUpdate: ConfigUpdate) {
            FirebaseRemoteConfig.getInstance().fetchAndActivate().addOnCompleteListener {
                updateConfig(true)
            }
        }

        override fun onError(error: FirebaseRemoteConfigException) = Unit

    }

    var initComplete = false

    fun initData() {
        runCatching {
            var startTime = System.currentTimeMillis()
            FirebaseApp.initializeApp(MyAPP.instance)
            initComplete = true
//            // Debug：确认设备当前 token（会变化，常见“发到旧 token 导致收不到”）
//            Firebase.messaging.token.addOnCompleteListener { task ->
//                if (task.isSuccessful) {
//                    val token = task.result ?: ""
//                    AppLogs.dLog("FCMService", "FCM token=$token")
//                } else {
//                    AppLogs.dLog("FCMService", "FCM token failed: ${task.exception}")
//                }
//            }
            updateConfig(false)
            val configSettings = remoteConfigSettings {
                minimumFetchIntervalInSeconds = 3600L   // 1小时
                fetchTimeoutInSeconds = 10L             // 可选：Fetch 超时
            }
            FirebaseRemoteConfig.getInstance().let { remoteConfig ->
                remoteConfig.addOnConfigUpdateListener(mConfigChange)
                remoteConfig.setConfigSettingsAsync(configSettings).addOnCompleteListener {
                    remoteConfig.fetchAndActivate().addOnCompleteListener {
                        EventManager.posePoint(PointConfig.firebase_time, Bundle().apply {
                            putLong(PointKey.time, (System.currentTimeMillis()-startTime)/1000)
                        })
                        updateConfig(true)
                        updateFB()
                    }
                }
            }
            InstallManager.requestRefer(MyAPP.instance, 0, {})
            subscribeTopicsOnColdStartByLocale()
        }
    }

    fun subscribeTopicsOnColdStartByLocale() {
        val locale = Locale.getDefault()
        val language = locale.language
        val country = locale.country

        val topics = when {
//            // 日语
//            language == "ja" -> listOf(
//                "pdf_jp",
//                "pdf_jp_noti"
//            )
//
//            // 中文 · 繁体（台湾 / 香港）
//            language == "zh" && (country == "TW" || country == "HK") -> listOf(
//                "pdf_zhs",
//                "pdf_zhs_noti"
//            )
//
//            // 中文 · 简体（中国）
//            language == "zh" && country == "CN" -> listOf(
//                "pdf_zh",
//                "pdf_zh_noti"
//            )
//
//            // 德语
//            language == "de" -> listOf(
//                "pdf_de",
//                "pdf_de_noti"
//            )
//
//            // 法语
//            language == "fr" -> listOf(
//                "pdf_fr",
//                "pdf_fr_noti"
//            )

            // 默认
            else -> listOf(
                "B20_pdf_fcm",
                "B20_pdf_fcm02"
            )
        }

        subscribeTopicsOnColdStart(topics)
    }


    fun subscribeTopicsOnColdStart(topics: List<String>) {
        // 按顺序订阅，避免短时间并发请求触发 throttle
        fun subscribeNext(index: Int = 0) {
            if (index >= topics.size) return

            val topic = topics[index]
            Firebase.messaging.subscribeToTopic(topic)
                .addOnCompleteListener { task ->
                    // 可以打印日志
                    if (task.isSuccessful) {
                        AppLogs.dLog(TAG, "Subscribed to topic: $topic")
                    } else {
                        AppLogs.dLog(TAG,"Failed to subscribe $topic: ${task.exception}")
                        // 可选：延迟 5 秒再尝试同一个 topic
                        Handler(Looper.getMainLooper()).postDelayed({
                            Firebase.messaging.subscribeToTopic(topic)
                                .addOnCompleteListener {
                                    // 忽略第二次失败
                                    subscribeNext(index + 1)
                                }
                        }, 5000)
                        return@addOnCompleteListener
                    }
                    // 订阅下一个 topic
                    subscribeNext(index + 1)
                }
        }

        subscribeNext()
    }

    fun updateFB() {
        fbData = DataUtils.parseBeanByGson(getConfig("pr_fb"), FBData::class.java)
        if (fbData!=null){
            runCatching {
                runCatching {
                    if (FacebookSdk.isInitialized()) return
                    FacebookSdk.setApplicationId(fbData!!.app_id)
                    FacebookSdk.setClientToken(fbData!!.token)
                    FacebookSdk.sdkInitialize(MyAPP.instance)
                }
            }.onFailure {
                AppLogs.eLog(MyAPP.instance.TAG,"fb init error :${it.stackTraceToString()}")
            }
        }
    }

    fun updateConfig(isRemote: Boolean) {
        ltvConfig = getConfig("pr_daytop_percent")
        if (ltvConfig.isNullOrEmpty()){
            ltvConfig = DefaultConfig.LTV_DEFAULT
        }
        runCatching{
            NFControl.floatRandom = getConfig("float_oho").toInt()
        }
        runCatching{
            NFControl.mediaPopCD = getConfig("mediapop_cd").toInt()
        }
        runCatching { NFControl.appExit = getConfig("app_exit").toInt() }
        riskData = DataUtils.parseBeanByGson(getConfig("risk_control"), RiskData::class.java)
        if (riskData!=null){
            UserControl.isBuyUser()
            UserControl.uploadRisk()
        }
        var pdf_test_name = getConfig("pdf_test_name")
        if (pdf_test_name.isNotEmpty()){
            EventManager.addUserType(Bundle().apply {
                putString("pdf_test_name",pdf_test_name)
            })
        }
        var pdf_group_name =
            getConfig("pdf_group_name")
        if (pdf_group_name.isNotEmpty()){
            EventManager.addUserType(Bundle().apply {
                putString("pdf_group_name",pdf_group_name)
            })
        }
        updateADConfig()
        updateReferConfig()
    }


    private fun updateReferConfig() {
        UserControl.userReferData = DataUtils.parseBeanByGson(getConfig("pr_refer"), UserReferData::class.java)
        if (UserControl.userReferData == null){
            UserControl.userReferData = DataUtils.parseBeanByGson(DefaultConfig.DEFAULT_USER_REFER_CONFIG,UserReferData::class.java)
        }
    }

    fun updateADConfig() {
        refreshLatestADConfig(force = true)
    }


    fun isFcmForegroundServiceEnabled(): Boolean {
        return getConfig("fcm_foreground_service_enabled").toBooleanStrictOrNull() ?: true
    }

    fun getConfig(name: String): String {
        runCatching {
            var data = FirebaseRemoteConfig.getInstance().getString(name)
            AppLogs.dLog(TAG,"remote config : $name = $data")
            return data
        }.onFailure {

        }
        return ""
    }
    fun refreshLatestADConfig(force: Boolean = false) {
        if (!initComplete) return
        val now = System.currentTimeMillis()
        if (!force && now - lastADConfigRefreshTime < AD_CONFIG_REFRESH_INTERVAL) return
        lastADConfigRefreshTime = now
        ADControl.adLoadTime = getConfig("isk_time").toIntOrNull() ?: 0
        ADControl.cd_hot = getConfig("cd_hot").toIntOrNull()
        ADControl.new_op_cd = getConfig("new_op_cd").toIntOrNull()
        ADControl.kc_cd = getConfig("kc_cd").toIntOrNull()
        ADControl.a_kc_cd = getConfig("a_kc_cd").toIntOrNull()
        runCatching { ADControl.newRequestConfig = getConfig("new_request").toInt() }
        val configName = "pdf_ad_20"
        ADControl.adRootBean = DataUtils.parseBeanByGson(getConfig(configName), ADData::class.java)
        ADControl.adSource = if (ADControl.adRootBean == null) "local" else "remote"
        if (ADControl.adRootBean == null){
            ADControl.adRootBean = DataUtils.parseBeanByGson(DefaultConfig.DEFAULT_AD_JSON, ADData::class.java)
        }
        // 开屏限额使用独立配置。远端缺失或内容非法时回退到本地安全默认值。
        ADControl.adLimitConfig = DataUtils.parseBeanByGson(
            getConfig("ad_config"),
            ADLimitConfig::class.java,
        )
        if (ADControl.adLimitConfig == null) {
            ADControl.adLimitConfig = DataUtils.parseBeanByGson(
                DefaultConfig.DEFAULT_AD_CONFIG,
                ADLimitConfig::class.java,
            )
        }
        // 展示前按具体广告场景读取开关；解析失败时使用全开启的本地配置。
        ADControl.adSwitchData = DataUtils.parseBeanByGson(
            getConfig("switch_config"),
            AdSwitchData::class.java,
        )
        if (ADControl.adSwitchData == null) {
            ADControl.adSwitchData = DataUtils.parseBeanByGson(
                DefaultConfig.DEFAULT_AD_SWITCH,
                AdSwitchData::class.java,
            )
        }
        ADControl.updateADConfig()
    }

}
