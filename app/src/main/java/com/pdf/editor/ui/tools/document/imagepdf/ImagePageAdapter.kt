// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.imagepdf

import android.net.Uri
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pdf.editor.R
import com.pdf.editor.databinding.ToolItemImgBinding

class ImagePageAdapter(
    private val pages: List<Uri>,
    private val itemTouchHelper: () -> ItemTouchHelper,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<ImagePageAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        return ImageViewHolder(ToolItemImgBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.binding.tvNum.text = (position + 1).toString()
        holder.binding.tvTitle.text = holder.itemView.context.getString(R.string.app_page_index, position + 1)
        Glide.with(holder.binding.ivCover).load(pages[position]).into(holder.binding.ivCover)
        holder.binding.ivDelete.setOnClickListener {
            holder.bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let(onDelete)
        }
        holder.binding.ivDrag.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) itemTouchHelper().startDrag(holder)
            false
        }
    }

    override fun getItemCount(): Int = pages.size

    class ImageViewHolder(val binding: ToolItemImgBinding) : RecyclerView.ViewHolder(binding.root)
}
