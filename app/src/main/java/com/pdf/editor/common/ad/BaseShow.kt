package com.pdf.editor.common.ad

import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import com.appsflyer.AFAdRevenueData
import com.appsflyer.AdRevenueScheme
import com.appsflyer.AppsFlyerLib
import com.appsflyer.MediationNetwork
import com.pdf.editor.BuildConfig
import com.pdf.editor.MyAPP
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.config.DefaultConfig
import com.pdf.editor.common.config.FirebaseManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey

import com.pdf.editor.data.ADResultData

import com.google.android.gms.ads.AdValue
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.firebase.analytics.FirebaseAnalytics
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs

import kotlinx.coroutines.launch
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Currency

abstract class BaseShow(var currentADEnum: ADEnum,var activity: BaseActivity<*>, var tag: String,var result: (type: String, hasValue:Boolean) -> Unit){

    var hasValue = false


    abstract fun showScreenAD(adResultData: ADResultData, adEnum: ADEnum, pointTag: String)
    abstract fun showNativeAD(flRoot: FrameLayout,adResultData: ADResultData, adEnum: ADEnum, adViewType: Int,pointTag: String)
    abstract fun showNativeScreenAD(flRoot: FrameLayout,adResultData: ADResultData, adEnum: ADEnum, pointTag: String)
    abstract fun showBannerAD(parent: ViewGroup, adResultData: ADResultData, adEnum: ADEnum,pointTag:String)
    abstract fun destroyNative(pointTag:String)


    fun adShowFailed(log: String,adEnum: ADEnum,pointTag: String) {
        AppLogs.dLog(ADControl.TAG,"adShowFailed log:${log},pointTag:${pointTag} ")
        loadComplete(ADControl.AD_SHOW_TYPE_FAILED, log,adEnum,pointTag)
    }

    fun adDismissFullScreen(log: String,adResultData: ADResultData,adEnum: ADEnum, pointTag: String) {
        AppLogs.dLog(ADControl.TAG,"adDismissFullScreen log:${log},pointTag:${pointTag} ")
        EventManager.posePoint(PointConfig.ad_close, Bundle().apply {
            putString(PointKey.scene,currentADEnum.adName)
            putString(PointKey.ad_format,adResultData.adRequestData?.iwn?:"")
            putString(PointKey.ad_code_id,adResultData.adRequestData?.jsk?:"")
            putString(PointKey.ad_pos_id,pointTag)
            putString(PointKey.ad_network,adResultData.adNetWork)
            putString(PointKey.ad_source_client,adResultData.adRequestData?.iwk?:"")
            putLong(PointKey.close_time_long, adResultData.getCloseTimeLong())
        })
        ADControl.lastScreenDismissTime = System.currentTimeMillis()
        loadComplete(type = ADControl.AD_SHOW_TYPE_SUCCESS, log,adEnum,pointTag)
    }

    fun adShowFullScreen(log: String, pointTag: String, adResultData: ADResultData) {
        AppLogs.dLog(ADControl.TAG,"adShowFullScreen log:${log},pointTag:${pointTag} ")
        adResultData.markShown()
        // 开屏限额只累计开屏广告的实际展示，插屏等其他全屏广告不占用该限额。
        if (ADControl.isOpenAd(currentADEnum)) {
            LocalDataManager.openAdShowEveryDay = LocalDataManager.openAdShowEveryDay + 1
        }
        LocalDataManager.lastScreenImpressionTime = System.currentTimeMillis()
    }

    fun loadComplete(type: String, log: String, adEnum: ADEnum,pointTag: String) {
        AppLogs.dLog(ADControl.TAG,"loadComplete log:${log},type:${type},pointTag:${pointTag} ")
        result.invoke(type,hasValue)
    }

