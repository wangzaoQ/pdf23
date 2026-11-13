// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.splitpdf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.pdf.editor.R
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.ui.detail.reader.setSequentialClickListener
import com.pdf.editor.databinding.ToolActivityPdfSplitQueueBinding
import com.pdf.editor.ui.detail.reader.ReaderSurfaceActivity
import com.pdf.editor.ui.tools.document.common.DocumentProcessActivity
import com.pdf.editor.ui.tools.document.common.DocumentTaskRequest
import com.pdf.editor.ui.tools.document.common.FileRenamePop
import java.io.File

/** 拆分队列沿用合并队列的视觉排列，但页面、布局和数据独立，只删除、不排序、不添加。 */
class PdfSplitQueueActivity : BaseActivity<ToolActivityPdfSplitQueueBinding>() {
    // PDF14-MIGRATION: 当前 BaseActivity 的绑定字段；源页面绑定调用保持不变。
    private val binding: ToolActivityPdfSplitQueueBinding get() = aBinding

    // by viewModels 按 Activity 作用域获取实例；by lazy 首次访问时读取一次启动路径。
    private val viewModel by viewModels<PdfSplitViewModel>()
    private val sourcePath by lazy { intent.getStringExtra(EXTRA_SOURCE).orEmpty() }
    private val pageAdapter by lazy {
        // ::remove 是方法引用；预览只定位原始页，不携带 SPLIT_PDF 操作按钮，避免递归进入队列。
        PdfSplitAdapter(viewModel::remove) { index ->
            ReaderSurfaceActivity.open(this, File(sourcePath).name, sourcePath,
                initialPageIndex = index, recordHistory = false)
        }
    }

    override fun getBinding(inflater: LayoutInflater) = ToolActivityPdfSplitQueueBinding.inflate(inflater)

    override fun setShowView() {
        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = pageAdapter
        refreshState()
        viewModel.load(sourcePath)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showStatusBarText(false)
    }

    override fun setListener() {
        binding.ivBack.setSequentialClickListener { finish() }
        binding.btnSave.setSequentialClickListener {
            // orEmpty 处理尚未加载的 null；map 按列表顺序提取原始下标，得到本次任务的页码快照。
            val retained = viewModel.pages.value.orEmpty().map { it.sourceIndex }
            if (retained.isEmpty()) return@setSequentialClickListener
            FileRenamePop(this).createPop(File(sourcePath).nameWithoutExtension, R.string.app_split_pdf, R.string.app_split_action) { name ->
                startActivity(DocumentProcessActivity.intent(this,
                    DocumentTaskRequest.SplitPdf(sourcePath, retained, name)))
                finish()
            }
        }
    }

    override fun setDataListener() {
        viewModel.pages.observe(this) { pages -> pageAdapter.submit(pages); refreshState() }
        viewModel.loading.observe(this) { refreshState() }
        viewModel.error.observe(this) { message ->
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /** 缩略图加载时不可提交，删除全部页面时保留空态且禁用 Split。 */
    private fun refreshState() {
        // == true 仅在加载值为 true 时成立；! 取反，&& 要求已加载且非空同时成立。
        val loading = viewModel.loading.value == true
        val hasPages = viewModel.pages.value.orEmpty().isNotEmpty()
        binding.loading.isVisible = loading
        binding.emptyState.isVisible = !loading && !hasPages
        binding.btnSave.isEnabled = !loading && hasPages
    }

    companion object {
        private const val EXTRA_SOURCE = "split_pdf_source"
        /** 来源预览确认后进入独立队列，传递源文件路径，不改变源文件或已有工具队列。 */
        fun open(context: Context, sourcePath: String) {
            context.startActivity(Intent(context, PdfSplitQueueActivity::class.java).putExtra(EXTRA_SOURCE, sourcePath))
        }
    }
}
