package com.pdf.editor.feature.onboarding.overlay

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import com.pdf.editor.R
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.nf.NotifyShow
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.PermissionManager
import com.pdf.editor.common.tools.jumpToActivity
import com.pdf.editor.databinding.PdfPopSystemAlertBinding
import com.pdf.editor.ui.PdfMainActivity
import com.pdf.editor.feature.preferences.language.LanguagePickerActivity

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 原类：`com.pdf.read.ui.guide.SystemAlertActivity`。 */
class OverlayPermissionActivity: BaseActivity<PdfPopSystemAlertBinding>() {
    private var switchStateRunnable: Runnable? = null
    private var isSwitchOn = false
    private var commitBreathAnimator: ObjectAnimator? = null
    private var commitShineAnimator: ObjectAnimator? = null

    override fun getBinding(inflater: LayoutInflater): PdfPopSystemAlertBinding {
        return PdfPopSystemAlertBinding.inflate(layoutInflater)
    }

    var alertJob: Job?=null

    /**
     * 1 启动流程进入 ai_pop!=0 && startShowAIGuide && !PermissionManager.allowSystemAlert(this) 要走后续启动页流程
     * 2 启动流程进入(不满足1 的条件)在MainActivity前展示如果 后续只跳转 MainActivity
     * 3 非启动流程进入 需要带参数
     */
    var systemType = 0
    var nfContent = ""
    var fromType = 0

    override fun setShowView() {
        systemType = intent.getIntExtra("systemType", 0)
        nfContent = intent.getStringExtra("nfContent")?:""
        fromType = intent.getIntExtra("fromType",0)
        aBinding.root.post {
            EventManager.posePoint(PointConfig.float_page_view)
            startCommitBreathAnimation()
            startCommitShineAnimation()
        }
        aBinding.root.post {
            if (!PermissionManager.allowNF(this)){
                PermissionManager.requestNFPermission(this, onGranted = {
                    NotifyShow.startForeground("SystemAlertActivity")
                }, onFail = {

                })
            }
        }
    }

    override fun setListener() {
        aBinding?.apply {
            flCommit.setOneClick {
                EventManager.posePoint(PointConfig.float_page_click, Bundle().apply {
                    putString("button","open")
                })
                PermissionManager.ensureSystemAlertPermission(this@OverlayPermissionActivity,{},{})
                showPermissionGuideActivity()
                alertJob = addLaunch(success = {
                    while (!PermissionManager.allowSystemAlert(this@OverlayPermissionActivity)){
                        delay(200)
                    }
                    withContext(Dispatchers.Main) {
                        EventManager.posePoint(PointConfig.float, Bundle().apply {
                            putString("state","on")
                        })
                        nextPage()
                    }
                }, failBack = {})
            }
            llRoot.setOneClick {
                flCommit.performClick()
            }
            tvLater.setOneClick {
                EventManager.posePoint(PointConfig.float_page_click, Bundle().apply {
                    putString("button","later")
                })
                nextPage()
            }
        }
    }

    private fun showPermissionGuideActivity() {
        aBinding.root.postDelayed({
            runCatching {
                startActivity(
                    Intent(
                        this@OverlayPermissionActivity,
                        OverlayPermissionGuideActivity::class.java
                    )
                )
                overridePendingTransition(0, 0)
            }
        }, 1000)
    }

    fun nextPage() {
        if (systemType == 1){
            AppLogs.dLog(aTAG, "start LanguagePickerActivity")
            jumpToActivity<LanguagePickerActivity>(Bundle().apply {
                putInt("fromType", 1)
                putInt("systemType", 1)
                putString("nfContent", nfContent)
            })
        }else if (systemType == 2){
            jumpToActivity<PdfMainActivity>()
        }else if (systemType == 3){
            jumpToActivity<PdfMainActivity>(Bundle().apply {
                putInt("fromType", fromType)
                putString("nfContent", nfContent)
            })
        }
        finish()
    }


    private fun startSwitchAnimation() {
        stopSwitchAnimation()
        isSwitchOn = false
        aBinding.ivSwitch.setImageResource(R.drawable.ic_switch1)
        val runnable = object : Runnable {
            override fun run() {
                if (isFinishing || isDestroyed) return
                isSwitchOn = !isSwitchOn
                aBinding.ivSwitch.setImageResource(
                    if (isSwitchOn) R.drawable.ic_switch2 else R.drawable.ic_switch1
                )
                aBinding.ivSwitch.postDelayed(this, SWITCH_STATE_DURATION_MS)
            }
        }
        switchStateRunnable = runnable
        aBinding.ivSwitch.postDelayed(runnable, SWITCH_STATE_DURATION_MS)
    }

    private fun stopSwitchAnimation() {
        switchStateRunnable?.let(aBinding.ivSwitch::removeCallbacks)
        switchStateRunnable = null
    }

    private fun startCommitBreathAnimation() {
        stopCommitBreathAnimation()
        aBinding.flCommit.post {
            if (isFinishing || isDestroyed) return@post
            val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.04f)
            val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.04f)
            val animator = ObjectAnimator.ofPropertyValuesHolder(aBinding.flCommit, scaleX, scaleY).apply {
                duration = 500L
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
            }
            commitBreathAnimator = animator
            animator.start()
        }
    }

    private fun stopCommitBreathAnimation() {
        commitBreathAnimator?.cancel()
        commitBreathAnimator = null
        aBinding.flCommit.scaleX = 1f
        aBinding.flCommit.scaleY = 1f
    }

    private fun startCommitShineAnimation() {
        stopCommitShineAnimation()
        aBinding.flCommit.post {
            if (isFinishing || isDestroyed) return@post
            val shineWidth = aBinding.vCommitShine.width
            val startX = -shineWidth.toFloat()
            val endX = aBinding.flCommit.width + shineWidth.toFloat()
            val animator = ObjectAnimator.ofFloat(aBinding.vCommitShine, View.TRANSLATION_X, startX, endX).apply {
                duration = 1800L
                startDelay = 250L
                repeatMode = ValueAnimator.RESTART
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
            aBinding.vCommitShine.translationX = startX
            commitShineAnimator = animator
            animator.start()
        }
    }

    private fun stopCommitShineAnimation() {
        commitShineAnimator?.cancel()
        commitShineAnimator = null
        aBinding.vCommitShine.translationX = 0f
    }

    override fun onBackPressed() {
    }

    override fun onPause() {
        stopSwitchAnimation()
        stopCommitBreathAnimation()
        stopCommitShineAnimation()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        aBinding.root.post {
            startSwitchAnimation()
            startCommitBreathAnimation()
            startCommitShineAnimation()
        }
    }

    override fun onDestroy() {
        stopSwitchAnimation()
        stopCommitBreathAnimation()
        stopCommitShineAnimation()
        super.onDestroy()
    }

    companion object {
        private const val SWITCH_STATE_DURATION_MS = 1000L
    }
}
