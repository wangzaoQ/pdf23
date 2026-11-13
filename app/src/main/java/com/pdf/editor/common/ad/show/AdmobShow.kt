package com.pdf.editor.common.ad.show

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.get
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.google.android.gms.ads.rewarded.RewardedAd
import com.pdf.editor.R
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.BaseShow
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.setOneClick
import com.pdf.editor.data.ADResultData
import com.pdf.editor.databinding.AdNative2Binding
import com.pdf.editor.databinding.AdNativeScreenBinding

class AdmobShow(
    currentADEnum: ADEnum,
    activity: BaseActivity<*>,
    tag: String,
    result: (type: String, hasValue: Boolean) -> Unit,
) : BaseShow(currentADEnum, activity, tag, result) {
    private var nativeAd: NativeAd? = null
    private var nativeAdView: NativeAdView? = null

    override fun showScreenAD(data: ADResultData, placement: ADEnum, pointTag: String) {
        val callback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent(){
                adShowFullScreen(logInfo(placement, data), pointTag, data)
            }

            override fun onAdClicked() {
                ADControl.onAdClicked(placement, data.adRequestData, pointTag, data.adNetWork)
            }

            override fun onAdDismissedFullScreenContent() {
                ADControl.fullScreenAdShowing = false
                adDismissFullScreen(logInfo(placement, data), data, placement, pointTag)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                ADControl.fullScreenAdShowing = false
                adShowFailed(logInfo(placement, data), placement, pointTag)
                ADControl.poseAdImpressionFail(placement, data, pointTag, error.code.toString(), error.message)
            }
        }
        ADControl.fullScreenAdShowing = true
        runCatching {
            when (data.adRequestData?.iwn) {
                ADControl.AD_TYPE_OPEN -> (data.adAny as? AppOpenAd)?.apply {
                    fullScreenContentCallback = callback
                    show(activity)
                }
                ADControl.AD_TYPE_INT -> (data.adAny as? InterstitialAd)?.apply {
                    fullScreenContentCallback = callback
                    show(activity)
                }
                ADControl.AD_TYPE_RV -> (data.adAny as? RewardedAd)?.apply {
                    fullScreenContentCallback = callback
                    show(activity) { }
                }
                else-> {
                    adShowFailed("unsupported fullscreen ad", placement, pointTag)
                    ADControl.fullScreenAdShowing = false
                }
            }
        }.onFailure {
            AppLogs.dLog(ADControl.TAG,"全屏展示失败")
            ADControl.fullScreenAdShowing = false
        }

    }

    override fun showNativeAD(root: FrameLayout, data: ADResultData, placement: ADEnum, viewType: Int, pointTag: String) {
        val ad = data.adAny as? NativeAd ?: return failedNative(placement, data, pointTag)
        nativeAd = ad
        nativeAdView = if (root.childCount == 1 && root[0] is NativeAdView) {
            root[0] as NativeAdView
        } else {
            if (viewType == 0) AdNative2Binding.inflate(activity.layoutInflater).root
            else AdNative2Binding.inflate(activity.layoutInflater).root
        }
        root.removeAllViews()
        root.addView(nativeAdView)
        root.visibility = View.VISIBLE
        bindNativeAd(ad, nativeAdView!!, pointTag, false)
        activity.life.destoryList.add { if (it == 0) destroyNative(pointTag) }
    }

    override fun showNativeScreenAD(
        flRoot: FrameLayout,
        adResultData: ADResultData,
        adEnum: ADEnum,
        pointTag: String
    ) {
        val ad = adResultData.adAny as? NativeAd
            ?: return failedNative(adEnum, adResultData, pointTag)
        nativeAd = ad
        nativeAdView = if (flRoot.childCount == 1 && flRoot[0] is NativeAdView) {
            flRoot[0] as NativeAdView
        } else {
            AdNativeScreenBinding.inflate(activity.layoutInflater).root.also {
                flRoot.removeAllViews()
                flRoot.addView(it)
            }
        }
        flRoot.visibility = View.VISIBLE
        adResultData.markShown()
        nativeAdView?.let { view ->
            bindNativeAd(ad, view, pointTag, true)
            view.findViewById<MediaView>(R.id.mediaView)?.apply {
                ad.mediaContent?.let { mediaContent = it }
                view.mediaView = this
            }
            view.findViewById<ImageView>(R.id.ivTips)?.apply {
                visibility = View.VISIBLE
                setOneClick {
                    EventManager.posePoint(PointConfig.ad_close, Bundle().apply {
                        putString(PointKey.scene,currentADEnum.adName)
                        putString(PointKey.ad_format,adResultData.adRequestData?.iwn?:"")
                        putString(PointKey.ad_code_id,adResultData.adRequestData?.jsk?:"")
                        putString(PointKey.ad_pos_id,pointTag)
                        putString(PointKey.ad_network,adResultData.adNetWork)
                        putString(PointKey.ad_source_client,adResultData.adRequestData?.iwk?:"")
                        putLong(PointKey.close_time_long, adResultData.getCloseTimeLong())
                    })
                    loadComplete(ADControl.AD_SHOW_TYPE_SUCCESS, "native ad_close click", currentADEnum, pointTag)
                }
            }
        }
        activity.life.destoryList.add { if (it == 0) destroyNative(pointTag) }
    }

    private fun bindNativeAd(ad: NativeAd, view: NativeAdView, pointTag: String, screen: Boolean) {
        view.findViewById<TextView>(R.id.tvTitle)?.apply { text = ad.headline; view.headlineView = this }
        view.findViewById<TextView>(R.id.tvBody)?.apply { text = ad.body; view.bodyView = this }
        view.findViewById<TextView>(R.id.tvConfirm)?.apply {
            visibility = if (ad.callToAction == null) View.INVISIBLE else View.VISIBLE
            text = ad.callToAction
            view.callToActionView = this
        }
        view.findViewById<ImageView>(R.id.ivIcon)?.apply {
            ad.icon?.let { setImageDrawable(it.drawable); view.iconView = this }
        }
        view.setNativeAd(ad)
    }

    override fun showBannerAD(parent: ViewGroup, data: ADResultData, placement: ADEnum, pointTag: String) {
        val adView = data.adAny as? AdView ?: return failedNative(placement, data, pointTag)
        (adView.parent as? ViewGroup)?.removeView(adView)
        parent.visibility = View.VISIBLE
        parent.addView(adView)
        ADControl.addChance(placement, pointTag)
        activity.life.destoryList.add {
            if (it == 0) {
                adView.destroy()
                parent.removeView(adView)
                data.adAny = null
            }
        }
    }

    override fun destroyNative(pointTag: String) {
        nativeAd?.destroy()
        (nativeAdView?.parent as? ViewGroup)?.removeView(nativeAdView)
        nativeAdView?.destroy()
        nativeAd = null
        nativeAdView = null
        loadComplete(ADControl.AD_SHOW_TYPE_SUCCESS, "native closed", currentADEnum, pointTag)
    }

    private fun failedNative(placement: ADEnum, data: ADResultData, pointTag: String) {
        loadComplete(ADControl.AD_SHOW_TYPE_FAILED, "native ad unavailable", placement, pointTag)
        ADControl.poseAdImpressionFail(placement, data, pointTag, "", ADControl.AD_IMPRESSION_FAIL_NOT_PREPARED)
    }
}

private fun logInfo(placement: ADEnum, data: ADResultData) = "admob ${placement.adName}:${data.adRequestData?.jsk}"
