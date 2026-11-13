package com.pdf.editor.ui.detail.add

import android.view.LayoutInflater
import com.pdf.editor.MyAPP
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.databinding.ActivitySignBinding

class SignActivity : BaseActivity<ActivitySignBinding>() {
    override fun getBinding(inflater: LayoutInflater): ActivitySignBinding {
        return ActivitySignBinding.inflate(inflater)
    }

    override fun setListener() {
        aBinding.apply {
            tvClear.setOneClick {
                signView.clear()
            }
            mcFinish.setOneClick {
                val bitmap = signView.getCroppedBitmapWithPadding() ?: return@setOneClick
                MyAPP.pdfSignLiveData.postValue(bitmap)
                finish()
            }
            ivBack.setOneClick {
                finish()
            }
        }
    }

    override fun setShowView() = Unit

    override fun onResume() {
        super.onResume()
        showSystemBars(window)
        showStatusBarText(true)
    }
}
