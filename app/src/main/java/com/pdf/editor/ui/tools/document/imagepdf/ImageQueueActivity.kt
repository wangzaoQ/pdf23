// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.imagepdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pdf.editor.R
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.ui.detail.reader.setSequentialClickListener
import com.pdf.editor.databinding.ToolActivityImgToPdfBinding
import com.pdf.editor.ui.tools.document.common.FileRenamePop
import com.pdf.editor.ui.tools.document.common.DocumentProcessActivity
import com.pdf.editor.ui.tools.document.common.DocumentTaskRequest
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** 在创建文档前管理图片的排序、添加和删除。 */
class ImageQueueActivity : BaseActivity<ToolActivityImgToPdfBinding>() {
    // PDF14-MIGRATION: 当前 BaseActivity 的绑定字段；源页面绑定调用保持不变。
    private val binding: ToolActivityImgToPdfBinding get() = aBinding

    private val pages = mutableListOf<Uri>()

    private val touchHelper: ItemTouchHelper by lazy {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from !in pages.indices || to !in pages.indices) return false
                Collections.swap(pages, from, to)
                pageAdapter.notifyItemMoved(from, to)
                pageAdapter.notifyItemRangeChanged(min(from, to), max(from, to) - min(from, to) + 1)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
    }

    private val pageAdapter: ImagePageAdapter by lazy {
        ImagePageAdapter(pages, { touchHelper }, ::removeQueuedPage)
    }

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { selected ->
        if (selected.isNullOrEmpty()) return@registerForActivityResult
        val start = pages.size
        pages.addAll(selected)
        pageAdapter.notifyItemRangeInserted(start, selected.size)
        refreshQueueState()
    }

    override fun getBinding(inflater: LayoutInflater): ToolActivityImgToPdfBinding {
        return ToolActivityImgToPdfBinding.inflate(inflater)
    }

    override fun setShowView() {
        pages.addAll(intent.readPages())
        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = pageAdapter
        touchHelper.attachToRecyclerView(binding.rv)
        refreshQueueState()
    }

    override fun setListener() {
        binding.ivBack.setSequentialClickListener { finish() }
        binding.llAddMore.setSequentialClickListener { launchImagePicker() }
        binding.btnSave.setSequentialClickListener {
            if (pages.isEmpty()) {
                Toast.makeText(this, R.string.app_no_images_selected, Toast.LENGTH_SHORT).show()
            } else {
                FileRenamePop(this).createPop(createOutputName(), R.string.app_img_to_pdf, R.string.app_save_pdf) { fileName ->
                    // 将有序图片和文件名封装为统一请求，交给公共执行页；保留启动后关闭队列页的原有流程。
                    startActivity(DocumentProcessActivity.intent(
                        this, DocumentTaskRequest.ImagesToPdf(pages.map(Uri::toString), fileName)
                    ))
                    finish()
                }
            }
        }
    }

    /** 打开系统选择器以添加更多图片页面。 */
    private fun launchImagePicker() {
        photoPicker.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                .build()
        )
    }

    /** 从待处理图片序列中移除一个页面。 */
    private fun removeQueuedPage(position: Int) {
        if (position !in pages.indices) return
        pages.removeAt(position)
        pageAdapter.notifyItemRemoved(position)
        pageAdapter.notifyItemRangeChanged(position, pages.size - position)
        refreshQueueState()
    }

    /** 根据图片队列是否有内容刷新相关控件。 */
    private fun refreshQueueState() {
        binding.btnSave.isEnabled = pages.isNotEmpty()
    }

    /** 生成带时间戳的默认输出名称。 */
    private fun createOutputName(): String {
        return "${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}"
    }

    companion object {
        private const val EXTRA_IMAGE_URIS = "image_pdf_uris"

        /** 使用初始选中的页面打开图片队列。 */
        fun open(context: Context, pages: List<Uri>) {
            context.startActivity(
                Intent(context, ImageQueueActivity::class.java).apply {
                    putParcelableArrayListExtra(EXTRA_IMAGE_URIS, ArrayList(pages))
                }
            )
        }

        /** 读取启动 Intent 中保存的图片页面列表。 */
        @Suppress("DEPRECATION")
        private fun Intent.readPages(): List<Uri> {
            return getParcelableArrayListExtra<Uri>(EXTRA_IMAGE_URIS).orEmpty()
        }
    }
}
