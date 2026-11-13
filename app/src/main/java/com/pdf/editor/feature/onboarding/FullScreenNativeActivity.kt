package com.pdf.editor.feature.onboarding

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.databinding.ActivityFullScreenNativeBinding

class FullScreenNativeActivity: BaseActivity<ActivityFullScreenNativeBinding>() {
    companion object {
        const val EXTRA_AD_ENUM = "full_screen_native_ad_enum"
        const val EXTRA_POINT_TAG = "full_screen_native_point_tag"
        const val EXTRA_RESULT_TYPE = "full_screen_native_result_type"
        const val EXTRA_RESULT_HAS_VALUE = "full_screen_native_result_has_value"
    }

    private var hasReturnedResult = false

    override fun getBinding(inflater: LayoutInflater): ActivityFullScreenNativeBinding {
        return ActivityFullScreenNativeBinding.inflate(layoutInflater)
    }

    override fun setListener() {
    }

    override fun setShowView() {
        setResult(
            Activity.RESULT_CANCELED,
            buildResultIntent(ADControl.AD_SHOW_TYPE_FAILED, false)
        )

        val adEnum = intent.getStringExtra(EXTRA_AD_ENUM)
            ?.let { enumName -> ADEnum.entries.firstOrNull { it.name == enumName } }
            ?: ADEnum.INT_AD_NEW
        val pointTag = intent.getStringExtra(EXTRA_POINT_TAG)
            ?: PointConfig.AD_POINT_GUIDE_FULL
        val adShowManager = ADShowManager(this, "FullScreenNativeActivity")

        adShowManager.showNativeScreenAD(aBinding.flADRoot, pointTag, adEnum) { type, hasValue ->
            complete(type, hasValue)
        }
    }

    private fun complete(type: String, hasValue: Boolean) {
        if (hasReturnedResult) return
        hasReturnedResult = true
        setResult(Activity.RESULT_OK, buildResultIntent(type, hasValue))
        finish()
    }

    private fun buildResultIntent(type: String, hasValue: Boolean) = Intent().apply {
        putExtra(EXTRA_RESULT_TYPE, type)
        putExtra(EXTRA_RESULT_HAS_VALUE, hasValue)
    }
}
