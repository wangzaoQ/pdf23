package com.pdf.editor.model

import android.os.Environment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pdf.editor.common.core.BaseDataModel
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.Scan1
import com.pdf.editor.common.tools.ScanConfig
import com.pdf.editor.data.FilesData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicInteger

/** Home 下所有文件分类页共享的扫描 ViewModel。 */
class DataModel : BaseDataModel() {

    private val _allFilesLiveData = MutableLiveData<List<FilesData>>()
    val allFilesLiveData: LiveData<List<FilesData>> = _allFilesLiveData

    private var scanJob: Job? = null
    private val scanRequestId = AtomicInteger(0)
    @Volatile
    private var hasCompletedScan = false

    /**
     * 请求全量扫描。共享作用域内已有扫描时直接复用，避免多个分类重复扫描磁盘。
     * 首次加载使用 force=false，后续分类页会直接复用已经完成的全量快照。
     */
    fun startScan(force: Boolean): Boolean {
        if (scanJob?.isActive == true) return true
        if (!force && hasCompletedScan) return false

        val requestId = scanRequestId.incrementAndGet()
        scanJob = loadData(loadBack = {
            try {
                val scan = Scan1(
                    parentDirectory = Environment.getExternalStorageDirectory(),
                    waitTime = 0L,
                    onProgressBatch = {},
                    onCompleteFilesData = { filesData ->
                        ensureActive()
                        if (requestId != scanRequestId.get()) {
                            AppLogs.dLog(TAG, "ignore stale scan result requestId=$requestId")
                        } else {
                            ScanConfig.allFileList = filesData.toMutableList()
                            hasCompletedScan = true
                            _allFilesLiveData.postValue(filesData)
                        }
                    }
                )
                scan.scanStart()
            } catch (_: CancellationException) {
                AppLogs.dLog(TAG, "scan canceled requestId=$requestId")
            }
        }, failBack = {
            if (it !is CancellationException) {
                failLiveData.postValue("scan data error")
                _allFilesLiveData.postValue(ScanConfig.allFileList.toList())
            }
        }, type = 1)
        return scanJob != null
    }

    /**
     * 从最近一次扫描的全量快照中，临时构造某个分类页的默认列表。
     *
     * 这个方法常用于：
     * - 页面首次恢复显示
     * - 搜索框清空后恢复默认列表
     * - 改名、删除后按当前分类重新刷一遍
     *
     * 这里不传排序时，会走默认排序分支；
     * 具体显示时，调用方还会再按自己的 currentSortType 做一次整理。
     */
    fun getTempList(type: Int, onResult: (list: MutableList<FilesData>) -> Unit = {}) {
        onResult.invoke(buildEndList(ScanConfig.allFileList, type, sortType = null))
    }

    /**
     * 在全量快照里做“当前分类页内搜索”。
     *
     * 注意搜索顺序：
     * 1. 先按当前页 type 过滤
     * 2. 再按文件名关键字匹配
     *
     * 这样可以避免在 PDF 页里搜关键字时，把 Word / Excel 等其它类型也带出来。
     */
    fun getSearchList(type: Int, content: String): MutableList<FilesData> {
        val keyword = content.trim()
        if (keyword.isEmpty()) {
            return buildEndList(ScanConfig.allFileList, type, sortType = null)
        }
        return filterFilesByType(ScanConfig.allFileList, type)
            .filterTo(mutableListOf()) { it.fileName.contains(keyword, ignoreCase = true) }
    }

    /**
     * 把一份原始文件列表整理成当前页面最终要显示的顺序。
     *
     * 处理流程固定为两步：
     * 1. 按 type 过滤
     * 2. 按 sortType 排序
     *
     * 之所以把“过滤”和“排序”封装在这里，是为了保证：
     * - 扫描完成后的首屏展示
     * - 临时恢复列表
     * - 搜索清空后的恢复
     *
     * 这些路径最终使用的是同一套规则，避免不同入口各自拼逻辑又互相不一致。
     */
    private fun buildEndList(
        filesData: List<FilesData>,
        type: Int,
        sortType: String?
    ): MutableList<FilesData> {
        val matched = filterFilesByType(filesData, type)
        return when (sortType ?: ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST) {
            ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST -> matched
                .sortedWith(compareByDescending<FilesData> { it.fileOpenTime }.thenByDescending { it.fileTime })
                .toMutableList()
            ScanConfig.SORT_TYPE_OPEN_TIME_OLDEST -> matched
                .sortedWith(compareBy<FilesData> { it.fileOpenTime }.thenBy { it.fileTime })
                .toMutableList()
            ScanConfig.SORT_TYPE_FILE_NAME_A_Z -> matched
                .sortedBy { it.fileName.lowercase() }
                .toMutableList()
            ScanConfig.SORT_TYPE_FILE_NAME_Z_A -> matched
                .sortedByDescending { it.fileName.lowercase() }
                .toMutableList()
            else -> matched
                .sortedWith(compareByDescending<FilesData> { it.fileOpenTime }.thenByDescending { it.fileTime })
                .toMutableList()
        }
    }

    /**
     * 只按文件类型做一次纯过滤，不负责排序。
     *
     * 这个方法是整个列表构造的最底层公共逻辑。
     * 搜索、默认列表恢复、扫描完成后的结果回推，都会先走这里。
     *
     * 单独拆出来的好处是：
     * - 类型边界统一，不会这个入口按 type 过滤，另一个入口忘了过滤
     * - 后面如果要新增新的文件类型，只需要集中改这一处
     */
    fun filterFilesByType(filesData: List<FilesData>, type: Int): MutableList<FilesData> {
        return filesData.filterTo(mutableListOf()) { file ->
            when (type) {
                ScanConfig.FILE_TYPE_ALL -> file.fileType in setOf(
                    ScanConfig.FILE_TYPE_PDF,
                    ScanConfig.FILE_TYPE_DOC,
                    ScanConfig.FILE_TYPE_EXCEL,
                    ScanConfig.FILE_TYPE_PPT,
                    ScanConfig.FILE_TYPE_TEXT
                )
                ScanConfig.FILE_TYPE_PDF -> file.fileType == ScanConfig.FILE_TYPE_PDF
                ScanConfig.FILE_TYPE_DOC -> file.fileType == ScanConfig.FILE_TYPE_DOC
                ScanConfig.FILE_TYPE_EXCEL -> file.fileType == ScanConfig.FILE_TYPE_EXCEL
                ScanConfig.FILE_TYPE_PPT -> file.fileType == ScanConfig.FILE_TYPE_PPT
                ScanConfig.FILE_TYPE_TEXT -> file.fileType == ScanConfig.FILE_TYPE_TEXT
                else -> false
            }
        }
    }
}
