package com.pdf.editor.feature.files.actions

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.animation.Animation
import com.blankj.utilcode.util.KeyboardUtils
import com.pdf.editor.R
import com.pdf.editor.common.tools.setOneClick
import com.pdf.editor.databinding.PopFileRenameBinding
import razerdp.basepopup.BasePopupWindow
import razerdp.util.animation.AnimationHelper
import razerdp.util.animation.ScaleConfig

/** 原类：`com.pdf.read.pop.FileRenamePop`。 */
class RenameFilePopup(context: Context) : BasePopupWindow(context) {
    init {
        setContentView(R.layout.pop_file_rename)
    }

    var defaultBinding: PopFileRenameBinding? = null

    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        defaultBinding = PopFileRenameBinding.bind(contentView)
    }

    fun createPop(fileName: String,callBack: (resultName: String) -> Unit) {
        defaultBinding?.apply {
            etEdit.setText(fileName)
            etEdit.setHint(fileName)
            etEdit.setSelection(etEdit.text?.length ?: 0)
            etEdit.setOnKeyListener { v, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
                    KeyboardUtils.hideSoftInput(etEdit)
                    return@setOnKeyListener true
                }
                return@setOnKeyListener false
            }
            etEdit.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(p0: Editable?) {
                    tvConfirm.isEnabled = etEdit.text.toString().trim().isNotEmpty()
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }
            })
            tvConfirm.setOneClick {
                callBack.invoke(etEdit.text.toString().trim())
                dismiss()
            }
            tvCancel.setOneClick {
                dismiss()
            }
            llRoot.setOneClick {
                KeyboardUtils.hideSoftInput(etEdit)
            }
        }
        showPopupWindow()
    }

    override fun onCreateShowAnimation(): Animation {
        return AnimationHelper.asAnimation()
            .withScale(ScaleConfig.CENTER)
            .toShow()
    }

    override fun onCreateDismissAnimation(): Animation {
        return AnimationHelper.asAnimation()
            .withScale(ScaleConfig.CENTER)
            .toDismiss()
    }
}
