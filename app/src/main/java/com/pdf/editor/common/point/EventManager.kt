package com.pdf.editor.common.point

import android.os.Build
import android.os.Bundle
import com.android.installreferrer.api.ReferrerDetails
import com.pdf.editor.MyAPP
import com.pdf.editor.common.config.FirebaseManager
import com.pdf.editor.common.install.InstallManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.cache.LocalDataManager
import kotlinx.coroutines.launch
import okhttp3.Response
import org.json.JSONObject

object EventManager {
    fun posePoint(key:String, bundle: Bundle?=null,needFireBase:Boolean=true,callback: EventNet.PointCallback?=null) {
//        AppLogs.dLog(NET_TAG,"posePoint tag:${key}")
        MyAPP.instance.applicationScope.launch{
            val jsonObject = EventParamsManager.getGenericParams()
            jsonObject.put("haifa", key)
            bundle?.keySet()?.forEach {
                jsonObject.put("kenneth#$it", bundle.get(it))
            }

            EventNet.postEvent(jsonObject,tag = key,callback)
            if (needFireBase){
                runCatching {
                    FirebaseManager.firebaseAnalytics.logEvent(key, bundle)
                }.onFailure {
                    it.stackTraceToString()
                }
            }
        }
    }

    fun addUserType(bundle: Bundle?=null){
        MyAPP.instance.applicationScope.launch{
            val jsonObject = EventParamsManager.getGenericParams()
            jsonObject.put("haifa", "paso")
            bundle?.keySet()?.forEach {
                jsonObject.put("kenneth#$it", bundle.get(it))
            }
            EventNet.postEvent(jsonObject)
        }
    }

    var isInstallLoading = false

    /**
     * install
     */
    fun install(referrerDetails: ReferrerDetails? = null) {
        if (LocalDataManager.installReferNet.isNullOrEmpty() && isInstallLoading.not()){
            isInstallLoading = true
            MyAPP.instance.applicationScope.launch{
                val jsonObject = EventParamsManager.getGenericParams().apply {
                    put("haifa", "spain")
                    put("scruffy", "build/${Build.ID}")
                    put("cackle", referrerDetails?.installReferrer ?: "")
                    put("darkle", EventParamsManager.getWebUA())
                    put("bendix", "syllabic")
                    put("goucher", referrerDetails?.referrerClickTimestampSeconds ?: 0)
                    put("nozzle", referrerDetails?.installBeginTimestampSeconds ?: 0)
                    put("hideous", referrerDetails?.referrerClickTimestampServerSeconds ?: 0)
                    put("book", referrerDetails?.installBeginTimestampServerSeconds ?: 0)
                    put("fluster", EventParamsManager.pkgInfo.firstInstallTime / 1000)
                    put("m", EventParamsManager.pkgInfo.lastUpdateTime / 1000)
                }
                EventNet.postEvent(jsonObject,"install",object : EventNet.PointCallback {
                    override fun onSuccess(response: Response) {
                        AppLogs.dLog(InstallManager.TAG,"install打点成功 refer:${referrerDetails?.installReferrer?:""}")
                        LocalDataManager.installReferNet = referrerDetails?.installReferrer?:""
                        FirebaseManager.updateADConfig()
//                    getADConfig(firebaseRemoteConfig,"requestRefer")
                    }
                })
            }

        }
    }

    /**
     * session
     */
    fun session(){
        MyAPP.instance.applicationScope.launch{
            EventNet.postEvent(EventParamsManager.getGenericParams().apply {
                put("greater", JSONObject())
            },"session")
        }

    }

    fun adImpression(map: HashMap<String, Any>){
        MyAPP.instance.applicationScope.launch{
            val jsonObject = EventParamsManager.getGenericParams().apply {
                put("haifa", "given")
            }
            map.forEach { key, value->
                jsonObject.put(key,value)
            }
            EventNet.postEvent(jsonObject,"adImpression")
        }
    }

}

