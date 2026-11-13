// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.mergepdf

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStateAtLeast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pdf.editor.R
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.ui.detail.reader.setSequentialClickListener
import com.pdf.editor.databinding.ToolActivityImgToPdfBinding
import com.pdf.editor.ui.tools.document.file.FileListActivity
import com.pdf.editor.ui.detail.reader.ReaderSurfaceActivity
import com.pdf.editor.ui.tools.document.common.DocumentProcessActivity
import com.pdf.editor.ui.tools.document.common.DocumentTaskRequest
import com.pdf.editor.ui.tools.document.common.FileRenamePop
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 独立 PDF 合并队列，复用图片队列的界面结构，文件顺序就是最终 PDF 的拼接顺序。 */
class PdfMergeQueueActivity : BaseActivity<ToolActivityImgToPdfBinding>() {
    // PDF14-MIGRATION: 当前 BaseActivity 的绑定字段；源页面绑定调用保持不变。
    private val binding: ToolActivityImgToPdfBinding get() = aBinding

    private val paths = mutableListOf<String>()
    private var restoredPaths: ArrayList<String>? = null
    // by lazy 表示首次访问时才创建并缓存实例；{ position -> ... } 是接收条目位置的操作回调。
    private val queueAdapter: PdfMergeAdapter by lazy {
        PdfMergeAdapter(paths, { helper }, { position ->
            paths.removeAt(position)
            queueAdapter.notifyDataSetChanged()
            refreshButton()
        }, { position ->
            val file = File(paths[position])
            // PDF14-MIGRATION: 源 open 通过队列 Activity 类型判定纯预览；当前入口显式传参保持相同行为。
            ReaderSurfaceActivity.open(this, file.name, file.absolutePath, recordHistory = false)
        })
    }
    private val helper: ItemTouchHelper by lazy {
        // 此处 or 是整数按位或，将向上、向下两个拖动标志合并；不同于布尔条件中的 ||。
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, holder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = holder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                // indices 是有效下标范围；!in 表示越界，|| 表示任一位置越界就拒绝此次拖动。
                if (from !in paths.indices || to !in paths.indices) return false
                // 移动而非交换，跨越多个条目时仍与 RecyclerView 的移动动画保持一致。
                paths.add(to, paths.removeAt(from))
                queueAdapter.notifyItemMoved(from, to)
                // minOf 取受影响区间起点；abs 取位置差的绝对值，+ 1 把区间两端都计入刷新数量。
                queueAdapter.notifyItemRangeChanged(minOf(from, to), kotlin.math.abs(from - to) + 1)
                return true
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
        })
    }
    /** Add More 始终新建选择页，取消不改变队列，确认后用完整返回集合替换当前队列。 */
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        // == 比较返回码；只有确认成功才处理，取消选择不会替换原队列。
        if (result.resultCode == RESULT_OK) {
            // ?. 在返回 Intent 非 null 时读取路径；orEmpty 将 null 转为空列表。
            // distinct 按完整路径去重并保留首次出现顺序；>= 2 才接受返回的完整选择。
            val selected = result.data?.getStringArrayListExtra(FileListActivity.EXTRA_SELECTED_PATHS).orEmpty().distinct()
            if (selected.size >= 2) {
                paths.clear()
                paths.addAll(selected)
                queueAdapter.notifyDataSetChanged()
                refreshButton()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Bundle? 表示允许为空；?. 仅在存在保存状态时读取，新启动页面得到 null。
        restoredPaths = savedInstanceState?.getStringArrayList(KEY_PATHS)
        // 基类没有调用保存状态的父类实现，单独恢复结果注册表，避免重建后 Add More 结果丢失。
        // ?.let 只处理非 null 的 Bundle；:: 是方法引用，将 Bundle 作为参数传给恢复方法。
        savedInstanceState?.getBundle(KEY_REGISTRY)?.let(activityResultRegistry::onRestoreInstanceState)
        super.onCreate(savedInstanceState)
    }

    override fun getBinding(inflater: LayoutInflater) = ToolActivityImgToPdfBinding.inflate(inflater)

    override fun setShowView() {
        // ?: 优先使用恢复列表，否则读取 Intent；orEmpty 兜底空列表，distinct 去重且不改变先后顺序。
        paths.addAll((restoredPaths ?: intent.getStringArrayListExtra(KEY_PATHS)).orEmpty().distinct())
        binding.tvTitle.setText(R.string.app_pdf_merge_pdf)
        binding.btnSave.setText(R.string.app_pdf_merge_pdf)
        binding.rv.layoutManager = LinearLayoutManager(this)
        binding.rv.adapter = queueAdapter
        helper.attachToRecyclerView(binding.rv)
        refreshButton()
    }

    override fun setListener() {
        binding.ivBack.setSequentialClickListener { finish() }
        binding.llAddMore.setSequentialClickListener { picker.launch(FileListActivity.mergeSelectionIntent(this, paths)) }
        binding.btnSave.setSequentialClickListener {
            // < 2 表示数量不足；return@setSequentialClickListener 只结束本次按钮回调。
            if (paths.size < 2) return@setSequentialClickListener
            showUseEndAdBySave{
                // 默认文件名只使用日期和时间，不再添加 PDF14 品牌前缀。
                val defaultName = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                FileRenamePop(this).createPop(defaultName, R.string.app_pdf_merge_pdf, R.string.app_pdf_merge_pdf) { name ->
                    // 请求使用队列快照，后续执行完全交给公共任务页，队列页随启动关闭。
                    // toList 创建当前顺序的列表快照，后续 paths 的增删不会改变请求中的列表。
                    startActivity(DocumentProcessActivity.intent(this, DocumentTaskRequest.MergePdfs(paths.toList(), name)))
                    finish()
                }
            }
        }
    }

    /** 扫描图片成功转换为 PDF 后展示 pr_use_end 广告，再打开生成的文件。 */
    private fun showUseEndAdBySave(result: () -> Unit) {
        useEndContinuationConsumed = false
        runCatching {
            val adShowManager = ADShowManager(this, "ReaderSurfaceActivity")
            adShowManager.showScreenAD(
                PointConfig.AD_POINT_USE_END,
                ADEnum.INT_AD_USER
            ) { _, _ ->
                continueAfterUseEndAd(result)
            }
        }.onFailure {
            AppLogs.eLog(aTAG, "showScreenAD error:${it.stackTraceToString()}")
            continueAfterUseEndAd(result)
        }
    }

    private var useEndContinuationConsumed = false

    /**
     * 广告结束或被拦截后只打开一次生成结果。
     *
     * 广告关闭回调可能早于 Activity.onResume()，因此不能在回调中立即跳转。
     * 与启动页一致，先等待生命周期恢复到 RESUMED，再检查 Activity 当前状态。
     */
    private fun continueAfterUseEndAd(result: () -> Unit) {
        if (useEndContinuationConsumed) {
            return
        }
        useEndContinuationConsumed = true
        lifecycleScope.launch {
            lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                if (isFinishing || isDestroyed || !getActivityStatus()) {
                    return@withStateAtLeast
                }
                result.invoke()
            }
        }
    }

    /** 删除后允许保留零或一个文件继续添加，但此时不允许开始合并。 */
    private fun refreshButton() {
        // >= 包含等于，因此恰好两个文件即可合并；背景由项目通用状态选择器自动切换。
        binding.btnSave.isEnabled = paths.size >= 2
    }

    companion object {
        private const val KEY_PATHS = "pdf_merge_queue"
        private const val KEY_REGISTRY = "pdf_merge_result_registry"
        /** 首次选择确认后进入新队列；原选择页由调用方关闭。 */
        fun open(context: Context, paths: List<String>) {
            context.startActivity(Intent(context, PdfMergeQueueActivity::class.java)
                .putStringArrayListExtra(KEY_PATHS, ArrayList(paths)))
        }
    }
}
