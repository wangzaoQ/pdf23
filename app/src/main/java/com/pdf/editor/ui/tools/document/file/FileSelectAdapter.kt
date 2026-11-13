// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.file

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.pdf.editor.R
import com.pdf.editor.common.tools.FileManager
import com.pdf.editor.common.tools.GlideTools
import com.pdf.editor.common.tools.ScanConfig
import com.pdf.editor.databinding.PdfItemFileListBinding
import java.io.File

/** File picker adapter. It shares the home row layout but owns selection state. */
class FileSelectAdapter : BaseQuickAdapter<ScannedFile, FileSelectAdapter.FileViewHolder>() {

    var selectedPath: String? = null
    /** 合并入口使用有序多选集合；null 表示继续使用其他工具原有的单选逻辑。 */
    var selectedPaths: Set<String>? = null

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int
    ): FileViewHolder = FileViewHolder(parent)

    override fun onBindViewHolder(
        holder: FileViewHolder,
        position: Int,
        item: ScannedFile?
    ) {
        // item?.let 只绑定非 null 的文件，it 是该文件；selectedPaths?.contains 查询多选集合。
        // ?: 仅在左侧为 null（未启用多选）时回退到单选比较，左侧为 false 时不会回退。
        item?.let { holder.bind(it, selectedPaths?.contains(it.absolutePath) ?: (it.absolutePath == selectedPath)) }
    }

    fun select(position: Int) {
        val item = items.getOrNull(position) ?: return
        selectedPath = item.absolutePath
    }

    class FileViewHolder(
        parent: ViewGroup,
        private val binding: PdfItemFileListBinding = PdfItemFileListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScannedFile, selected: Boolean) = with(binding) {
            val fileType = FileManager.getFileType(item.name)
            if (fileType == ScanConfig.FILE_TYPE_PDF &&
                !item.thumbnailPath.isNullOrBlank() &&
                File(item.thumbnailPath).exists()
            ) {
                GlideTools.loadImg(iv = ivFile, url = item.thumbnailPath)
            } else {
                ivFile.setImageResource(FileManager.getFileImg(fileType))
            }
            tvFileName.text = item.name
            tvFileDate.text = "${FileManager.getFileTime(item.modifiedAtMillis)} · "
            tvFileSize.text = FileManager.getFileSize(item.sizeBytes)
            allFiles.setBackgroundResource(FileManager.getFileBg(fileType))
            ivMore.setImageResource(
                if (selected) R.drawable.ic_selected else R.drawable.ic_unselected
            )
            ivMore.contentDescription = root.context.getString(
                if (selected) R.string.app_file_selected else R.string.app_file_unselected
            )
            ivMore.setOnClickListener { root.performClick() }
            ivCollect.visibility = android.view.View.GONE
        }
    }
}
