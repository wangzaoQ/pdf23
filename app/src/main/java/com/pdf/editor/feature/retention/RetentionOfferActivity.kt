package com.pdf.editor.feature.retention

import android.os.Bundle
import android.view.LayoutInflater
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADControl.preloadAD
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.jumpToActivity
import com.pdf.editor.databinding.ActivityUninstallBinding
import com.pdf.editor.ui.PdfMainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 原类：`com.pdf.read.ui.uninstall.PdfUninstallActivity`。 */
class RetentionOfferActivity : BaseActivity<ActivityUninstallBinding>() {
    override fun getBinding(inflater: LayoutInflater): ActivityUninstallBinding {
        return ActivityUninstallBinding.inflate(layoutInflater)
    }

    override fun setListener() {
        aBinding.apply {
            btnTryAgain.setOneClick { toMain() }
            btnTryAgain2.setOneClick { toMain() }
            btnKeepApp.setOneClick {
                EventManager.posePoint(PointConfig.uninstall_option_select, Bundle().apply {
                    putInt(PointKey.type, 1)
                })
                val adShowManager = ADShowManager(
                    this@RetentionOfferActivity,
                    "PdfUninstallActivity"
                )
                adShowManager.showScreenAD(
                    PointConfig.AD_POINT_UNLOAD2,
                    ADEnum.INT_AD_EXIT
                ) { _, _ ->
                    jumpToActivity<ExitSurveyActivity>()
                    finish()
                }
            }
            tvPersistUninstall.setOneClick { toMain() }
            ivBack.setOneClick { toMain() }
        }
    }

    override fun setShowView() {
        preloadAD(ADEnum.INT_AD_EXIT)
        preloadAD(ADEnum.BANNER_AD_1)
        EventManager.posePoint(PointConfig.uninstall_option_page)
        addLaunch(success = {
            while (true) {
                if (
                    ADControl.getCacheAD(ADEnum.BANNER_AD_1) != null &&
                    getActivityStatus() &&
                    LocalDataManager.isBUser &&
                    ADControl.allowAds
                ) {
                    withContext(Dispatchers.Main) {
                        ADShowManager(
                            this@RetentionOfferActivity,
                            "PdfUninstallActivity"
                        ).showADBannerOrNative(
                            aBinding.flADRoot2,
                            ADEnum.BANNER_AD_1,
                            PointConfig.AD_POINT_UNLOAD_NAT1
                        ) { _, _ -> }
                    }
                    break
                }
                delay(1_000L)
            }
        }, failBack = {})
    }

    override fun onBackPressed() {
        aBinding.ivBack.performClick()
    }

    private fun toMain() {
        EventManager.posePoint(PointConfig.uninstall_option_select, Bundle().apply {
            putInt(PointKey.type, 0)
        })
        jumpToActivity<PdfMainActivity>()
        finish()
    }
}
