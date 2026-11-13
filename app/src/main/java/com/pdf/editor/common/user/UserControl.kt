package com.pdf.editor.common.user

import android.os.Build
import android.os.Bundle
import com.blankj.utilcode.util.DeviceUtils
import com.pdf.editor.BuildConfig
import com.pdf.editor.MyAPP
import com.pdf.editor.common.config.FirebaseManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.point.EventNet
import com.pdf.editor.common.point.EventParamsManager
import com.pdf.editor.common.point.EventParamsManager.urlEncoder
import com.pdf.editor.common.tools.OtherTools
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.DataUtils
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.config.UserLevel
import com.pdf.editor.data.UserReferData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.Proxy

object UserControl {

    var TAG = "UserControl"

    var userReferData: UserReferData?=null

    private const val USER_STATUS_PENDING = "PENDING"

    @Volatile
    var userTimeLimit = RiskVerdict.UNKNOWN
    @Volatile
    var userTestAdLimit = RiskVerdict.NORMAL
    @Volatile
    var userTestLabLimit = RiskVerdict.UNKNOWN
    @Volatile
    var ipResult = RiskVerdict.UNKNOWN

    fun isBuyUser(refer: String = ""): Boolean {
        // B 身份一旦确认就保持不变，避免异步风控结果回来后再次降级为 A。
        if (LocalDataManager.isBUser) {
            AppLogs.dLog(TAG, "正常用户")
            showMedia()
            return true
        }
        val cloakData = getCloakData()
        val referData = getReferData(refer)
        val isRiskyUsers = getUserStatus()
        AppLogs.dLog(
            TAG,
            "cloakData:${cloakData} referData:${referData} isRiskyUsers:${isRiskyUsers}"
        )
        if (isRiskyUsers == USER_STATUS_PENDING) {
            AppLogs.dLog(TAG, "风控检测结果未全部返回，等待异步结果")
            return false
        }
        if (cloakData == "B" && referData == "B" && isRiskyUsers == "B") {
            AppLogs.dLog(TAG, "正常用户:cloakData B referData B")
            LocalDataManager.isBUser = true
            LocalDataManager.userLevel = UserLevel.USER_LEVEL_2.level
            LocalDataManager.lastUserType = 0
            EventManager.addUserType(Bundle().apply {
                putInt("user_bv", 1)
            })
            EventManager.posePoint(PointConfig.user_type, Bundle().apply {
                putInt(PointKey.user, 1)
            })
            showMedia()
            return true
        } else {
            if (LocalDataManager.lastUserType == 0) {
                LocalDataManager.lastUserType = 1
                EventManager.addUserType(Bundle().apply {
                    putInt("user_bv", 0)
                })
                EventManager.posePoint(PointConfig.user_type, Bundle().apply {
                    putInt(PointKey.user, 0)
                })
            }
        }
        return false
    }

    var isFirstShowMedia = true

    private fun showMedia() {
        if (isFirstShowMedia){
            isFirstShowMedia = false
            OtherTools.showNF2()
        }
    }

    private fun getUserStatus(): String {
        var riskData = FirebaseManager.riskData
        if (riskData == null) {
            AppLogs.dLog(TAG, "FirebaseManager.riskData = null")
            return "A"
        }
        val riskDeviceTypes = riskData.device
        var configuredCount = 0
        var finishedCount = 0
        val riskSources = mutableListOf<String>()

        if (riskDeviceTypes.contains("root")) {
            configuredCount++
            finishedCount++
            if (DeviceRiskUtils.isAbnormalEnv || DeviceRiskUtils.isXposed) riskSources.add("root")
        }
        if (riskDeviceTypes.contains("vpn")) {
            configuredCount++
            finishedCount++
            if (DeviceRiskUtils.isVpn) riskSources.add("vpn")
        }
        if (riskDeviceTypes.contains("sim")) {
            configuredCount++
            finishedCount++
            if (!DeviceRiskUtils.isSim(MyAPP.instance)) riskSources.add("sim")
        }
        if (riskDeviceTypes.contains("simulator")) {
            configuredCount++
            finishedCount++
            if (DeviceRiskUtils.isEmulator || DeviceRiskUtils.isEmulator2) riskSources.add("simulator")
        }
        if (riskDeviceTypes.contains("googleplay")) {
            configuredCount++
            finishedCount++
            if (!"com.android.vending".contentEquals(DeviceRiskUtils.getInstaller(MyAPP.instance))) {
                riskSources.add("install")
            }
        }
        if (riskDeviceTypes.contains("developer")) {
            configuredCount++
            finishedCount++
            if (DeviceRiskUtils.isDevModel(MyAPP.instance) || DeviceRiskUtils.isDebug(MyAPP.instance)) {
                riskSources.add("developer")
            }
        }
        if (riskDeviceTypes.contains("ip") && riskData.ip == 1) {
            configuredCount++
            if (ipResult != RiskVerdict.UNKNOWN) {
                finishedCount++
                if (ipResult != RiskVerdict.NORMAL) riskSources.add("ip")
            }
        }
        if (riskDeviceTypes.contains("timeTampered")) {
            configuredCount++
            if (userTimeLimit != RiskVerdict.UNKNOWN) {
                finishedCount++
                if (userTimeLimit != RiskVerdict.NORMAL) riskSources.add("timeTampered")
            }
        }
        if (riskDeviceTypes.contains("testAd")) {
            configuredCount++
            finishedCount++
            if (userTestAdLimit != RiskVerdict.NORMAL) riskSources.add("testAd")
        }
        if (riskDeviceTypes.contains("testLab")) {
            configuredCount++
            userTestLabLimit = AdditionalRiskDetector.testLab(MyAPP.instance)
            if (userTestLabLimit != RiskVerdict.UNKNOWN) {
                finishedCount++
                if (userTestLabLimit != RiskVerdict.NORMAL) riskSources.add("testLab")
            }
        }
        if (finishedCount < configuredCount) {
            AppLogs.dLog(TAG, "风控检测未完成 finished:$finishedCount configured:$configuredCount")
            return USER_STATUS_PENDING
        }
        riskSources.forEach(::updateUserStatus)
        return if (riskSources.isNotEmpty()) "A" else "B"
    }

