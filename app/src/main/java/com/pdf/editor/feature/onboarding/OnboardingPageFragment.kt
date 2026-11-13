package com.pdf.editor.feature.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import com.pdf.editor.R
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.core.BaseFragment
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.setOneClick
import com.pdf.editor.databinding.FragmentGuideBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 原类：`com.pdf.read.ui.guide.GuideFragment`。 */
class OnboardingPageFragment : BaseFragment<FragmentGuideBinding>() {

    companion object {
        fun newInstance(index: Int): OnboardingPageFragment {
            return OnboardingPageFragment().apply {
                arguments = Bundle().apply { putInt("index", index) }
            }
        }
    }

    private var index = 0

    override fun startLoadData() {
        EventManager.posePoint(PointConfig.guide_page_action, Bundle().apply {
            putString(PointKey.page, "page_${index + 1}")
        })

       val adEnum =  if (index == 0){
            ADEnum.BANNER_AD_1
        }else{
           ADEnum.BANNER_AD_2
        }
        val pointTag = if (index == 0){
            PointConfig.AD_POINT_GUIDE1
        }else{
            PointConfig.AD_POINT_GUIDE2
        }
        rootActivity.addLaunch(success = {
            while (true){
                if (rootActivity.getActivityStatus() && ADControl.getCacheAD(adEnum)!=null && LocalDataManager.isBUser && ADControl.allowAds){
                    withContext(Dispatchers.Main){
                        var adShowManager = ADShowManager(rootActivity,"OnboardingPageFragment")
                        adShowManager.showADBannerOrNative(fBinding.flADRoot,adEnum, pointTag,this@OnboardingPageFragment){ type, hasValue ->

                        }
                    }
                    break
                }
                delay(1000)
            }
        }, failBack = {})
    }

    override fun setListener() {
        fBinding.tvCommit.setOneClick {
            (rootActivity as OnboardingActivity).next()
        }
    }

    override fun setShowView() {
        index = (arguments?.getInt("index") ?: 0).coerceIn(0, 2)
        fBinding.apply {
            when (index) {
                0 -> {
                    ivGuide.setImageResource(R.drawable.ic_guide1)
                    tvGuide.setText(R.string.app_guide_title1)
                    tvGuide2.setText(R.string.app_guide_content1)
                    tvLater.text = "1/2"
                }
                1 -> {
                    ivGuide.setImageResource(R.drawable.ic_guide2)
                    tvGuide.setText(R.string.app_guide_title2)
                    tvGuide2.setText(R.string.app_guide_content2)
                    tvLater.text = "2/2"
                }
            }
        }
    }

    override fun getBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentGuideBinding {
        return FragmentGuideBinding.inflate(inflater, container, false)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }
}
