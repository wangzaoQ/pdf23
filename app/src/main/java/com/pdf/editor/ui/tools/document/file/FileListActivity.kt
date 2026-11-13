// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.file

import android.view.LayoutInflater
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.PersistableBundle
import android.util.TypedValue
import android.view.View
import java.io.File
import com.pdf.editor.R
import com.pdf.editor.ui.tools.document.mergepdf.PdfMergeQueueActivity
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.ui.tools.document.file.ToolSessionStore as LocalCacheStore
import com.pdf.editor.common.config.PointConfig as EventConfig
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.ui.tools.document.file.ToolPermissionManager as PermissionManager
import com.pdf.editor.ui.tools.document.file.FileCategory
import com.pdf.editor.ui.tools.document.file.ScannedFile
import com.pdf.editor.databinding.ToolActivityFileListBinding
import com.pdf.editor.ui.detail.reader.ReaderSurfaceActivity

class FileListActivity : BaseActivity<ToolActivityFileListBinding>() {
    // PDF14-MIGRATION: 当前 BaseActivity 的绑定字段；源页面绑定调用保持不变。
    private val binding: ToolActivityFileListBinding get() = aBinding

    private val scanModel by viewModels<ScanModel>()
    private val fileAdapter = FileSelectAdapter()
    private var isScanning = false
    /** 路径作为本地文件标识；LinkedHashSet 保留队列顺序，新勾选文件追加到末尾。 */
    private val mergeSelection = linkedSetOf<String>()
    // get() 每次读取时计算；== 比较入口是否为合并模式，结果是 Boolean。
    private val isMerge: Boolean get() = selectAction == FileSelectAction.MERGE_PDF

    // ?.let 只在入口字符串非 null 时执行；value 是该字符串。
    // runCatching 捕获枚举解析异常，getOrNull() 在解析失败时返回 null。
    private val selectAction: FileSelectAction?
        get() = intent.getStringExtra(EXTRA_FILE_ACTION)
            ?.let { value -> runCatching { FileSelectAction.valueOf(value) }.getOrNull() }

    private val fileCategory: FileCategory
        // ?. 安全读取分类；入口为空时，?: 使用右侧 ALL 作为默认值。
        get() = selectAction?.category ?: FileCategory.ALL

    override fun getBinding(inflater: LayoutInflater): ToolActivityFileListBinding {
        return ToolActivityFileListBinding.inflate(inflater)
    }