    private fun updateUserStatus(from: String): String {
        EventManager.posePoint(PointConfig.risk_control, Bundle().apply {
            putString("risk_type", from)
        })
        AppLogs.dLog(
            TAG,
            "风控判断为A包:${from}"
        )
        return "A"
    }

    var cloakValue = ""

    fun getCloakData(): String {
        if (cloakValue.isNotEmpty()) {
            if (cloakValue.equals("gland", true)) {
                AppLogs.dLog(TAG, "cloak 命中正常用户")
                return "B"
            } else {
                AppLogs.dLog(TAG, "cloak 命中黑名单用户")
                return "A"
            }
        }
        return "A"
    }


    private fun getReferData(tempRefer: String = ""): String {
        val refer = tempRefer.ifEmpty { LocalDataManager.installRefer }
        val door = userReferData?.door ?: 1
        if (door == 1) {
            val configList = userReferData?.ilve ?: mutableListOf()
            val index = configList.indexOfFirst { refer.contains(it, true) }
            if (index >= 0) {
                AppLogs.dLog(TAG, "refer 命中买量 match:${configList[index]}")
                return "B"
            }
        } else {
            AppLogs.dLog(TAG, "refer 跳过 直接命中B")
            return "B"
        }
        var buyAF = LocalDataManager.afFrom.equals("Organic", true).not()
        if (buyAF) {
            AppLogs.dLog(TAG, "buyAF 命中买量 match:${buyAF}")
            return "B"
        }
        AppLogs.dLog(TAG, "归因未命中买量当前为非买量用户")
        return "A"
    }

    fun getCloak(){
        if (LocalDataManager.isBUser)return
        EventManager.posePoint(PointConfig.cloak_req)
        MyAPP.instance.applicationScope.launch {
            var url =
                "https://vantage.nogappdfreaderscanner.com/exorcist/those?ridgway=${urlEncoder(BuildConfig.APPLICATION_ID)}" +
                        "&female=dent&seasonal=${urlEncoder(BuildConfig.VERSION_NAME)}&sidestep=${urlEncoder(EventParamsManager.getDeviceID())}" +
                        "&creep=${System.currentTimeMillis()}&hayden=${urlEncoder(DeviceUtils.getModel())}&crypt=${urlEncoder(Build.VERSION.RELEASE)}" +
                        "&meant=${urlEncoder(EventParamsManager.getGID())}&mock=${urlEncoder(EventParamsManager.getDeviceID())}"
            getCloakRequest(url,"getClock", callBack = {
                cloakValue = it
                isBuyUser()
                poseUserStatusByCloak()
            })
        }
    }

    var clockTryCount = 20

