// PDF14-MIGRATION: 扫描流程原样迁移；当前项目 ViewModel 与缓存适配，未引入源首页文件操作。
package com.pdf.editor.ui.tools.document.file

import android.content.Context
import android.os.Environment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.pdf.editor.common.core.BaseDataModel
import com.pdf.editor.ui.tools.document.file.ToolSessionStore as LocalCacheStore
import com.pdf.editor.ui.tools.document.file.FileCategory
import com.pdf.editor.ui.tools.document.file.ScannedFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页四个分类页共享的扫描 ViewModel。
 *
 * 这里特意不包含 `docflow` 中的收藏筛选、收藏缓存、最近打开时间和详情页更新逻辑。
 * 四个 ListFragment 通过 Activity 级 ViewModel 观察同一份扫描结果，设备目录只需要扫描一次，
 * 各页面再根据自己的 [com.pdf.editor.ui.tools.document.file.FileCategory] 在内存中完成分类。
 */
class ScanModel : BaseDataModel() {

    data class ScanState(
        val files: List<ScannedFile> = emptyList(),
        val isScanning: Boolean = false,
        val errorMessage: String? = null
    )

    // No emission until a result exists; an empty result must not be confused with initialization.
    private val mutableScanState = MutableLiveData<ScanState>()
    val scanState: LiveData<ScanState> = mutableScanState

    private var scanJob: Job? = null
    private var hasCompletedDeviceScan = false

    /**
     * 在后台扫描共享存储并发布完整列表。
     *
     * 缓存读取由 ListFragment 在请求扫描前直接处理，不改变这里原有的扫描流程。扫描成功
     * 后覆盖 MMKV 缓存并发布最新结果；[force] 允许下拉刷新再次扫描。
     */
    fun startDeviceScan(context: Context, force: Boolean = false) {
        if (scanJob?.isActive == true) return
        if (hasCompletedDeviceScan && !force) return

        val appContext = context.applicationContext

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val scannedFiles = FileScanner().scan(Environment.getExternalStorageDirectory())

                // 参考 docflow：扫描完成后在 IO 线程为每个 PDF 生成第一页缩略图。
                // Word/Excel 不做额外处理，继续使用各自的静态类型图标。
                val filesWithThumbnails = scannedFiles.map { file ->
                    if (file.category == FileCategory.PDF) {
                        file.copy(
                            thumbnailPath = PdfThumbnailGenerator.generate(
                                context = appContext,
                                filePath = file.absolutePath
                            )?.absolutePath
                        )
                    } else {
                        file
                    }
                }
                // 重命名/删除会取消正在进行的扫描。这里再次检查取消状态，防止旧扫描结果
                // 在文件操作完成后写回缓存，从而把已经删除或改名的旧路径重新显示出来。
                currentCoroutineContext().ensureActive()
                hasCompletedDeviceScan = true
                val filesWithBrowsingHistory = mergeCachedBrowsingHistory(filesWithThumbnails)
                // ScanModel 只缓存真实扫描数据；示例 PDF 由列表过滤后再插入，不写入 MMKV。
                LocalCacheStore.cachedScannedFiles = filesWithBrowsingHistory
                mutableScanState.postValue(ScanState(files = filesWithBrowsingHistory))
            } catch (cancellation: CancellationException) {
                // ViewModel 清理或任务被替换时属于正常取消，不向页面展示错误状态。
                throw cancellation
            } catch (throwable: Throwable) {
                mutableScanState.postValue(
                    ScanState(
                        files = emptyList(),
                        errorMessage = throwable.message ?: "scan_failed"
                    )
                )
            }
        }
    }

    // PDF14-MIGRATION: 工具选择页仅使用源扫描路径；首页专属重命名、删除及插入接口不迁入。

    /**
     * 按文件绝对路径把 MMKV 中记录的最后浏览信息合并到新列表。
     *
     * 扫描、新建或文件操作生成的 [ScannedFile] 可能仍使用默认页码；如果直接写回缓存，
     * 会覆盖已保存的浏览页码和打开时间。缓存中没有对应路径时保留传入对象。
     */
    private fun mergeCachedBrowsingHistory(files: List<ScannedFile>): List<ScannedFile> {
        val cachedFilesByPath = LocalCacheStore.cachedScannedFiles.associateBy {
            it.absolutePath
        }
        return files.map { file ->
            val cachedFile = cachedFilesByPath[file.absolutePath] ?: return@map file
            val cachedPage = cachedFile.lastOpenedPage.coerceAtLeast(1)
            val cachedOpenedAtMillis = cachedFile.lastOpenedAtMillis.coerceAtLeast(0L)
            if (
                file.lastOpenedPage == cachedPage &&
                file.lastOpenedAtMillis == cachedOpenedAtMillis
            ) {
                file
            } else {
                file.copy(
                    lastOpenedPage = cachedPage,
                    lastOpenedAtMillis = cachedOpenedAtMillis
                )
            }
        }
    }
}
