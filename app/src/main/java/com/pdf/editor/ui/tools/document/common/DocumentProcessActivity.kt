// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.common

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.core.view.isVisible
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import com.pdf.editor.R
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.databinding.ToolActivityImgMergeBinding
import com.pdf.editor.ui.tools.document.imagepdf.MergeSuccessActivity

/**
 * 工具公共执行承接页：读取请求、展示状态，并沿用现有成功页和失败提示。
 * 图片沿用原有模拟进度，Word 使用不确定进度，等待本地渲染和打印完成。
 * 文件处理交给 ViewModel 和 Executor，后续接入工具无需在页面内编写转换算法。
 */
class DocumentProcessActivity : BaseActivity<ToolActivityImgMergeBinding>() {
    // PDF14-MIGRATION: 当前 BaseActivity 的绑定字段；源页面绑定调用保持不变。
    private val binding: ToolActivityImgMergeBinding get() = aBinding

    // by lazy 首次访问时执行并缓存；Provider 后的 [] 调用其 get 方法，按 class.java 指定的类型获取 ViewModel。
    private val viewModel by lazy { ViewModelProvider(this)[DocumentBuildViewModel::class.java] }
    private var progressAnimator: ValueAnimator? = null
    // 真实页进度可用于 Word 和 PNG；保存阶段按任务替换文案。
    private var exportingImages = false
    // PDF 合并以文件为进度单位，不能套用转换任务的页数文案。
    private var mergingPdfs = false
    /** 拆分统计保留页的真实处理进度，最后保存阶段使用独立文案。 */
    private var splittingPdf = false

    /** 复用原布局，等实际接入其他工具时再根据任务需要扩展展示。 */
    override fun getBinding(inflater: LayoutInflater) = ToolActivityImgMergeBinding.inflate(inflater)

    /** 读取统一请求；缺少参数或图片为空时沿用原来的直接退出行为。 */
    @Suppress("DEPRECATION")
    override fun setShowView() {
        // as? 类型不匹配时返回 null；|| 表示任一异常条件成立，&& 要求图片任务且列表为空同时成立。
        val request = intent.getSerializableExtra(EXTRA_REQUEST) as? DocumentTaskRequest
        if (request == null || (request is DocumentTaskRequest.ImagesToPdf && request.imageUris.isEmpty())) {
            finish()
            return
        }
        // 每个任务使用对应的大图；穷举请求类型，新增工具时必须补齐图标。
        binding.ivMergePdf.setImageResource(when (request) {
            is DocumentTaskRequest.ImagesToPdf -> R.drawable.ic_tool_i2p
            is DocumentTaskRequest.WordToPdf -> R.drawable.ic_tool_w2p
            is DocumentTaskRequest.PdfToWord -> R.drawable.ic_tool_p2w
            is DocumentTaskRequest.PdfToImages -> R.drawable.ic_tool_p2i
            is DocumentTaskRequest.MergePdfs -> R.drawable.ic_tool_merge
            is DocumentTaskRequest.SplitPdf -> R.drawable.ic_tool_split
        })
        if (request is DocumentTaskRequest.ImagesToPdf) {
            binding.tvTitle.setText(R.string.app_img_to_pdf)
            binding.tvProgressHint.setText(R.string.app_pdf_creating_hint)
        }
        // Word 没有准确的已处理页数，展示英文等待提示，仅隐藏图片数量行。
        if (request is DocumentTaskRequest.WordToPdf) {
            binding.tvTitle.setText(R.string.app_word_to_pdf)
            binding.progressTextContainer.isVisible = true
            binding.tvProgressHint.setText(R.string.app_pdf_creating_hint)
            binding.imageCountContainer.isVisible = false
        }
        // PDF 转 Word 显示源页实际进度，不使用图片合并的计数文案和模拟动画。
        if (request is DocumentTaskRequest.PdfToWord) {
            binding.tvTitle.setText(R.string.app_pdf_to_word)
            binding.tvProgressHint.setText(R.string.app_word_creating_hint)
            binding.imageCountContainer.isVisible = false
        }
        exportingImages = request is DocumentTaskRequest.PdfToImages
        if (exportingImages) {
            binding.tvTitle.setText(R.string.app_pdf_to_image)
            binding.tvProgressHint.setText(R.string.app_images_creating_hint)
            binding.imageCountContainer.isVisible = false
        }
        // is 是类型判断，得到 Boolean，用于后续选择文件级进度和保存文案。
        mergingPdfs = request is DocumentTaskRequest.MergePdfs
        if (mergingPdfs) {
            binding.tvTitle.setText(R.string.app_pdf_merging)
            binding.tvProgressHint.setText(R.string.app_pdf_merge_hint)
            binding.progressTextContainer.isVisible = true
            binding.imageCountContainer.isVisible = false
        }
        // is 判断拆分请求；仅替换拆分展示，不更改合并和图片任务的状态推进。
        splittingPdf = request is DocumentTaskRequest.SplitPdf
        if (splittingPdf) {
            binding.tvTitle.setText(R.string.app_split_pdf)
            binding.tvProgressHint.setText(R.string.app_pdf_split_hint)
            binding.progressTextContainer.isVisible = true
            binding.imageCountContainer.isVisible = false
        }
        viewModel.start(request)
    }