    private fun sendAdmobValue(admobValue: AdValue, adResultData: ADResultData, adUnitId: String, type:String, pointTag:String) {
        MyAPP.instance.applicationScope.launch {
            var net = adResultData.adNetWork
            var countryCode = if (admobValue.currencyCode.isNullOrEmpty()){
                "USD"
            }else{
                admobValue.currencyCode
            }

            adResultData.adAny?.apply {
                if (this is AppOpenAd){
                    net = ADNetworkName.fromAdmob(responseInfo)
                }else if (this is InterstitialAd){
                    net = ADNetworkName.fromAdmob(responseInfo)
                }else if (this is RewardedAd){
                    net = ADNetworkName.fromAdmob(responseInfo)
                }else if (this is AdView){
                    net = ADNetworkName.fromAdmob(responseInfo)
                }else if (this is NativeAd){
                    net = ADNetworkName.fromAdmob(responseInfo)
                }
            }
            adResultData.adNetWork = net
            val valueMicros = if (BuildConfig.DEBUG) 1 * 100_000 else admobValue.valueMicros
            val detailValueMicros = BigDecimal(if (BuildConfig.DEBUG) 1 * 100_000 else valueMicros).divide(
                BigDecimal(1_000_000),10,
                BigDecimal.ROUND_HALF_UP).toDouble()
            reportAdRevenue(
                adResultData = adResultData,
                valueMicros = valueMicros,
                detailValueMicros = detailValueMicros,
                countryCode = countryCode,
                net = net,
                platform = ADControl.AD_PLATFORM_ADMOB,
                adUnitId = adUnitId,
                type = type,
                pointTag = pointTag
            )
        }
    }

    private fun reportAdRevenue(
        adResultData: ADResultData,
        valueMicros: Long,
        detailValueMicros: Double,
        countryCode: String,
        net: String,
        platform: String,
        adUnitId: String,
        type: String,
        pointTag: String
    ) {
        EventManager.adImpression(HashMap<String, Any>().apply {
            put("beatify", valueMicros)
            put("frill", countryCode)
            put("script", net)
            put("ashmen", platform)
            put("minotaur", adUnitId)
            put("incident", pointTag)
            put("lord", type)
            put("scm", currentADEnum.adName)
        })
        if (adResultData.adShowReported.compareAndSet(false, true)) {
            EventManager.posePoint(PointConfig.ad_show, Bundle().apply {
                putString(PointKey.scene,currentADEnum.adName)
                putString(PointKey.ad_format,adResultData.adRequestData?.iwn?:"")
                putString(PointKey.ad_code_id,adResultData.adRequestData?.jsk?:"")
                putString(PointKey.ad_network,adResultData.adNetWork)
                putString(PointKey.ad_pos_id,pointTag)
                putString(PointKey.ad_source, ADControl.adSource)
                putString(PointKey.ad_source_client, adResultData.adRequestData?.iwk)
            })
        }
        total001(detailValueMicros,countryCode)
        runCatching {
            val adRevenueData = AFAdRevenueData(
                net,
                MediationNetwork.GOOGLE_ADMOB,
                countryCode,
                detailValueMicros
            )
            val additionalParameters = mapOf(
                AdRevenueScheme.AD_UNIT to adUnitId,
                AdRevenueScheme.AD_TYPE to type,
                AdRevenueScheme.PLACEMENT to pointTag
            )
            AppsFlyerLib.getInstance().logAdRevenue(adRevenueData, additionalParameters)
        }
        runCatching {
            FirebaseManager.fb.logPurchase(detailValueMicros.toBigDecimal(), Currency.getInstance(countryCode))
        }

        EventManager.posePoint("ad_impression_revenue",Bundle().apply {
            putDouble("${FirebaseAnalytics.Param.VALUE}",detailValueMicros)
            putString("${FirebaseAnalytics.Param.CURRENCY}",countryCode)
        }, needFireBase = false)
        runCatching {
            FirebaseManager.firebaseAnalytics.logEvent("ad_impression_revenue", Bundle().apply {
                putDouble(FirebaseAnalytics.Param.VALUE, detailValueMicros)
                putString(FirebaseAnalytics.Param.CURRENCY,countryCode)
            })
        }.onFailure {
            it.stackTraceToString()
        }
        runCatching {
            var dayValue = LocalDataManager.adDayValue
            var newValue = dayValue+detailValueMicros
            LocalDataManager.adDayValue = newValue
            val result = getLTVList()
            result.forEachIndexed { index, value ->
                if ( dayValue< value && newValue >= value) {
                    val name =  getPostName().get(index)
                    name?.let {
                        EventManager.posePoint(it,Bundle().apply {
                            putDouble(FirebaseAnalytics.Param.VALUE, value)
                            putString(FirebaseAnalytics.Param.CURRENCY,countryCode)
                        })
                    }
                }
            }
        }
        runCatching {
            var dayEcpm = LocalDataManager.adDayEcpm
            var newEcpm = dayEcpm+valueMicros
            LocalDataManager.adDayEcpm = newEcpm
            val result = getEcpmList()
            result.forEachIndexed { index, value ->
                if ( dayEcpm< value && newEcpm >= value) {
                    val name =  getEcpmPostName().get(index)
                    name?.let {
                        EventManager.posePoint(it,Bundle().apply {
                            putLong(FirebaseAnalytics.Param.VALUE, value)
                            putString(FirebaseAnalytics.Param.CURRENCY,countryCode)
                        })
                    }
                }
            }
        }
    }
//
//    private fun adValueToGG(countryCode: String, detailValueMicros: Double) {
//        var bundle = Bundle()
//        bundle.putString(FirebaseAnalytics.Param.CURRENCY, countryCode)
//        bundle.putDouble(FirebaseAnalytics.Param.VALUE, detailValueMicros)
//        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, bundle)
//    }

