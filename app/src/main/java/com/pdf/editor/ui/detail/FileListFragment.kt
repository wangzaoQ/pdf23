package com.pdf.editor.ui.detail

import android.content.Intent
import android.graphics.Rect
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.FileUtils
import com.pdf.editor.MyAPP
import com.pdf.editor.R
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.core.BaseFragment2
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.FileManager
import com.pdf.editor.common.tools.PermissionManager
import com.pdf.editor.common.tools.ScanConfig
import com.pdf.editor.data.FilesData
import com.pdf.editor.databinding.PdfFragmentFileListBinding
import com.pdf.editor.feature.viewer.office.OfficeDocumentActivity
import com.pdf.editor.model.DataModel
import com.pdf.editor.feature.files.actions.FileActionsPopup
import com.pdf.editor.feature.files.actions.RenameFilePopup
import com.pdf.editor.ui.detail.reader.ReaderSurfaceActivity // PDF14-MIGRATION: 直接启动 PDF 阅读器，移除 FunActivity 转发层。
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FileListFragment : BaseFragment2<PdfFragmentFileListBinding>() {

    private val viewModel by viewModels<DataModel>({ requireParentFragment() })
    private val fileAdapter = FileListAdapter()
    private var sourceList: List<FilesData> = emptyList()
    private var fromType = ScanConfig.FILE_TYPE_ALL
    private var fromTag = ScanConfig.FROM_TAG_FILE
    private var currentSortType = ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST
    private var isAutoRefresh = false
    private val fileListObserver = Observer<List<FilesData>> { files ->
        fBinding.refresh.isRefreshing = false
        val list = viewModel.filterFilesByType(files, fromType)
        sourceList = list
        updateBySort(currentSortType)
        updateState()
        if (list.isNotEmpty()) {
            fBinding.rv.scrollToPosition(0)
        }
    }

    companion object {
        private const val ARG_TYPE = "type"
        private const val ARG_TAG = "tag"

        fun newInstance(type: Int, tag: String): FileListFragment {
            val fragment = FileListFragment()
            fragment.arguments = android.os.Bundle().apply {
                putInt(ARG_TYPE, type)
                putString(ARG_TAG, tag)
            }
            return fragment
        }
    }

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): PdfFragmentFileListBinding {
        return PdfFragmentFileListBinding.inflate(inflater, container, false)
    }

    override fun setShowView() {
        fromType = arguments?.getInt(ARG_TYPE) ?: 0
        fromTag = arguments?.getString(ARG_TAG) ?: ScanConfig.FROM_TAG_FILE
        currentSortType = LocalDataManager.getSortTypeByFromType(fromType)
        fBinding.rv.apply {
            layoutManager = LinearLayoutManager(rootActivity)
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
        }
    }
    private var filePermissionWatchJob: Job? = null
    private var waitingForFilePermission = false

    private fun handleFilePermissionGranted() {
        waitingForFilePermission = false
        startLoadData()
    }

    override fun setListener() {
        viewModel.allFilesLiveData.observe(this, fileListObserver)
        fBinding.btnNext.setOnClickListener {
            EventManager.posePoint(PointConfig.storage_auth_click)
            filePermissionWatchJob?.cancel()
            waitingForFilePermission = true
            filePermissionWatchJob = viewLifecycleOwner.lifecycleScope.launch {
                while (isActive && waitingForFilePermission) {
                    delay(300)
                    if (PermissionManager.hasAllFilePermission(rootActivity)) {
                        handleFilePermissionGranted()
                        // 将当前宿主 Activity 重新置于前台；Settings 页面属于系统应用，
                        // 这里只能拉回自己的任务，不能直接 finish 系统页面。
                        startActivity(
                            Intent(rootActivity, rootActivity::class.java).apply {
                                addFlags(
                                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            }
                        )
                        break
                    }
                }
            }
            PermissionManager.requestManageAllFilesPermission(rootActivity, {
                if (waitingForFilePermission) handleFilePermissionGranted()
            }, {
                waitingForFilePermission = false
            })
        }
        fBinding.refresh.setOnRefreshListener {
            isAutoRefresh = false
            loadData(force = true)
        }
        fileAdapter.addOnItemChildClickListener(R.id.allFiles) { adapter, _, position ->
            val item = adapter.items.getOrNull(position) ?: return@addOnItemChildClickListener
            item.fileOpenTime = System.currentTimeMillis()
            LocalDataManager.setOpenTime(item.fileId, item.fileOpenTime)
            LocalDataManager.recentFileData = item
            LocalDataManager.saveRecentOpenFiles(item)
            MyAPP.resetLiveData.postValue("${fromType}_${fromTag}")
            if (item.fileType == ScanConfig.FILE_TYPE_PDF) {
                ReaderSurfaceActivity.startFileActivity(rootActivity, item)
            }else{
                OfficeDocumentActivity.startFileActivity(rootActivity, item)
            }
        }
        fileAdapter.addOnItemChildClickListener(R.id.ivMore) { adapter, view, position ->
            adapter.items.getOrNull(position) ?: return@addOnItemChildClickListener
            var data = adapter.items.get(position)
            if (FilesData.isDemoSample(data)) {
                return@addOnItemChildClickListener
            }
            EventManager.posePoint(PointConfig.file_multi_action_click)
            FileActionsPopup(rootActivity).createPop(view) { type ->
                when (type) {
                    0 -> {
                        fBinding.root.postDelayed({
                            RenameFilePopup(rootActivity).createPop(
                                FileManager.getFileNameWithoutExtension(data.fileName)
                            ) { newName ->
                                val oldFilePath = data.filePath
                                val oldFileId = data.fileId
                                val renamedData = FileManager.renameFile(data, newName)
                                    ?: return@createPop

                                // 顺序上先迁缓存，再替换内存列表，最后按当前筛选/排序规则重新出一版列表。
                                // 这样可以保证 UI 刷新时拿到的已经是完整的新数据，不会短暂混入旧 fileId 或旧文件名。
                                LocalDataManager.migrateOpenTime(oldFileId, renamedData.fileId, data.fileOpenTime)
                                LocalDataManager.migrateFilePage(oldFileId, renamedData.fileId)
                                LocalDataManager.updateRenamedFile(oldFilePath, renamedData)
                                ScanConfig.replaceFile(oldFilePath, renamedData)

                                viewModel.getTempList(fromType) {
                                    sourceList = it
                                    updateBySort(currentSortType)
                                    updateState()
                                }
                            }
                        },200)
                    }
                    1 -> {
                        if (FileUtils.delete(data.filePath)) {
                            LocalDataManager.removeDeletedFile(data)
                            ScanConfig.removeFile(data.filePath)
                            MyAPP.deleteFilesLiveData.postValue(data)
                        }
                    }
                }
            }
        }

        MyAPP.resetLiveData.observe(viewLifecycleOwner) {
            if (!isLoad && (it == "resetAll" || it == "${fromType}_${fromTag}" || it.isEmpty())) {
                if (!PermissionManager.hasAllFilePermission(rootActivity)) {
                    submitSourceList(getNoPermissionList())
                    fBinding.llPermission.visibility = View.VISIBLE
                    fBinding.llEmpty.visibility = View.GONE
                }else{
                    fBinding.llPermission.visibility = View.GONE
                    fBinding.llEmpty.visibility = View.GONE
                }
                autoRefresh(force = true)
            }
        }
        MyAPP.deleteFilesLiveData.observe(viewLifecycleOwner) {
            var position = -1
            for (i in 0 until fileAdapter.items.size){
                if (fileAdapter.items[i].fileId == it.fileId){
                    position = i
                    break
                }
            }
            if (position >= 0){
                val newList = fileAdapter.items.toMutableList()
                newList.removeAt(position)
                sourceList = newList
                fileAdapter.submitList(newList)
                updateState()
            }
        }
    }

    override fun startLoadData() {
        if (!PermissionManager.hasAllFilePermission(rootActivity)) {
            submitSourceList(getNoPermissionList())
            fBinding.llPermission.visibility = View.VISIBLE
            fBinding.llEmpty.visibility = View.GONE
            if (LocalDataManager.isFirstPermissionPoint){
                LocalDataManager.isFirstPermissionPoint = false
                EventManager.posePoint(PointConfig.storage_auth_view)
            }
            return
        }
        fBinding.llPermission.visibility = View.GONE
        fBinding.llEmpty.visibility = View.GONE
        viewModel.getTempList(fromType) {
            sourceList = it
            updateBySort(currentSortType)
            updateState()
        }
        autoRefresh(force = false)
    }

    fun search(content: String) {
        if (!isAdded || parentFragment?.isAdded != true || view == null) return
        if (content.isEmpty()){
            fileAdapter.addHighContent("")
            viewModel.getTempList(fromType) {
                sourceList = it
                updateBySort(currentSortType)
                updateState()
            }
            return
        }
        val filtered = viewModel.getSearchList(fromType, content)
        fileAdapter.addHighContent(content)
        submitSourceList(filtered)
        updateState()
    }

    private fun loadData(force: Boolean) {
        if (PermissionManager.hasAllFilePermission(rootActivity)){
            val isScanning = viewModel.startScan(force)
            if (!isScanning) {
                fBinding.refresh.isRefreshing = false
            }
        }else{
            fBinding.refresh.isRefreshing = false
            updateBySort(currentSortType)
            updateState()
        }
    }

    private fun autoRefresh(force: Boolean) {
        isAutoRefresh = true
        fBinding.refresh.isRefreshing = true
        loadData(force)
    }

    private fun submitSourceList(items: List<FilesData>) {
        sourceList = items
        fileAdapter.submitList(buildDisplayList(items))
    }

    fun updateBySort(sortType: String) {
        currentSortType = sortType
        LocalDataManager.setSortTypeByFromType(fromType, currentSortType)
        if (sourceList.isEmpty()) {
            submitSourceList(getNoPermissionList())
            return
        }
        val sorted = when (currentSortType) {
            ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST -> sourceList.sortedWith(
                compareByDescending<FilesData> { it.fileOpenTime }.thenByDescending { it.fileTime }
            )
            ScanConfig.SORT_TYPE_OPEN_TIME_OLDEST -> sourceList.sortedWith(
                compareBy<FilesData> { it.fileOpenTime }.thenBy { it.fileTime }
            )
            ScanConfig.SORT_TYPE_FILE_NAME_A_Z -> sourceList.sortedBy { it.fileName.lowercase() }
            ScanConfig.SORT_TYPE_FILE_NAME_Z_A -> sourceList.sortedByDescending { it.fileName.lowercase() }
            else -> sourceList
        }
        submitSourceList(sorted)
    }

    fun getCurrentSortType(): String {
        return currentSortType
    }

    private fun updateState() {
        val hasPermission = PermissionManager.hasAllFilePermission(rootActivity)
        val showEmpty = hasPermission && fileAdapter.items.isEmpty()
        fBinding.llPermission.visibility = if (hasPermission) View.GONE else View.VISIBLE
        fBinding.llEmpty.visibility = if (showEmpty) View.VISIBLE else View.GONE
    }

    private fun getNoPermissionList(): List<FilesData> {
        return emptyList()
    }

    private fun buildDisplayList(items: List<FilesData>): List<FilesData> {
        if (fromType != ScanConfig.FILE_TYPE_ALL && fromType != ScanConfig.FILE_TYPE_PDF) {
            return items
        }
        val demoItem = FilesData.createDemoSample(rootActivity) ?: return items
        return buildList(items.size + 1) {
            add(demoItem)
            addAll(items.filterNot {
                FilesData.isDemoSample(it) ||
                    it.filePath == demoItem.filePath ||
                    it.fileName.equals(demoItem.fileName, ignoreCase = true)
            })
        }
    }

    override fun onDestroy() {
        viewModel.allFilesLiveData.removeObservers(this)
        super.onDestroy()
    }

    val Int.dp: Int
        get() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            resources.displayMetrics
        ).toInt()
}
