// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.splitpdf

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pdf.editor.R
import com.pdf.editor.databinding.ToolItemPdfSplitBinding

/** 独立拆分条目，仅提供预览和删除，不注册拖动操作，也不复用合并/图片适配器。 */
class PdfSplitAdapter(private val onDelete: (Int) -> Unit, private val onPreview: (Int) -> Unit) :
    RecyclerView.Adapter<PdfSplitAdapter.Holder>() {
    var pages: List<PdfSplitPage> = emptyList()
        private set

    /** 列表由 ViewModel 提供快照；本适配器不直接修改队列数据。 */
    fun submit(pages: List<PdfSplitPage>) {
        this.pages = pages
        notifyDataSetChanged()
    }

    class Holder(val binding: ToolItemPdfSplitBinding) : RecyclerView.ViewHolder(binding.root)
    override fun getItemCount() = pages.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ToolItemPdfSplitBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = with(holder.binding) {
        // [] 读取列表项；+ 1 将从零开始的原始下标转换为用户看到的页码。
        val page = pages[position]
        tvTitle.text = root.context.getString(R.string.app_page_index, page.sourceIndex + 1)
        Glide.with(ivCover).load(page.thumbnail).error(R.drawable.ic_pdf).into(ivCover)
        // getOrNull 越界时返回 null，?.let 仅在位置有效时调用回调；传递原始页号而非当前列表位置。
        ivDelete.setOnClickListener { pages.getOrNull(holder.bindingAdapterPosition)?.let { onDelete(it.sourceIndex) } }
        root.setOnClickListener { pages.getOrNull(holder.bindingAdapterPosition)?.let { onPreview(it.sourceIndex) } }
    }

    override fun onViewRecycled(holder: Holder) {
        Glide.with(holder.binding.ivCover).clear(holder.binding.ivCover)
        super.onViewRecycled(holder)
    }
}