    fun register(adResultData: ADResultData, pointTag: String) {
        adResultData.adPointTag = pointTag
        when (adResultData.adAny) {
            is AppOpenAd -> {
                (adResultData.adAny as AppOpenAd).setOnPaidEventListener {
                    sendAdmobValue(it,adResultData,adResultData.adRequestData?.jsk?:"","open",pointTag)
                }
            }
            is InterstitialAd->{
                (adResultData.adAny as InterstitialAd).setOnPaidEventListener {
                    sendAdmobValue(it,adResultData,adResultData.adRequestData?.jsk?:"","interstitial",pointTag)
                }
            }

            is RewardedAd->{
                (adResultData.adAny as RewardedAd).setOnPaidEventListener {
                    sendAdmobValue(it,adResultData,adResultData.adRequestData?.jsk?:"","reward",pointTag)
                }
            }
            is AdView->{
                (adResultData.adAny as AdView).setOnPaidEventListener {
                    sendAdmobValue(it,adResultData,adResultData.adRequestData?.jsk?:"","banner",pointTag)
                }
            }
            is NativeAd->{
                (adResultData.adAny as NativeAd).setOnPaidEventListener {
                    sendAdmobValue(it,adResultData,adResultData.adRequestData?.jsk?:"","native",pointTag)
                }
            }
            else -> {}
        }
    }


    fun total001(revenue:Double,countryCode:String){
        //001
        runCatching {
            val newCurrInt = LocalDataManager.ad001 + revenue
            AppLogs.dLog(ADControl.TAG,"total001 当前累计:${newCurrInt}")
            if (newCurrInt >= 0.01) {
                EventManager.posePoint(PointConfig.pr_total_001_revenue, Bundle().apply {
                    putDouble(FirebaseAnalytics.Param.VALUE,newCurrInt)
                    putString(FirebaseAnalytics.Param.CURRENCY,countryCode)
                })
                LocalDataManager.ad001 = 0.0
            } else {
                LocalDataManager.ad001=newCurrInt
            }
        }
    }

    /**
     * "reader_oneday_top10":1,
     * "reader_oneday_top20":0.8,
     * "reader_oneday_top30":0.6,
     * "reader_oneday_top40":0.5,
     * "reader_oneday_top50":0.1
     */

    fun getLTVList(): MutableList<Double> {
        val ad = mutableListOf<Double>()
        val jsonObject = JSONObject(FirebaseManager.ltvConfig)
        ad.add(0, jsonObject.getDouble("reader_oneday_top50"))
        ad.add(1, jsonObject.getDouble("reader_oneday_top40"))
        ad.add(2, jsonObject.getDouble("reader_oneday_top30"))
        ad.add(3, jsonObject.getDouble("reader_oneday_top20"))
        ad.add(4, jsonObject.getDouble("reader_oneday_top10"))
        return ad
    }

    fun getEcpmList(): MutableList<Long> {
        val ad = mutableListOf<Long>()
        val jsonObject = JSONObject(DefaultConfig.ECPM_DEFAULT)
        ad.add(0, jsonObject.getLong("Adecpm_OneDay_100"))
        ad.add(1, jsonObject.getLong("Adecpm_OneDay_150"))
        ad.add(2, jsonObject.getLong("Adecpm_OneDay_200"))
        return ad
    }

    fun getPostName(): MutableMap<Int, String> {
        val map = mutableMapOf<Int, String>().apply {
            put(0, "AdLTV_OneDay_Top50Percent")
            put(1, "AdLTV_OneDay_Top40Percent")
            put(2, "AdLTV_OneDay_Top30Percent")
            put(3, "AdLTV_OneDay_Top20Percent")
            put(4, "AdLTV_OneDay_Top10Percent")
        }
        return map
    }

    fun getEcpmPostName(): MutableMap<Int, String> {
        val map = mutableMapOf<Int, String>().apply {
            put(0, "Adecpm_OneDay_100")
            put(1, "Adecpm_OneDay_150")
            put(2, "Adecpm_OneDay_200")
        }
        return map
    }

}
