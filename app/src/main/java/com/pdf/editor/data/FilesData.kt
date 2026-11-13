package com.pdf.editor.data

import android.content.Context
import com.pdf.editor.common.tools.FileManager
import java.io.File

class FilesData {

    var fileName = ""
    var filePath = ""
    var fileSize = 0L
    var fileTime = 0L
    var fileType = 0
    var fileId = ""
    var fileOpenTime = 0L
    var fileImg = ""
    var allPage = 0
    var currentPage = 0

    companion object {
        private const val DEMO_FILE_ID = "asset_demo_sample_pdf"

        fun createDefault(file: File): FilesData {
            val fileName = file.name
            val filePath = file.absolutePath
            val fileSize = file.length()
            return FilesData().apply {
                this.fileName = fileName
                this.filePath = filePath
                this.fileSize = fileSize
                this.fileTime = FileManager.getFileCreationTime(file) ?: 0L
                this.fileType = FileManager.getFileType(file.name)
                this.fileId = FileManager.getFileId(file)
                this.fileOpenTime = FileManager.getFileOpenTime(this.fileId)
            }
        }

        fun createDemoSample(context: Context): FilesData? {
            return createFromAsset(context, FileManager.ASSET_SAMPLE_PDF)?.apply {
                fileId = DEMO_FILE_ID
                fileOpenTime = FileManager.getFileOpenTime(fileId)
            }
        }

        fun createFromAsset(context: Context, assetPath: String): FilesData? {
            return runCatching {
                val assetName = assetPath.substringAfterLast('/')
                val targetDir = File(context.cacheDir, "asset_samples")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val targetFile = File(targetDir, assetName)
                if (!targetFile.exists() || targetFile.length() == 0L) {
                    context.assets.open(assetPath).use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                createDefault(targetFile).apply {
                    FileManager.ensurePdfThumbnail(context, this)
                }
            }.getOrNull()
        }

        fun isDemoSample(data: FilesData): Boolean {
            return data.fileId == DEMO_FILE_ID
        }
    }
}
