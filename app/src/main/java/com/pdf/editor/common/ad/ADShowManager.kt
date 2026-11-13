package com.pdf.editor.common.ad

import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.ad.show.AdmobShow
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.jobCancel
import com.pdf.editor.common.tools.jumpToActivity
import com.pdf.editor.data.ADResultData
import com.pdf.editor.databinding.AdLoadingBinding
import com.pdf.editor.feature.onboarding.FullScreenNativeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class ADShowManager(
    var activity: BaseActivity<*>, var tag: String
) {

    private var adShow: BaseShow? = null
    private var currentResult: ((type: String, hasValue: Boolean) -> Unit)? = null

    private fun initBaseShow(
        adEnum: ADEnum,
        adResultData: ADResultData?,
        result: (type: String, hasValue: Boolean) -> Unit
    ) {
        currentResult = result
        adShow = AdmobShow(adEnum, activity, tag, result)
    }

    private var nativeRefreshJob: Job? = null
    private var hasAddNativeRefreshDestroyListener = false

    private val NATIVE_REFRESH_RETRY_DELAY_MS = 1_000L

    var nativeRefreshCount = 30

    fun showScreenAD(
        pointTag: String,
        adEnum: ADEnum,
        result: (type: String, hasValue: Boolean) -> Unit
    ) {
        if (!ADControl.isAdPointEnabled(pointTag)) {
            result(ADControl.AD_SHOW_TYPE_FAILED, false)
            return
        }
        // 开屏真正展示前检查每日展示/点击限额，未通过时不能消费库存。
        if (ADControl.isOpenAd(adEnum) &&
            ADControl.isOpenAdFrequencyLimitReached("showScreenAD")
        ) {
            result.invoke(ADControl.AD_SHOW_TYPE_FAILED, false)
            AppLogs.dLog(ADControl.TAG,"开屏广告达到上限")
            return
        }
        if (ADControl.fullScreenAdShowing){
            result.invoke(ADControl.AD_SHOW_TYPE_FAILED, false)
            AppLogs.dLog(ADControl.TAG,"当前有正在展示的广告")
            return
        }
        val previewAdResultData = ADControl.getCacheAD(adEnum)
        initBaseShow(adEnum, previewAdResultData, result)
        if (isAllowShowAD(pointTag, adEnum,previewAdResultData,false)) return
        if (previewAdResultData?.adRequestData?.iwn == "nat"){
            runCatching {
                activity.jumpToActivity<FullScreenNativeActivity>(
                    extra = Bundle().apply {
                        putString(FullScreenNativeActivity.EXTRA_AD_ENUM, adEnum.name)
                        putString(FullScreenNativeActivity.EXTRA_POINT_TAG, pointTag)
                    }
                ) { activityResult ->
                    val resultData = activityResult.data
                    val resultType = resultData?.getStringExtra(
                        FullScreenNativeActivity.EXTRA_RESULT_TYPE
                    ) ?: ADControl.AD_SHOW_TYPE_FAILED
                    val hasValue = resultData?.getBooleanExtra(
                        FullScreenNativeActivity.EXTRA_RESULT_HAS_VALUE,
                        false
                    ) ?: false
                    result(resultType, hasValue)
                }
            }.onFailure {
                result(ADControl.AD_SHOW_TYPE_FAILED, false)
            }
        }else{
            val adResultData = ADControl.takeCacheAD(adEnum)
            if (adResultData == null) {
                adShow?.loadComplete(
                    type = ADControl.AD_SHOW_TYPE_FAILED,
                    log = "cache error",
                    adEnum,
                    pointTag
                )
                ADControl.poseAdImpressionFail(
                    adEnum = adEnum,
                    adResultData = null,
                    pointTag = pointTag,
                    error_code = "",
                    error_message = ADControl.AD_IMPRESSION_FAIL_NO_CACHE
                )
                return
            }
            adShow?.register(adResultData, pointTag)
            //走通用的逻辑
            adShow?.showScreenAD(adResultData, adEnum, pointTag)
            if (!ADControl.refuseContinueLoadList.contains(adEnum.adName) && pointTag!= PointConfig.AD_POINT_GUIDE_INT){
                ADControl.preloadAD(adEnum,"showScreenAD")
            }
        }
    }

    fun showNativeScreenAD(
        flRoot: FrameLayout,
        pointTag: String,
        adEnum: ADEnum,
        result: (type: String, hasValue: Boolean) -> Unit
    ) {
        if (!ADControl.isAdPointEnabled(pointTag)) {
            result(ADControl.AD_SHOW_TYPE_FAILED, false)
            return
        }
        val previewAdResultData = ADControl.getCacheAD(adEnum)
        initBaseShow(adEnum, previewAdResultData, result)
        if (isAllowShowAD(pointTag, adEnum, previewAdResultData, false)) return
        val adResultData = ADControl.takeCacheAD(adEnum)
        if (adResultData == null) {
            adShow?.loadComplete(
                type = ADControl.AD_SHOW_TYPE_FAILED,
                log = "cache error",
                adEnum,
                pointTag
            )
            ADControl.poseAdImpressionFail(
                adEnum = adEnum,
                adResultData = null,
                pointTag = pointTag,
                error_code = "",
                error_message = ADControl.AD_IMPRESSION_FAIL_NO_CACHE
            )
            return
        }
        adShow?.register(adResultData, pointTag)
        adShow?.showNativeScreenAD(flRoot, adResultData, adEnum, pointTag)
        if (!ADControl.refuseContinueLoadList.contains(adEnum.adName) && pointTag!= PointConfig.AD_POINT_GUIDE_INT){
            ADControl.preloadAD(adEnum,"showNativeScreenAD")
        }
    }

    private fun ADShowManager.isAllowShowAD(
        pointTag: String,
        adEnum: ADEnum,
        adResultData: ADResultData?,
        isBannerOrNative: Boolean
    ): Boolean {
        if (activity.isFinishing || activity.isDestroyed) {
            adShow?.loadComplete(
                type = ADControl.AD_SHOW_TYPE_FAILED,
                log = "activity error",
                adEnum,
                pointTag = pointTag
            )
            return true
        }
        if (!ADControl.allowAds){
            adShow?.loadComplete(
                type = ADControl.AD_SHOW_TYPE_FAILED,
                log = "ump 未通过",
                adEnum,
                pointTag = pointTag
            )
            return true
        }
        var isBUser = LocalDataManager.isBUser
        if (isBannerOrNative){
            if (ADControl.interceptPointList_B.contains(pointTag)){
                if (!isBUser){
                    adShow?.loadComplete(
                        type = ADControl.AD_SHOW_TYPE_FAILED,
                        log = "当前广告只允许买量用户展示",
                        adEnum,
                        pointTag
                    )
                    return true
                }
            }
        }else{
            //在需要限制的位置要满足 kc_cd 才能展示广告
            if (ADControl.interceptNeedADLimit.contains(pointTag)){
                if (ADControl.allowShowScreenAD()){
                    if (ADControl.interceptPointList_B.contains(pointTag)){
                        if (isBUser){
                            ADControl.addChance(adEnum,pointTag)
                        }else{
                            adShow?.loadComplete(
                                type = ADControl.AD_SHOW_TYPE_FAILED,
                                log = "a/b refuse",
                                adEnum,
                                pointTag
                            )
                            return true
                        }
                    }else{
                        ADControl.addChance(adEnum,pointTag)
                    }
                }else{
                    adShow?.loadComplete(
                        type = ADControl.AD_SHOW_TYPE_FAILED,
                        log = "cd_hot refuse",
                        adEnum,
                        pointTag
                    )
                    return true
                }
            }else if (ADControl.interceptPointList_B.contains(pointTag)){
                if (isBUser){
                    ADControl.addChance(adEnum,pointTag)
                }else{
                    adShow?.loadComplete(
                        type = ADControl.AD_SHOW_TYPE_FAILED,
                        log = "a/b refuse",
                        adEnum,
                        pointTag
                    )
                    return true
                }
            }else if (ADControl.interceptPointList_AB.contains(pointTag)){
                ADControl.addChance(adEnum,pointTag)
            }
        }

        if (adResultData == null) {
            adShow?.loadComplete(
                type = ADControl.AD_SHOW_TYPE_FAILED,
                log = "cache error",
                adEnum,
                pointTag
            )
            ADControl.poseAdImpressionFail(
                adEnum = adEnum,
                adResultData = adResultData,
                pointTag = pointTag,
                error_code = "",
                error_message = ADControl.AD_IMPRESSION_FAIL_NO_CACHE
            )
            return true
        }
        return false
    }

    fun showADBannerOrNative(parent: FrameLayout, adEnum: ADEnum, pointTag:String, lifecycleOwner: LifecycleOwner? = null, result: (type: String, hasValue: Boolean) -> Unit) {
        if (!ADControl.isAdPointEnabled(pointTag)) {
            result(ADControl.AD_SHOW_TYPE_FAILED, false)
            return
        }
        val previewAdResultData = ADControl.getCacheAD(adEnum)
        initBaseShow(adEnum, previewAdResultData, result)
        if (isAllowShowAD(pointTag, adEnum,previewAdResultData,true)) return
        // banner/native 共用这个入口，但 native-refresh 不能在这里先 takeCacheAD。
        // 原因有两个：
        // 1. 这里的职责只是判断“当前库存是 banner 还是 native”，不是立刻展示 native。
        // 2. 如果在这里先消费一次库存，showNativeADRefresh() 里又会再消费一次，
        //    就会出现“需要 2 个库存才能展示 1 个”的问题。
        //
        // 所以 native 分支这里只窥视当前最好的一条库存（getCacheAD），真正进入展示时
        // 再由 showNativeADRefresh() 在合适的生命周期里 takeCacheAD。
        if (previewAdResultData!!.adRequestData!!.iwn == "nat"){
            AppLogs.dLog(ADControl.TAG,"showADBannerOrNative 来源 nat")
            showNativeADRefresh(parent,pointTag,adEnum,lifecycleOwner,result)
        }else{
            val adResultData = ADControl.takeCacheAD(adEnum)
            if (adResultData == null) {
                adShow?.loadComplete(
                    type = ADControl.AD_SHOW_TYPE_FAILED,
                    log = "cache error",
                    adEnum,
                    pointTag
                )
                ADControl.poseAdImpressionFail(
                    adEnum = adEnum,
                    adResultData = null,
                    pointTag = pointTag,
                    error_code = "",
                    error_message = ADControl.AD_IMPRESSION_FAIL_NO_CACHE
                )
                return
            }
            adShow?.register(adResultData, pointTag)
            adShow?.showBannerAD(parent,adResultData,adEnum,pointTag)
        }
    }


    // 展示原生广告，并按固定间隔刷新。
    //
    // 这里最重要的约束是：
    // “轮询是否可展示”阶段绝不能消费库存，只有真正准备 render 的那一刻才允许 takeCacheAD。
    //
    // 否则会有两个直接问题：
    // 1. 入口 showADBanner() 先拿一次库存，这里再拿一次，就会白白吃掉一条库存。
    // 2. 页面如果此刻不在 RESUMED，库存会在等待阶段就被拿走，后续直接丢失。
    fun showNativeADRefresh(
        flRoot: FrameLayout,
        pointTag: String,
        adEnum: ADEnum,
        lifecycleOwner: LifecycleOwner? = null,
        result: (type: String, hasValue: Boolean) -> Unit
    ) {
        if (!ADControl.allowAds || !ADControl.isAdPointEnabled(pointTag)) {
            flRoot.visibility = View.GONE
            result(ADControl.AD_SHOW_TYPE_FAILED, false)
            return
        }
        ADControl.addChance2(adEnum, pointTag)
        stopNativeADRefresh()
        nativeRefreshJob = activity.addLaunch(success = {
            while (true) {
                val isLifecycleResumed = lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true
                val isActivityReady = activity.getActivityStatus()
                val previewAdResultData = ADControl.getCacheAD(adEnum)

                // 先判断页面是否允许展示、库存是否存在；这些条件都满足以后才真正消费库存。
                if (isActivityReady && previewAdResultData != null && isLifecycleResumed) {
                    val adResultData = ADControl.getCacheAD(adEnum)
                    if (adResultData == null) {
                        // getCacheAD/takeCacheAD 之间理论上可能被别的展示路径竞争掉库存。
                        // 这里不要误报失败，继续下一轮轮询，等新的库存或下一次机会即可。
                        delay(NATIVE_REFRESH_RETRY_DELAY_MS)
                        continue
                    }
                    withContext(Dispatchers.Main) {
                        // 展示器要跟“真正消费掉的那条库存”保持一致，不能沿用入口阶段预览到的对象。
                        val realADData = ADControl.takeCacheAD(adEnum)
                        adShow?.register(realADData!!, pointTag)
                        adShow?.showNativeAD(flRoot,realADData!!,adEnum,2,pointTag)
                        ADControl.preloadAD(adEnum,"showNativeADRefresh")
                    }
                    break
                }
                delay(NATIVE_REFRESH_RETRY_DELAY_MS)
            }
            var tempTime = 0
            while (true) {
                delay(NATIVE_REFRESH_RETRY_DELAY_MS)
                val isLifecycleResumed = lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) ?: true
                if (!activity.getActivityStatus() || !isLifecycleResumed) continue
                tempTime++
                if (tempTime < nativeRefreshCount) continue

                withContext(Dispatchers.Main) {
                    // 这里是“为了刷新而主动替换旧广告”，先清理当前展示，再显示 loading。
                    // 下一轮 showNativeADRefresh() 会重新等待页面可展示、且仅在真正展示前消费新库存。
                    destroyScreenNative(pointTag)
                    showNativeLoadingView(flRoot)
                }
                showNativeADRefresh(flRoot, pointTag, adEnum,lifecycleOwner,result)
                break
            }
        }, failBack = {})
    }


    fun stopNativeADRefresh() {
        nativeRefreshJob.jobCancel()
        nativeRefreshJob = null
    }



    private fun showNativeLoadingView(flRoot: FrameLayout) {
        flRoot.visibility = View.VISIBLE
        if (flRoot.childCount == 1) return
        val binding: AdLoadingBinding = AdLoadingBinding.inflate(activity.layoutInflater)
        flRoot.removeAllViews()
        flRoot.addView(binding.root)
    }

    fun destroyScreenNative(pointTag: String){
        adShow?.destroyNative(pointTag)
    }
}
