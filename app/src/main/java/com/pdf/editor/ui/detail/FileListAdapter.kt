package com.pdf.editor.ui.detail

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter4.BaseQuickAdapter
import com.pdf.editor.common.tools.FileManager
import com.pdf.editor.common.tools.GlideTools
import com.pdf.editor.common.tools.ScanConfig
import com.pdf.editor.data.FilesData
import com.pdf.editor.databinding.PdfItemFileListBinding
import java.io.File

class FileListAdapter : BaseQuickAdapter<FilesData, FileListAdapter.VH>() {

    private var highContent = ""

    class VH(
        parent: ViewGroup,
        val binding: PdfItemFileListBinding = PdfItemFileListBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
    ) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(context: Context, parent: ViewGroup, viewType: Int): VH {
        return VH(parent)
    }

    fun addHighContent(content: String) {
        highContent = content
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: VH, position: Int, item: FilesData?) {
        if (item == null) return
        holder.binding.apply {
            if (item.fileType == ScanConfig.FILE_TYPE_PDF && item.fileImg.isNotEmpty() && File(item.fileImg).exists()) {
                GlideTools.loadImg(iv = ivFile, url = item.fileImg)
            } else {
                ivFile.setImageResource(FileManager.getFileImg(item.fileType))
            }
            val text = item.fileName
            if (highContent.isEmpty() || !text.contains(highContent, ignoreCase = true)) {
                tvFileName.text = text
            } else {
                val start = text.lowercase().indexOf(highContent.lowercase())
                if (start >= 0) {
                    val end = start + highContent.length
                    val spannable = SpannableString(text)
                    spannable.setSpan(
                        ForegroundColorSpan(Color.parseColor("#FFFE9615")),
                        start,
                        end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    tvFileName.text = spannable
                } else {
                    tvFileName.text = text
                }
            }
            tvFileSize.text = "${FileManager.getFileSize(item.fileSize)}"
            tvFileDate.text = "${FileManager.getFileTime(item.fileTime)} · "
            allFiles.setBackgroundResource(FileManager.getFileBg(item.fileType))
            ivMore.visibility = if (FilesData.isDemoSample(item)) View.GONE else View.VISIBLE
        }
    }
}
