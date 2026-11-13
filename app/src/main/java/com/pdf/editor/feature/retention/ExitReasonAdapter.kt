package com.pdf.editor.feature.retention

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.pdf.editor.R
import com.pdf.editor.feature.retention.model.ExitReasonOption
import com.pdf.editor.databinding.ItemUninstallInfoBinding

/** 原类：`com.pdf.read.ui.uninstall.UninstallInfoAdapter`。 */
class ExitReasonAdapter : BaseQuickAdapter<ExitReasonOption, ExitReasonAdapter.ViewHolder>() {


    class ViewHolder(
        parent: ViewGroup,
        val binding: ItemUninstallInfoBinding = ItemUninstallInfoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder = ViewHolder(parent)

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
        item: ExitReasonOption?
    ) {
        if (item == null) return
        holder.binding.apply {
            tvInfo.text = item.content
            ivSelected.setImageResource(if (!item.selected)R.drawable.ic_unselected else R.drawable.ic_selected)
        }
    }
}
