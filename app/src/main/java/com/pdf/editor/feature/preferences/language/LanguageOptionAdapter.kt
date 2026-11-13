package com.pdf.editor.feature.preferences.language

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pdf.editor.R
import com.pdf.editor.feature.preferences.language.model.LanguageOption
import com.pdf.editor.databinding.PdfItemLanguageBinding
import com.chad.library.adapter4.BaseQuickAdapter

/** 原类：`com.pdf.read.ui.language.LanguageAdapter`。 */
class LanguageOptionAdapter : BaseQuickAdapter<LanguageOption, LanguageOptionAdapter.VH>() {
    class VH(parent: ViewGroup, val viewBinding: PdfItemLanguageBinding = PdfItemLanguageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
    ) : RecyclerView.ViewHolder(viewBinding.root)


    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int
    ): VH {
        return VH(parent)
    }

    override fun onBindViewHolder(
        holder: VH,
        position: Int,
        item: LanguageOption?
    ) {
        if (item == null)return
        holder.viewBinding.apply {
            rlLanguage.isEnabled = !item.isSelected
            ivSelected.setImageResource(if (rlLanguage.isEnabled)R.drawable.ic_unselected else R.drawable.ic_selected)
            ivLanguage.setImageResource(item.drawableId)
            tvLanguage.text = item.title
        }
    }

}
