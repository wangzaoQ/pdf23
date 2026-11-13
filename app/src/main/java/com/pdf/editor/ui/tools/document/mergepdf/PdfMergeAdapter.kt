// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.mergepdf

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.pdf.editor.R
import com.pdf.editor.common.tools.GlideTools
import com.pdf.editor.databinding.ToolItemPdfMergeBinding
import com.pdf.editor.ui.tools.document.file.PdfThumbnailGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** PDF 队列使用独立条目，保留序号、删除和拖动操作，长文件名限制在操作按钮之前。 */
class PdfMergeAdapter(
    private val paths: List<String>,
    private val helper: () -> ItemTouchHelper,
    private val onDelete: (Int) -> Unit,
    private val onPreview: (Int) -> Unit
) : RecyclerView.Adapter<PdfMergeAdapter.Holder>() {
    private val thumbnailScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // () -> ItemTouchHelper 表示无参数、返回拖动助手的函数；(Int) -> Unit 表示接收位置且无返回值的回调。
    class Holder(val binding: ToolItemPdfMergeBinding) : RecyclerView.ViewHolder(binding.root)
    override fun getItemCount() = paths.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ToolItemPdfMergeBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    // = 使用表达式作为函数体；with 将 binding 作为作用域接收者，内部可直接使用控件名称。
    override fun onBindViewHolder(holder: Holder, position: Int) = with(holder.binding) {
        // 下标从 0 开始，+ 1 转成从 1 开始的显示序号；paths[position] 按下标读取路径。
        tvNum.text = (position + 1).toString()
        val path = paths[position]
        tvTitle.text = File(path).name
        holder.itemView.tag = path
        ivCover.setImageResource(R.drawable.ic_pdf)
        thumbnailScope.launch {
            val thumbnail = PdfThumbnailGenerator.generate(
                context = holder.itemView.context.applicationContext,
                filePath = path,
                widthDp = 44,
                heightDp = 44
            )
            if (thumbnail != null) withContext(Dispatchers.Main) {
                if (holder.itemView.tag == path) {
                    GlideTools.loadImg(iv = ivCover, url = thumbnail)
                }
            }
        }
        // 操作时获取实时位置，防止拖动或删除之后捕获的旧 position 指向其他文件。
        // takeIf 在条件成立时保留当前位置，否则返回 null；in 检查位置是否属于有效下标范围。
        // ?.let 只在位置有效时调用传入的 onDelete/onPreview 函数，并将位置作为参数。
        ivDelete.setOnClickListener { holder.bindingAdapterPosition.takeIf { it in paths.indices }?.let(onDelete) }
        root.setOnClickListener { holder.bindingAdapterPosition.takeIf { it in paths.indices }?.let(onPreview) }
        ivDrag.setOnTouchListener { _, event ->
            // _ 表示忽略 View 参数；== 判断是否刚按下，helper() 调用获取助手的函数再启动拖动。
            if (event.actionMasked == MotionEvent.ACTION_DOWN) helper().startDrag(holder)
            false
        }
    }

    override fun onViewRecycled(holder: Holder) {
        holder.itemView.tag = null
        Glide.with(holder.binding.ivCover).clear(holder.binding.ivCover)
        super.onViewRecycled(holder)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        thumbnailScope.cancel()
        super.onDetachedFromRecyclerView(recyclerView)
    }

}
