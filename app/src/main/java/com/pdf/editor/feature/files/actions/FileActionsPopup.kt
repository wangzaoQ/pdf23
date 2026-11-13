package com.pdf.editor.feature.files.actions

import android.content.Context
import android.view.View
import com.pdf.editor.R
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.setOneClick
import com.pdf.editor.databinding.PopFileEditBinding
import razerdp.basepopup.BasePopupWindow

/** 原类：`com.pdf.read.pop.EditPop`。 */
class FileActionsPopup(context: Context) : BasePopupWindow(context) {

    init {
        setContentView(R.layout.pop_file_edit)
    }

    var binding: PopFileEditBinding? = null

    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        binding = PopFileEditBinding.bind(contentView)
    }

    fun createPop(view: View, callBack: (type: Int) -> Unit) {
        binding?.apply {
            rlRename.setOneClick {
                callBack.invoke(0)
                dismiss()
            }
            rlDelete.setOneClick {
                EventManager.posePoint(PointConfig.file_delete_confirm)
                callBack.invoke(1)
                dismiss()
            }
        }
        showPopupWindow(view)
    }
}
