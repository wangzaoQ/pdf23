package com.pdf.editor.feature.files.actions

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.widget.PopupWindow
import com.pdf.editor.R
import com.pdf.editor.common.tools.ScanConfig
import com.pdf.editor.common.tools.setOneClick
import com.pdf.editor.databinding.PdfPopFileSortBinding
import com.pdf.editor.pop.PopupQueue
import razerdp.basepopup.BasePopupWindow
import razerdp.util.animation.AnimationHelper
import razerdp.util.animation.ScaleConfig
import razerdp.util.animation.TranslationConfig

/** 原类：`com.pdf.read.pop.FileSortPop`。 */
class FileSortPopup(context: Context) : BasePopupWindow(context) {

    init {
        setContentView(R.layout.pdf_pop_file_sort)
    }

    var binding: PdfPopFileSortBinding? = null

    override fun onViewCreated(contentView: View) {
        super.onViewCreated(contentView)
        binding = PdfPopFileSortBinding.bind(contentView)
    }
    fun createPop(currentSort: String, callBack: (String) -> Unit) {
        updateSortUI(currentSort)
        binding?.apply {
            rlDate1.setOneClick {
                selectSort(ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST, callBack)
            }
            rlDate2.setOneClick {
                selectSort(ScanConfig.SORT_TYPE_OPEN_TIME_OLDEST, callBack)
            }
            rlName1.setOneClick {
                selectSort(ScanConfig.SORT_TYPE_FILE_NAME_A_Z, callBack)
            }
            rlName2.setOneClick {
                selectSort(ScanConfig.SORT_TYPE_FILE_NAME_Z_A, callBack)
            }
        }
        PopupQueue.enqueue(this)
    }

    private fun selectSort(sortType: String, callBack: (String) -> Unit) {
        updateSortUI(sortType)
        callBack.invoke(sortType)
        popupWindow.dismiss()
    }

    private fun updateSortUI(currentSort: String) {
        binding?.apply {
            updateItemSelected(
                tvDate1,
                ivDate1Selected,
                currentSort == ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST
            )
            updateItemSelected(
                tvDate2,
                ivDate2Selected,
                currentSort == ScanConfig.SORT_TYPE_OPEN_TIME_OLDEST
            )
            updateItemSelected(
                tvName1,
                ivName1Selected,
                currentSort == ScanConfig.SORT_TYPE_FILE_NAME_A_Z
            )
            updateItemSelected(
                tvName2,
                ivName2Selected,
                currentSort == ScanConfig.SORT_TYPE_FILE_NAME_Z_A
            )
        }
    }

    private fun updateItemSelected(textView: View, selectedView: View, selected: Boolean) {
        textView.isEnabled = !selected
        selectedView.visibility = if (selected) View.VISIBLE else View.GONE
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
}
