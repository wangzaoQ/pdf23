package com.pdf.editor.pop

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.Animation
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.pdf.editor.R
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.PermissionManager
import com.pdf.editor.common.tools.setOneClick
import com.pdf.editor.databinding.PdfPopNfGuideBinding
import razerdp.basepopup.BasePopupWindow
import razerdp.util.animation.AnimationHelper
import razerdp.util.animation.TranslationConfig

class NFGuidePop(context: Context) : BasePopupWindow(context) {

    private var switchStateRunnable: Runnable? = null
    private var isSwitchOn = false

    init {
        setContentView(R.layout.pdf_pop_nf_guide)
    }

    var defaultBinding: PdfPopNfGuideBinding? = null

    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        defaultBinding = PdfPopNfGuideBinding.bind(contentView)
    }

    fun createPop(callBack: (type: Int) -> Unit) {
        startSwitchAnimation()
        PopupQueue.enqueue(this)
        EventManager.posePoint(PointConfig.push_guide_view, Bundle().apply {
            putString("show_type","secondary")
        })
        defaultBinding?.apply {
            tvLater.setOneClick {
                EventManager.posePoint(PointConfig.push_guide_click, Bundle().apply {
                    putString("state","later")
                })
                dismiss()
            }
            flCommit.setOneClick {
                EventManager.posePoint(PointConfig.push_guide_click, Bundle().apply {
                    putString("state","turn_on")
                })
                PermissionManager.requestNFPermission(context, {
                    callBack.invoke(0)
                    dismiss()
                }, {
                    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S){
                        XXPermissions.startPermissionActivity(
                            context,
                            PermissionLists.getPostNotificationsPermission()
                        )
                    }
                    dismiss()
                })
            }
        }
    }

    private fun startSwitchAnimation() {
        stopSwitchAnimation()
        isSwitchOn = false
        defaultBinding?.ivSwitch?.setImageResource(R.drawable.ic_switch1)
        val runnable = object : Runnable {
            override fun run() {
                val switchView = defaultBinding?.ivSwitch ?: return
                isSwitchOn = !isSwitchOn
                switchView.setImageResource(
                    if (isSwitchOn) R.drawable.ic_switch2 else R.drawable.ic_switch1
                )
                switchView.postDelayed(this, SWITCH_STATE_DURATION_MS)
            }
        }
        switchStateRunnable = runnable
        defaultBinding?.ivSwitch?.postDelayed(runnable, SWITCH_STATE_DURATION_MS)
    }

    private fun stopSwitchAnimation() {
        switchStateRunnable?.let { defaultBinding?.ivSwitch?.removeCallbacks(it) }
        switchStateRunnable = null
    }

    override fun onDismiss() {
        stopSwitchAnimation()
        super.onDismiss()
    }

    override fun onCreateShowAnimation(): Animation {
        return AnimationHelper.asAnimation()
            .withTranslation(TranslationConfig.FROM_BOTTOM)
            .toShow()
    }

    override fun onCreateDismissAnimation(): Animation {
        return AnimationHelper.asAnimation()
            .withTranslation(TranslationConfig.TO_BOTTOM)
            .toDismiss()
    }

    companion object {
        private const val SWITCH_STATE_DURATION_MS = 1000L
    }
}
