// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.common

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent
import android.view.View
import android.view.animation.Animation
import androidx.core.widget.doAfterTextChanged
import com.blankj.utilcode.util.KeyboardUtils
import com.pdf.editor.R
import com.pdf.editor.ui.detail.reader.setSequentialClickListener
import com.pdf.editor.databinding.ToolPopFileRenameBinding
import razerdp.basepopup.BasePopupWindow
import razerdp.util.animation.AnimationHelper
import razerdp.util.animation.ScaleConfig

class FileRenamePop(context: Context) : BasePopupWindow(context) {

    private var binding: ToolPopFileRenameBinding? = null

    init {
        setContentView(R.layout.tool_pop_file_rename)
    }

    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        binding = ToolPopFileRenameBinding.bind(contentView)
    }

    fun createPop(
        defaultName: String,
        @androidx.annotation.StringRes titleRes: Int = R.string.app_rename,
        @androidx.annotation.StringRes confirmRes: Int = R.string.app_save,
        @androidx.annotation.StringRes hintRes: Int = R.string.app_edit_name_hint,
        onConfirm: (resultName: String) -> Unit
    ) {
        val popupBinding = binding ?: return
        with(popupBinding) {
            tvTitle.setText(titleRes)
            tvConfirm.setText(confirmRes)
            etEdit.setHint(hintRes)
            etEdit.setText(defaultName)
            etEdit.setSelection(etEdit.text?.length ?: 0)
            tvConfirm.isEnabled = defaultName.isNotBlank()

            var hasSubmitted = false
            val submitName = {
                val resultName = etEdit.text?.toString()?.trim().orEmpty()
                if (!hasSubmitted && resultName.isNotEmpty()) {
                    hasSubmitted = true
                    tvConfirm.isEnabled = false
                    dismiss()
                    onConfirm(resultName)
                }
            }

            etEdit.doAfterTextChanged { text ->
                tvConfirm.isEnabled = !text.isNullOrBlank()
            }
            etEdit.setOnEditorActionListener { _, actionId, event ->
                val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE
                val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN
                if (isDoneAction || isEnterKey) {
                    submitName()
                    true
                } else {
                    false
                }
            }
            tvConfirm.setSequentialClickListener {
                submitName()
            }
            llRoot.setSequentialClickListener {
                KeyboardUtils.hideSoftInput(etEdit)
            }
        }

        showPopupWindow()
        popupBinding.etEdit.post {
            popupBinding.etEdit.requestFocus()
            KeyboardUtils.showSoftInput(popupBinding.etEdit)
        }
    }

    override fun dismiss() {
        binding?.let {
            KeyboardUtils.hideSoftInput(it.etEdit)
        }
        super.dismiss()
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