    fun getCloakRequest(url:String, tag:String?=null, callBack: (content:String) -> Unit={}){
        val request = Request.Builder().get().url(url)
        EventNet.getPonitNet().newCall(request.build())?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                cloakFailedTry(url,tag,callBack)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code == 200){
                    val bodyStr = response.body?.string() ?: ""
                    callBack.invoke(bodyStr)
                }  else cloakFailedTry(url,tag,callBack)
            }
        })
    }

    fun cloakFailedTry(url: String,tag:String?=null,callBack: (content:String) -> Unit={}) {
        if (clockTryCount > 0) {
            clockTryCount--
            MyAPP.instance.applicationScope.launch {
                delay(1000L)
                getCloakRequest(url,tag,callBack)
            }
        }
    }

    var ipTryCount = 20

    fun getIP() {
        if (LocalDataManager.isBUser) return
        MyAPP.instance.applicationScope.launch(Dispatchers.IO) {
            getIPRequest()
        }
    }

    suspend fun getIPRequest() {
        var builder = OkHttpClient.Builder()
        val JSON = "application/json; charset=utf-8".toMediaType()
        val jsonBody = """{"azebra": "${EventParamsManager.getDeviceID()}"}"""
        val requestBody = jsonBody.toRequestBody(JSON)
        val request = Request.Builder()
            .url("https://ip-prod.pdfhubpapertrans.com/api/cbat")
            .post(requestBody)
            .build()
        if (!BuildConfig.DEBUG) {
            builder.proxy(Proxy.NO_PROXY)
        }
        try {
            builder.build().newCall(request).execute().use { response ->
                if (response.code == 200) {
                    val bodyStr = response.body.string()
                    var decodeBody = DataUtils.decrypt(bodyStr, 58)
                    AppLogs.dLog(TAG, "decodeBody:${decodeBody}")
                    val rootObject = JSONObject(decodeBody)
                    val dataObject = rootObject.optJSONObject("data")
                    val status = dataObject?.optBoolean("bmonkey", false) ?: false
                    ipResult = if (status) RiskVerdict.NORMAL else RiskVerdict.RISK
                    withContext(Dispatchers.Main) {
                        isBuyUser()
                    }
                } else {
                    ipFailedTry()
                }
            }
        } catch (e: Exception) {
            ipFailedTry()
        }
    }

    suspend fun ipFailedTry() {
        if (ipTryCount > 0) {
            ipTryCount--
            delay(1000L)
            getIPRequest()
        }
    }

    fun poseUserStatusByRefer(refer: String) {
        val door = userReferData?.door ?: 1
        if (door == 0) {
            EventManager.posePoint(PointConfig.reffer_suc, Bundle().apply {
                putInt(PointKey.reffer_user, 1)
            })
            return
        }
        val configList = userReferData?.ilve ?: mutableListOf()
        val index = configList.indexOfFirst { refer.contains(it, true) }
        if (index >= 0) {
//            AppLogs.dLog(TAG, "refer 命中买量 match:${configList.get(index)}")
            //b
            EventManager.posePoint(PointConfig.reffer_suc, Bundle().apply {
                putInt(PointKey.reffer_user,1)
            })
        }else{
            //a
            EventManager.posePoint(PointConfig.reffer_suc, Bundle().apply {
                putInt(PointKey.reffer_user,0)
            })
        }
    }

    fun poseUserStatusByCloak() {
        if (cloakValue.isNotEmpty()) {
            if (cloakValue.equals("gland", true)) {
//                AppLogs.dLog(TAG, "cloak 命中正常用户")
                EventManager.posePoint(PointConfig.cloak_suc, Bundle().apply {
                    putInt(PointKey.cloak_user,1)
                })
            } else {
                EventManager.posePoint(PointConfig.cloak_suc, Bundle().apply {
                    putInt(PointKey.cloak_user,0)
                })
            }
        }
    }

    /** 原生广告加载完成后保存检测结果，并重新判断用户身份。 */
    fun getTestAd(headline: String?) {
        if (LocalDataManager.isBUser) return
        userTestAdLimit = AdditionalRiskDetector.testAd(MyAPP.instance, headline)
        isBuyUser()
    }

    fun getTime() {
        MyAPP.instance.applicationScope.launch {
            val serviceTime = TrustedTimeHolder.fetchServiceTime()
            userTimeLimit = if (TrustedTimeHolder.isWithinFiveMinutesOfNow(serviceTime)) {
                RiskVerdict.NORMAL
            } else {
                RiskVerdict.RISK
            }
            isBuyUser()
        }
    }

    fun uploadRisk() {
        MyAPP.instance.applicationScope.launch {
            var root = if ((DeviceRiskUtils.isAbnormalEnv || DeviceRiskUtils.isXposed)) 1 else 0
            var vpn = if (DeviceRiskUtils.isVpn) 1 else 0
            var sim = if (!DeviceRiskUtils.isSim(MyAPP.instance)) 0 else 1
            var simulator = if (DeviceRiskUtils.isEmulator || DeviceRiskUtils.isEmulator2) 1 else 0
            var install =
                if (!"com.android.vending".contentEquals(DeviceRiskUtils.getInstaller(MyAPP.instance))) 0 else 1
            var developer =
                if (DeviceRiskUtils.isDevModel(MyAPP.instance) || DeviceRiskUtils.isDebug(MyAPP.instance)) 1 else 0

            EventManager.posePoint("session_custom", Bundle().apply {
                putInt("root", root)
                putInt("vpn", vpn)
                putInt("sim", sim)
                putInt("simulator", simulator)
                putInt("googleplay", install)
                putInt("developer", developer)
            })
        }
    }
}
