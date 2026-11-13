package com.pdf.editor.common.point

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.webkit.WebSettings
import com.blankj.utilcode.util.DeviceUtils
import com.pdf.editor.BuildConfig
import com.pdf.editor.MyAPP
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.cache.LocalDataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

object EventParamsManager {

    var TAG = "PointParamsManager"

    val locale: Locale by lazy {
        Locale.getDefault()
    }

    val pkgInfo by lazy {
        MyAPP.instance.packageManager.getPackageInfo(MyAPP.instance.packageName, 0)
    }

    fun urlEncoder(str: String): String {
        return URLEncoder.encode(str, "UTF-8")
    }

    suspend fun getGenericParams(): JSONObject {
        return JSONObject().apply {
            put("clever", JSONObject().apply {
                put("ridgway", BuildConfig.APPLICATION_ID)
                put("seasonal", stripSemicolons(BuildConfig.VERSION_NAME))
                put("jacob", UUID.randomUUID().toString())
                put("creep", System.currentTimeMillis())
                put("clique", DeviceUtils.getManufacturer())
                put("meant", getGID())
            })
            put("dogma", JSONObject().apply {
                put("female", "dent")
                put("hayden", DeviceUtils.getModel())
                put("crypt", Build.VERSION.RELEASE)
                put("grip", getMccMncString())
                put("mock", getDeviceID())
            })
            put("beside", JSONObject().apply {
                put("sidestep", getDeviceID())
            })
            put("clammy", JSONObject().apply {
                put("squad", stripSemicolons("${locale.language}_${locale.country}"))
            })
        }
    }

    private fun stripSemicolons(value: String): String = value.replace(";", "")


    suspend fun getWebUA(): String {
        var webUA = LocalDataManager.webUA
        if (webUA.isNullOrEmpty()) {
            AppLogs.dLog(TAG, "getWebUA start")
            var startTime = System.currentTimeMillis()
            webUA = WebSettings.getDefaultUserAgent(MyAPP.instance) ?: ""
            AppLogs.dLog(TAG, "getWebUA end time :${System.currentTimeMillis() - startTime}")
            LocalDataManager.webUA = webUA
        }
        return webUA
    }

    suspend fun getGID(): String = withContext(Dispatchers.IO) {
        var gid = LocalDataManager.GID
        if (gid.isNullOrEmpty()) {
            AppLogs.dLog(TAG, "getGID start")
            var startTime = System.currentTimeMillis()
            runCatching{
//                gid = Uri.encode(AdvertisingIdClient.getAdvertisingIdInfo(MyAPP.instance).id)
            }
            AppLogs.dLog(TAG, "getGID end time :${System.currentTimeMillis() - startTime}")
            LocalDataManager.GID = gid
        }
        gid
    }

    fun getDeviceID(): String {
        var phoneId = LocalDataManager.deviceId
        if (phoneId.isNullOrEmpty().not()) {
            AppLogs.dLog(TAG, "ANDROID_ID:${phoneId}")
            return phoneId
        }
        val id = Settings.Secure.getString(
            MyAPP.instance.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        AppLogs.dLog(TAG, "ANDROID_ID:${id}")
        phoneId = if ("9774d56d682e549c" == id || "0000000000000000" == id) "" else id
        if (phoneId.isNullOrEmpty().not()) {
            LocalDataManager.deviceId = phoneId
            return phoneId
        }
        phoneId = UUID.randomUUID().toString().replace("-", "")
        AppLogs.dLog(TAG, "UUID:${phoneId}")
        if (phoneId.isNullOrEmpty().not()) {
            LocalDataManager.deviceId = phoneId
        }
        return phoneId
    }

    @SuppressLint("MissingPermission")
    fun getMccMncString(): String {
        runCatching {
            val tm = MyAPP.instance.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // 1. SIM 卡 MCC+MNC
            val sim = tm.simOperator
            if (!sim.isNullOrEmpty() && sim.length >= 5) {
                val mcc = sim.substring(0, 3)
                val mnc = sim.substring(3)
                return "$mcc-$mnc"
            }

            // 2. 当前网络 MCC+MNC（海外手机很稳定）
            val net = tm.networkOperator
            if (!net.isNullOrEmpty() && net.length >= 5) {
                val mcc = net.substring(0, 3)
                val mnc = net.substring(3)
                return "$mcc-$mnc"
            }
        }
        return "mnc"
    }


}
