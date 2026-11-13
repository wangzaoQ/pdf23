// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.file

import com.pdf.editor.ui.tools.document.file.FileCategory
import com.pdf.editor.ui.tools.document.file.ScannedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File

/**
 * 从设备共享存储中扫描首页支持的文档。
 *
 * 该实现由 `docflow` 的 Scan1 精简而来：保留非递归目录遍历、后台线程执行和协程取消，
 * 去掉进度回调和广告等待时间。PPT/TXT 虽然没有独立标签，但仍会进入扫描结果，供 All
 * 分类展示。
 */
class FileScanner {

    /**
     * 扫描 [rootDirectory] 下的全部受支持文件，并按最后修改时间从新到旧返回。
     *
     * 使用显式栈代替递归调用，避免目录层级很深时发生栈溢出。循环中定期检查协程取消，
     * 当页面 ViewModel 发起新的扫描或被销毁时，旧任务可以尽快停止。
     */
    suspend fun scan(rootDirectory: File): List<ScannedFile> = withContext(Dispatchers.IO) {
        if (!rootDirectory.exists() || !rootDirectory.isDirectory) {
            return@withContext emptyList()
        }

        val pendingDirectories = ArrayDeque<File>().apply { add(rootDirectory) }
        val visitedDirectories = hashSetOf<String>()
        val result = mutableListOf<ScannedFile>()
        var inspectedEntryCount = 0

        while (pendingDirectories.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val directory = pendingDirectories.removeLast()

            // 某些设备会通过符号链接或挂载点重复暴露同一目录，记录规范路径可避免重复扫描。
            val canonicalPath = runCatching { directory.canonicalPath }
                .getOrDefault(directory.absolutePath)
            if (!visitedDirectories.add(canonicalPath)) continue

            val children = runCatching { directory.listFiles() }.getOrNull() ?: continue
            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (child.isDirectory) {
                    // Android/data 与 Android/obb 在分区存储下通常不可读，也不属于用户文档区。
                    if (!child.name.equals("Android", ignoreCase = true)) {
                        pendingDirectories.add(child)
                    }
                } else if (isSupportedFile(child)) {
                    ScannedFile.fromFile(child)?.let(result::add)
                }

                inspectedEntryCount++
                if (inspectedEntryCount % YIELD_INTERVAL == 0) {
                    // 主动让出一次执行权，保证大型存储扫描期间取消请求能够及时生效。
                    yield()
                }
            }
        }

        result.sortedByDescending(ScannedFile::modifiedAtMillis)
    }

    private fun isSupportedFile(file: File): Boolean {
        // 示例 PDF 会由列表层固定插入首位，扫描阶段按文件名排除，防止设备中同名文件重复出现。
        if (file.name.equals(ScannedFile.SAMPLE_FILE_NAME, ignoreCase = true)) return false
        val extension = file.name.substringAfterLast('.', "").lowercase()

        // 来源项目会排除名称中带 log 的日志文件。本项目将该规则限定在 TXT，避免误伤
        // catalog.pdf、login.docx 等正常文档；忽略大小写可同时覆盖 log、LOG、Log 等命名。
        if (extension == "txt" && file.nameWithoutExtension.contains("log", ignoreCase = true)) {
            return false
        }
        return extension in FileCategory.supportedExtensions
    }

    private companion object {
        const val YIELD_INTERVAL = 100
    }
}
