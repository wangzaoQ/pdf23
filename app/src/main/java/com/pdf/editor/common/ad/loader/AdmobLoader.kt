package com.pdf.editor.common.ad.loader

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.WindowMetrics
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.pdf.editor.MyAPP
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADNetworkName
import com.pdf.editor.common.ad.BaseLoader
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.data.ADFailData
import com.pdf.editor.data.ADRequestData
import com.pdf.editor.data.ADResultData

class AdmobLoader(
    requestBean: ADRequestData,
    adEnum: ADEnum,
) : BaseLoader(requestBean, adEnum) {
    override fun openAD() {
        val startedAt = System.currentTimeMillis()
        AppOpenAd.load(
            MyAPP.instance,
            requestBean.jsk,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) = loadSucceeded(ad.toResult(startedAt))
                override fun onAdFailedToLoad(error: LoadAdError) = loadFailed(error.toFailData())
            },
        )
    }

    override fun intAD() {
        val startedAt = System.currentTimeMillis()
        InterstitialAd.load(
            MyAPP.instance,
            requestBean.jsk,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) = loadSucceeded(ad.toResult(startedAt))
                override fun onAdFailedToLoad(error: LoadAdError) = loadFailed(error.toFailData())
            },
        )
    }

    override fun rewarded() {
        val startedAt = System.currentTimeMillis()
        RewardedAd.load(
            MyAPP.instance,
            requestBean.jsk,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) = loadSucceeded(ad.toResult(startedAt))
                override fun onAdFailedToLoad(error: LoadAdError) = loadFailed(error.toFailData())
            },
        )
    }

    override fun nativeAD() {
        val startedAt = System.currentTimeMillis()
        var result: ADResultData? = null
        AdLoader.Builder(MyAPP.instance, requestBean.jsk)
            .forNativeAd { ad ->
                com.pdf.editor.common.user.UserControl.getTestAd(ad.headline)
                result = ad.toResult(startedAt)
                loadSucceeded(result!!)
            }
            .withNativeAdOptions(NativeAdOptions.Builder().build())
            .withAdListener(object : AdListener() {
                override fun onAdImpression() {
                }

                override fun onAdClicked() {
                    ADControl.onAdClicked(
                        loadAdEnum,
                        requestBean,
                        result?.adPointTag.orEmpty(),
                        result?.adNetWork.orEmpty(),
                    )
                }

                override fun onAdFailedToLoad(error: LoadAdError) = loadFailed(error.toFailData())
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    override fun bannerAD() {
        val activity = MyAPP.instance.lifecycleApp.stack.lastOrNull() as? Activity
            ?: return loadFailed("activity_unavailable", "No active activity for banner")
        val startedAt = System.currentTimeMillis()
        val adView = AdView(activity)
        val result = ADResultData()
        val widthDp = if (ADControl.width == 0) getWidth(activity) else px2dp(activity, ADControl.width).toInt()
        adView.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        adView.adUnitId = requestBean.jsk
        adView.adListener = object : AdListener() {
            override fun onAdClicked() {
                ADControl.onAdClicked(loadAdEnum, requestBean, result.adPointTag, result.adNetWork)
            }

            override fun onAdLoaded() {
                result.adRequestData = requestBean
                result.adAny = adView
                result.adType = requestBean.iwn
                result.adRequestTime = (System.currentTimeMillis() - startedAt) / 1_000
                result.adNetWork = ADNetworkName.fromAdmob(adView.responseInfo)
                loadSucceeded(result)
            }

            override fun onAdFailedToLoad(error: LoadAdError) = loadFailed(error.toFailData())
        }
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun AppOpenAd.toResult(startedAt: Long) = result(this, startedAt, ADNetworkName.fromAdmob(responseInfo))
    private fun InterstitialAd.toResult(startedAt: Long) = result(this, startedAt, ADNetworkName.fromAdmob(responseInfo))
    private fun RewardedAd.toResult(startedAt: Long) = result(this, startedAt, ADNetworkName.fromAdmob(responseInfo))
    private fun com.google.android.gms.ads.nativead.NativeAd.toResult(startedAt: Long) =
        result(this, startedAt, ADNetworkName.fromAdmob(responseInfo))

    private fun result(ad: Any, startedAt: Long, network: String) = ADResultData().apply {
        adRequestData = requestBean
        adAny = ad
        adType = requestBean.iwn
        adRequestTime = (System.currentTimeMillis() - startedAt) / 1_000
        adNetWork = network
    }

    private fun px2dp(context: Context, pixels: Int): Float = pixels / context.resources.displayMetrics.density

    private fun getWidth(activity: Activity): Int {
        val displayMetrics = activity.resources.displayMetrics
        val pixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics: WindowMetrics = activity.windowManager.currentWindowMetrics
            windowMetrics.bounds.width()
        } else {
            displayMetrics.widthPixels
        }
        return (pixels / displayMetrics.density).toInt()
    }
}

private fun LoadAdError.toFailData() = ADFailData().apply {
    error_code = code.toString()
    error_message = message
    adNetwork = ADNetworkName.fromAdmobFail(responseInfo)
}