    /** 当前执行页没有额外操作，保持原有交互。 */
    override fun setListener() = Unit

    /**
     * LiveData 随 Activity 生命周期自动启停观察，页面销毁时自动移除观察者。
     * observe 在 STARTED 和 RESUMED 接收状态；延迟跳转单独等待页面恢复交互。
     */
    override fun setDataListener() {
        viewModel.state.observe(this) { state ->
            renderState(state)
        }
    }


    /** 统一渲染入口；关闭中的页面不再处理，成功结果仍由 ViewModel 保证只消费一次。 */
    private fun renderState(state: DocumentTaskState) {
        // || 表示正在关闭或已经销毁任一成立就提前返回；when/is 按具体状态类型执行对应分支。
        if (isFinishing || isDestroyed) return
        when (state) {
            is DocumentTaskState.Running -> if (state.completed != null) {
                // != null 表示有真实完成数；总数 == 0 或完成数 == 总数时，用不确定进度表示等待或保存。
                binding.progress.isIndeterminate = state.total == 0 || state.completed == state.total
                // 完成数 * 100 / 总数计算整数百分比；先转 Long 避免乘法溢出，总数 > 0 才做除法。
                binding.progress.progress = if (state.total > 0) (state.completed.toLong() * 100 / state.total).toInt() else 0
                // if/else 表达式返回文案；先区分是否保存，再按合并、图片、Word 任务选对应资源。
                binding.tvProgressHint.text = if (state.completed == state.total) getString(
                    if (splittingPdf) R.string.app_pdf_split_saving else if (mergingPdfs) R.string.app_pdf_merge_saving else if (exportingImages) R.string.app_images_saving else R.string.app_word_saving)
                    else getString(if (splittingPdf) R.string.app_pdf_split_progress else if (mergingPdfs) R.string.app_pdf_merge_progress else R.string.app_word_page_progress, state.completed, state.total)
            } else if (state.total == 0) {
                binding.progress.isIndeterminate = true
            } else if (progressAnimator == null) runProgressSequence(state.total)
            is DocumentTaskState.Success -> {
                progressAnimator?.cancel()
                binding.progress.isIndeterminate = false
                binding.progress.progress = 100
                // 延迟受页面生命周期约束，关闭时自动取消；后台等待恢复后才消费结果。
                // first 等待首个满足条件的生命周期状态；!isFinishing 确认未关闭，?.let 只消费非 null 结果。
                lifecycleScope.launch {
                    delay(500)
                    lifecycle.currentStateFlow.first { it.isAtLeast(Lifecycle.State.RESUMED) }
                    if (!isFinishing) viewModel.takeResult()?.let { result ->
                        openResult(result)
                        finish()
                    }
                }
            }
            is DocumentTaskState.Failed -> {
                // ?. 只在动画存在时取消；?: 在任务未指定错误文案时使用默认 PDF 创建失败提示。
                progressAnimator?.cancel()
                Toast.makeText(this, state.messageRes ?: R.string.app_create_pdf_failed, Toast.LENGTH_LONG).show()
                finish()
            }
            DocumentTaskState.Idle -> Unit
        }
    }

    /** 保留原有 2.5 秒推进至 90% 的视觉反馈；数值不是处理器返回的真实进度。 */
    private fun runProgressSequence(total: Int) {
        binding.tvProgress.text = getString(R.string.app_merge_count, 0, total)
        progressAnimator = ValueAnimator.ofInt(0, 90).apply {
            duration = 2500L
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val value = animator.animatedValue as Int
                binding.progress.progress = value
                val completed = ((value / 100f) * total).toInt().coerceIn(0, total)
                binding.tvProgress.text = getString(R.string.app_merge_count, completed, total)
            }
            start()
        }
    }

    /** 当前任务输出单个文件，成功页根据 PDF / DOCX 类型决定预览方式。 */
    private fun openResult(result: DocumentTaskResult) {
        // PNG 不对应单个文件路径，使用相册结果入口，旧文档任务保持原路由。
        // ?.let 在存在相册结果时打开相册成功页；为 null 时 ?: 才执行普通文档分支。
        // single() 要求文件列表恰好有一个元素，防止把不符合协议的多文件结果当成单文件。
        result.images?.let { MergeSuccessActivity.startImages(this, it) }
            ?: MergeSuccessActivity.start(this, result.files.single().absolutePath)
    }

    /** 动画绑定当前视图，随页面释放；任务生命周期由 ViewModel 管理。 */
    override fun onDestroy() {
        progressAnimator?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_REQUEST = "document_task_request"

        /** 统一构造入口 Intent，调用方组装具体请求，无需依赖执行页内部参数键。 */
        fun intent(context: Context, request: DocumentTaskRequest) =
            Intent(context, DocumentProcessActivity::class.java).putExtra(EXTRA_REQUEST, request)
    }
}
