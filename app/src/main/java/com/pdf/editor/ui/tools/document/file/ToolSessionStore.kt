package com.pdf.editor.ui.tools.document.file

import com.pdf.editor.MyAPP
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.tools.ScanConfig
import com.pdf.editor.data.FilesData
import java.io.File

/** PDF14-MIGRATION: 工具选择缓存接入当前文件模型，不迁入源首页的另一套持久化缓存。 */
object ToolSessionStore {
    private var scannedCache = emptyList<ScannedFile>()

    var cachedScannedFiles: List<ScannedFile>
        get() = if (scannedCache.isNotEmpty()) scannedCache else ScanConfig.allFileList.mapNotNull { data ->
            ScannedFile.fromFile(File(data.filePath))?.copy(
                thumbnailPath = data.fileImg.takeIf(String::isNotBlank),
                lastOpenedPage = LocalDataManager.getFilePage(data.fileId),
                lastOpenedAtMillis = LocalDataManager.getOpenTime(data.fileId)
            )
        }
        set(value) { scannedCache = value }

    fun addCreatedFile(file: ScannedFile) {
        val existingFiles = cachedScannedFiles
        val data = FilesData.createDefault(File(file.absolutePath)).apply {
            fileImg = file.thumbnailPath.orEmpty()
        }
        ScanConfig.allFileList.removeAll { it.filePath == data.filePath }
        ScanConfig.allFileList.add(0, data)
        scannedCache = listOf(file) + existingFiles.filterNot { it.absolutePath == file.absolutePath }
        MyAPP.resetLiveData.postValue("resetAll")
    }
}
