package com.pdf.editor.feature.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.PermissionManager
import com.pdf.editor.common.tools.jumpToActivity
import com.pdf.editor.databinding.ActivityGuideBinding
import com.pdf.editor.ui.PdfMainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 原类：`com.pdf.read.ui.guide.GuideActivity`。 */
class OnboardingActivity : BaseActivity<ActivityGuideBinding>() {

    private val fragments = mutableListOf<Fragment>()
    private var systemType = 0
    private var launchContent = ""

    override fun getBinding(inflater: LayoutInflater): ActivityGuideBinding {
        return ActivityGuideBinding.inflate(inflater)
    }

    override fun setListener() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
    }

    override fun setShowView() {
        ADControl.preloadAD(ADEnum.INT_AD_NEW)
        ADControl.preloadAD(ADEnum.INT_AD_USER)
        systemType = intent.getIntExtra("systemType", 0)
        launchContent = intent.getStringExtra("nfContent") ?: ""
        fragments.apply {
            add(OnboardingPageFragment.newInstance(0))
            add(OnboardingPageFragment.newInstance(1))
        }
        aBinding.vp.apply {
            offscreenPageLimit = fragments.size
            adapter = object : FragmentPagerAdapter(
                supportFragmentManager,
                BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT
            ) {
                override fun getItem(position: Int): Fragment = fragments[position]

                override fun getCount(): Int = fragments.size
            }
        }
        LocalDataManager.cacheGuideFirst = false
    }


    fun next() {
        if (isFinishing || isDestroyed) return
        if (aBinding.vp.currentItem == 0){
            ADShowManager(this@OnboardingActivity, "OnboardingActivity").showScreenAD(PointConfig.AD_POINT_GUIDE_FULL,
                ADEnum.INT_AD_NEW) { type, _ ->
                aBinding.vp.currentItem = 1
            }
        }else{
            ADShowManager(this@OnboardingActivity, "OnboardingActivity").showScreenAD(PointConfig.AD_POINT_GUIDE_INT,
                ADEnum.INT_AD_NEW) { type, _ ->
                jumpToActivity<PdfMainActivity>(Bundle().apply {
                    putString("nfContent", launchContent)
                })
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }
}
