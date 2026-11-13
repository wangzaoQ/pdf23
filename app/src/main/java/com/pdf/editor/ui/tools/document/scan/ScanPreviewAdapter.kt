// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.scan

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pdf.editor.databinding.ItemImageBinding

class ScanPreviewAdapter(
    private val pages: List<Uri>
) : RecyclerView.Adapter<ScanPreviewAdapter.PageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        return PageViewHolder(
            ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        Glide.with(holder.binding.imageView).load(pages[position]).into(holder.binding.imageView)
    }

    override fun getItemCount(): Int = pages.size

    class PageViewHolder(val binding: ItemImageBinding) : RecyclerView.ViewHolder(binding.root)
}
