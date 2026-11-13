package com.pdf.editor.feature.onboarding.overlay

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.pdf.editor.R
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.PermissionManager
import com.pdf.editor.databinding.ViewPermissionGuideBinding

/** 原类：`com.pdf.read.ui.guide.SystemAlertGuideActivity`。 */
class OverlayPermissionGuideActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var guideView: View? = null
    private var guideBinding: ViewPermissionGuideBinding? = null
    private var isSwitchOn = false
    private var isFinishingGuide = false

    private val switchStateRunnable = object : Runnable {
        override fun run() {
            val switchView = guideBinding?.ivSwitch ?: return
            isSwitchOn = !isSwitchOn
            switchView.setImageResource(
                if (isSwitchOn) R.drawable.ic_switch2 else R.drawable.ic_switch1
            )
            handler.postDelayed(this, SWITCH_STATE_DURATION_MS)
        }
    }

    private val finishRunnable = Runnable {
//        finishGuide()
    }

    private val permissionCheckRunnable = object : Runnable {
        override fun run() {
            if (PermissionManager.allowSystemAlert(this@OverlayPermissionGuideActivity)) {
                finishGuide()
            } else {
                handler.postDelayed(this, 200)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        setupWindow()
        setContentView(createGuideView())
        startSwitchAnimation()
        startGuideEnterAnimation()
        handler.postDelayed(finishRunnable, 4000)
        handler.postDelayed(permissionCheckRunnable, 200)
    }

    private fun setupWindow() {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
        )
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    private fun createGuideView(): FrameLayout {
        val binding = ViewPermissionGuideBinding.inflate(layoutInflater)
        guideBinding = binding
        guideView = binding.root
        return FrameLayout(this).apply {
            setBackgroundColor(0x66000000)
            isClickable = true
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    finishGuide()
                }
                true
            }
            addView(
                binding.root,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                }
            )
        }
    }

    private fun startSwitchAnimation() {
        isSwitchOn = false
        guideBinding?.ivSwitch?.setImageResource(R.drawable.ic_switch1)
        handler.removeCallbacks(switchStateRunnable)
        handler.postDelayed(switchStateRunnable, SWITCH_STATE_DURATION_MS)
    }

    private fun startGuideEnterAnimation() {
        guideView?.apply {
            alpha = 0f
            translationY = -40.dp.toFloat()
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    private fun finishGuide() {
        if (isFinishing || isFinishingGuide) return
        isFinishingGuide = true
        handler.removeCallbacksAndMessages(null)
        guideView?.animate()
            ?.alpha(0f)
            ?.translationY(-24.dp.toFloat())
            ?.setDuration(160L)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.withEndAction {
                finish()
                overridePendingTransition(0, 0)
            }
            ?.start() ?: run {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        guideBinding = null
        super.onDestroy()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val SWITCH_STATE_DURATION_MS = 1000L
    }
}