    override fun setListener() {
        binding.ivBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener {
            // 确认前再次检查文件是否仍可读取；文件被外部删除时不能带着无效选择进入下一步。
            // removeAll 删除条件为 true 的项，it 是当前路径；! 表示取反，|| 表示任一条件成立即可。
            mergeSelection.removeAll { !File(it).isFile || !File(it).canRead() }
            fileAdapter.notifyDataSetChanged()
            refreshMergeButton()
            // < 2 表示不足两个文件；带标签的 return 只结束当前点击回调，不退出 Activity。
            if (mergeSelection.size < 2) return@setOnClickListener
            if (intent.getBooleanExtra(EXTRA_RETURN_SELECTION, false)) {
                setResult(RESULT_OK, Intent().putStringArrayListExtra(EXTRA_SELECTED_PATHS, ArrayList(mergeSelection)))
            } else {
                PdfMergeQueueActivity.open(this, mergeSelection.toList())
            }
            // 首次进入队列和 Add More 的结果回传都关闭当前选择页，不在返回栈中遗留选择页。
            finish()
        }
        binding.enableFileAccess.setOnClickListener {
            PermissionManager.request(
                context = this,
                onGranted = {
                    isScanning = true
                    renderState(fileAdapter.items, hasPermission = true)
                    scanModel.startDeviceScan(this, force = true)
                },
                onDenied = {
                    isScanning = false
                    renderState(fileAdapter.items, hasPermission = false)
                }
            )
        }
        fileAdapter.setOnItemClickListener { adapter, _, position ->
           // _ 表示不使用该回调参数；indices 是有效下标范围，!in 表示下标不在范围内。
           if (position !in fileAdapter.items.indices){
               return@setOnItemClickListener
           }
            var data = fileAdapter.items.get(position)
            if (isMerge) {
                // remove 返回是否成功删除；! 将结果取反，原本未选中时才执行 add，实现勾选切换。
                if (!mergeSelection.remove(data.absolutePath)) mergeSelection.add(data.absolutePath)
                fileAdapter.submitList(filterFiles(fileAdapter.items))
                fileAdapter.notifyDataSetChanged()
                refreshMergeButton()
                // 只结束本次条目点击，避免合并多选继续进入下方的单文件预览分支。
                return@setOnItemClickListener
            }
            fileAdapter.select(position)
            fileAdapter.notifyDataSetChanged()
            // PDF 转 Word 先进入已有预览页，在预览页确认转换，不从文件列表直接执行任务。
            // || 表示两种入口任意一种匹配即可；分支内的 !! 断言入口非 null，依据是前面的枚举匹配。
            // || 表示任一 PDF 工具匹配即可；拆分也先预览源文件，由预览页按钮进入页队列。
            if (selectAction == FileSelectAction.PDF_TO_WORD || selectAction == FileSelectAction.PDF_TO_IMAGE || selectAction == FileSelectAction.SPLIT_PDF) {
                // 两种 PDF 转换都先预览确认文件，转换按钮由来源标记决定。
                ReaderSurfaceActivity.open(this, data.name, data.absolutePath, selectAction!!.name)
            } else if (selectAction == FileSelectAction.WORD_TO_PDF){
                ContentSurfaceActivity.open(
                    context = this@FileListActivity,
                    fileName = data.name,
                    absolutePath = data.absolutePath,
                    type = FileSelectAction.WORD_TO_PDF.name
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showStatusBarText(false)
    }

    override fun setShowView() {
        // 仅从启动参数初始化已选文件，供 Add More 回显当前队列，不保存或恢复页面临时选择。
        // orEmpty() 将 null 转为空列表；addAll 将传入路径按原顺序加入有序集合。
        mergeSelection.addAll(intent.getStringArrayListExtra(EXTRA_SELECTED_PATHS).orEmpty())
        // 只有合并模式开启多选和底部确认按钮，已有转换入口继续单击预览。
        if (isMerge) {
            fileAdapter.selectedPaths = mergeSelection
            binding.tvTitle.setText(R.string.app_merge_select_title)
        }
        if (!isMerge) {
            binding.tvTitle.setText(when (selectAction) {
                FileSelectAction.WORD_TO_PDF -> R.string.app_select_docx
                FileSelectAction.PDF_TO_WORD, FileSelectAction.PDF_TO_IMAGE,
                FileSelectAction.SPLIT_PDF -> R.string.app_select_pdf
                else -> R.string.app_selected_one_file
            })
        }
        binding.btnSave.isVisible = isMerge
        refreshMergeButton()
        // apply 内部的 this 是 RecyclerView，可直接设置属性；this@FileListActivity 指向外层页面。
        binding.rv.apply {
            layoutManager = LinearLayoutManager(this@FileListActivity)
            adapter = fileAdapter
            if (itemDecorationCount == 0) {
                addItemDecoration(object : RecyclerView.ItemDecoration() {
                    private val itemSpacing = 8.dp

                    override fun getItemOffsets(
                        outRect: Rect,
                        view: View,
                        parent: RecyclerView,
                        state: RecyclerView.State
                    ) {
                        val position = parent.getChildAdapterPosition(view)
                        if (position == RecyclerView.NO_POSITION) return
                        outRect.bottom = if (position == state.itemCount - 1) 0 else itemSpacing
                    }
                })
            }
            setHasFixedSize(true)
        }
        val cachedFiles = filterFiles(LocalCacheStore.cachedScannedFiles)
        fileAdapter.submitList(cachedFiles)
        renderState(cachedFiles, PermissionManager.hasPermission(this))
    }

    override fun setDataListener() {
        scanModel.scanState.observe(this) { state ->
            isScanning = false
            val filteredFiles = filterFiles(state.files)
            fileAdapter.submitList(filteredFiles)
            renderState(filteredFiles, PermissionManager.hasPermission(this))
        }

        // && 要求两边同时成立：列表为空且已有文件权限时才首次扫描，不额外弹出权限请求。
        if (fileAdapter.items.isEmpty() && PermissionManager.hasPermission(this)) {
            isScanning = true
            renderState(emptyList(), hasPermission = true)
            scanModel.startDeviceScan(this)
        }
    }

    /** Word 转 PDF 当前只支持离线 DOCX；其他入口继续保留原分类过滤规则。 */
    private fun filterFiles(files: List<ScannedFile>): List<ScannedFile> {
        // 已选文件补入扫描结果，避免缓存尚未更新时丢失队列中的文件；顶部顺序与队列一致。
        // mapNotNull 将每个路径 it 转为 ScannedFile，并丢弃转换得到的 null，不修改原选择集合。
        // + 拼接两个列表并生成新列表；if/else 是表达式，非合并入口直接使用原 files。
        val candidates = if (isMerge) files + mergeSelection.mapNotNull { ScannedFile.fromFile(File(it)) } else files
        // 分类 filter 先筛文件类型；distinctBy 按 absolutePath 去重，同一路径只保留第一项。
        // 后面的 filter 只保留条件为 true 的文件；it 在这里指当前 ScannedFile。
        val filtered = fileCategory.filter(candidates).distinctBy { it.absolutePath }.filter {
            // != 表示不是 Word 转 PDF，此时 || 左侧已成立，不限制扩展名；否则右侧必须为 DOCX。
            // ignoreCase = true 表示比较扩展名时忽略大小写，.DOCX 也符合条件。
            selectAction != FileSelectAction.WORD_TO_PDF || it.name.endsWith(".docx", ignoreCase = true)
        }
        // !isMerge 表示非合并入口；提前返回，只有合并入口需要已选文件置顶。
        if (!isMerge) return filtered
        // withIndex 为路径附加从 0 开始的位置；associate 将每项转成 Map 中的一组键值。
        // it.value 是路径，it.index 是位置；to 创建 Pair，因此得到“路径 -> 队列位置”的映射。
        val order = mergeSelection.withIndex().associate { it.value to it.index }
        // sortedBy 按位置升序生成新列表；[] 查询 Map，未选路径查不到时用 ?: 取最大整数，排在末尾。
        return filtered.sortedBy { order[it.absolutePath] ?: Int.MAX_VALUE }
    }

    /** 首次选择与修改选择共用至少两个 PDF 的门槛，并显示当前选择数量。 */
    private fun refreshMergeButton() {
        // >= 2 包含恰好两个文件；按钮背景由项目通用状态选择器根据 enabled 自动切换。
        binding.btnSave.isEnabled = mergeSelection.size >= 2
        binding.btnSave.text = getString(R.string.app_merge_continue, mergeSelection.size)
    }

    private fun renderState(files: List<ScannedFile>, hasPermission: Boolean) {
        // ! 表示取反；&& 要求全部条件成立，例如空态只在有权限、无文件且未扫描时显示。
        binding.permissionState.isVisible = !hasPermission
        binding.emptyState.isVisible = hasPermission && files.isEmpty() && !isScanning
        binding.rv.isVisible = hasPermission && files.isNotEmpty()
    }

    companion object {
        private const val EXTRA_FILE_ACTION = "extra_file_action"
        const val EXTRA_SELECTED_PATHS = "merge_selected_paths"
        private const val EXTRA_RETURN_SELECTION = "merge_return_selection"

        /** Add More 创建新的选择页；返回的是完整最终集合，支持取消原来已选的文件。 */
        fun mergeSelectionIntent(context: android.content.Context, paths: List<String>) =
            Intent(context, FileListActivity::class.java)
                .putExtra(EXTRA_FILE_ACTION, FileSelectAction.MERGE_PDF.name)
                .putExtra(EXTRA_RETURN_SELECTION, true)
                .putStringArrayListExtra(EXTRA_SELECTED_PATHS, ArrayList(paths))

        fun open(
            context: android.content.Context,
            action: FileSelectAction
        ) {
            context.startActivity(
                android.content.Intent(context, FileListActivity::class.java)
                    .putExtra(EXTRA_FILE_ACTION, action.name)
            )
        }
    }

    val Int.dp: Int
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            resources.displayMetrics
        ).toInt()
}
