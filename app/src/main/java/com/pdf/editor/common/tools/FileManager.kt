package com.pdf.editor.common.tools

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.R
import com.pdf.editor.MyAPP
import com.pdf.editor.data.FilesData
import com.scwang.smart.refresh.layout.util.SmartUtil
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileManager {
    fun getFileCreationTime(file: File): Long? {
        return runCatching { file.lastModified() }.getOrNull()
    }

    fun getFileType(name: String): Int {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> ScanConfig.FILE_TYPE_PDF
            "doc", "docx" -> ScanConfig.FILE_TYPE_DOC
            "ppt", "pptx" -> ScanConfig.FILE_TYPE_PPT
            "xls", "xlsx" -> ScanConfig.FILE_TYPE_EXCEL
            "txt" -> ScanConfig.FILE_TYPE_TEXT
            else -> ScanConfig.FILE_TYPE_OTHER
        }
    }

    fun getFileId(file: File): String {
        return runCatching {
            val digest = MessageDigest.getInstance("MD5").digest(file.absolutePath.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        }.getOrDefault(file.absolutePath)
    }

    /**
     * 真正执行底层文件改名，并基于新路径重新构造一份 FilesData。
     *
     * 这里不能只改内存里的 fileName，因为项目里的 fileId 是由 absolutePath 计算出来的，
     * 文件一旦改名，filePath / fileId / fileType / fileSize 等派生信息理论上都可能变化。
     * 所以改名成功后直接重新 createDefault，避免留下半新半旧的数据。
     */
    fun renameFile(data: FilesData, newFileName: String): FilesData? {
        val sourceFile = File(data.filePath)
        if (!sourceFile.exists()) return null

        val targetName = buildRenamedFileName(sourceFile.name, newFileName.trim())
        if (targetName.isEmpty()) return null
        // 用户没有实际改名时直接复用原对象，避免无意义的文件操作和缓存迁移。
        if (targetName == sourceFile.name) return data

        val targetFile = File(sourceFile.parentFile, targetName)
        if (targetFile.exists()) return null
        if (!sourceFile.renameTo(targetFile)) return null

        return FilesData.createDefault(targetFile)?.apply {
            // 这些字段属于“使用态数据”，不是文件扫描天然能还原出来的，改名后继续沿用旧值。
            fileOpenTime = data.fileOpenTime
            fileImg = data.fileImg
            allPage = data.allPage
            currentPage = data.currentPage
        }
    }

    fun getFileNameWithoutExtension(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        if (dotIndex <= 0) return fileName
        return fileName.substring(0, dotIndex)
    }

    private fun buildRenamedFileName(oldFileName: String, inputName: String): String {
        if (inputName.isEmpty()) return ""
        val dotIndex = oldFileName.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex == oldFileName.lastIndex) {
            return inputName
        }
        val extension = oldFileName.substring(dotIndex)
        return inputName + extension
    }

    fun getFileOpenTime(fileId: String): Long {
        return LocalDataManager.getOpenTime(fileId)
    }

    fun ensurePdfThumbnail(context: Context, data: FilesData): FilesData {
        if (data.fileType != ScanConfig.FILE_TYPE_PDF) return data
        if (data.fileImg.isNotEmpty() && File(data.fileImg).exists()) return data
        data.fileImg = generatePdfThumbnailFileSync(
            context.applicationContext,
            data.filePath
        )?.absolutePath ?: ""
        return data
    }

    fun getFileImg(fileType: Int): Int {
        return when (fileType) {
            ScanConfig.FILE_TYPE_PDF -> R.drawable.ic_pdf
            ScanConfig.FILE_TYPE_DOC -> R.drawable.ic_word
            ScanConfig.FILE_TYPE_PPT -> R.drawable.ic_ppt
            ScanConfig.FILE_TYPE_EXCEL -> R.drawable.ic_excel
            ScanConfig.FILE_TYPE_TEXT -> R.drawable.ic_txt
            else -> R.mipmap.ic_nf_logo
        }
    }

    fun getFileBg(fileType: Int): Int {
        return when (fileType) {
            ScanConfig.FILE_TYPE_PDF -> R.drawable.bg_item_pdf
            ScanConfig.FILE_TYPE_DOC -> R.drawable.bg_item_doc
            ScanConfig.FILE_TYPE_PPT -> R.drawable.bg_item_ppt
            ScanConfig.FILE_TYPE_EXCEL -> R.drawable.bg_item_excel
            ScanConfig.FILE_TYPE_TEXT -> R.drawable.bg_item_txt
            else -> R.drawable.shape_white_14r
        }
    }

    fun getFileSize(size: Long): String {
        if (size <= 0L) return "0 B"
        val kb = size / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$size B"
        }
    }

    fun getFileTime(time: Long): String {
        if (time <= 0L) return ""
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))
        }.getOrDefault("")
    }

    const val ASSET_SAMPLE_PDF = "cache/sample_en.pdf"

    fun resolveRecentPdfData(context: Context): FilesData? {
        val recentFileData = LocalDataManager.recentFileData
        if (recentFileData?.filePath?.let { File(it).exists() } == true &&
            recentFileData.fileName.endsWith(".pdf", ignoreCase = true)
        ) {
            return recentFileData
        }
        var defaultFileData = FilesData.createFromAsset(context, ASSET_SAMPLE_PDF)
        defaultFileData?.allPage = 3
        return defaultFileData
    }

    fun share(item: FilesData) {
        systemShareFile(MyAPP.instance, File(item.filePath))
    }

    fun systemShareFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )

        val mime = when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "txt" -> "text/plain"
            else -> "*/*"
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "share").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }


    fun generatePdfThumbnailFileSync(
        context: Context,
        filePath: String,
        width: Int = SmartUtil.dp2px(40f),
        height: Int = SmartUtil.dp2px(40f),
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG
    ): File? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val thumbKey = "${file.absolutePath}|${file.length()}|${file.lastModified()}|$width|$height|${format.name}"
            val outputFile = File(context.cacheDir, "pdf_thumb_${thumbKey.md5()}.png")
            if (outputFile.exists() && outputFile.length() > 0L) {
                return outputFile
            }

            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor.use {

                val renderer = PdfRenderer(descriptor)
                if (renderer.pageCount <= 0) {
                    renderer.close()
                    return null
                }

                val page = renderer.openPage(0)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                FileOutputStream(outputFile).use { fos ->
                    bitmap.compress(format, 90, fos)
                }

                page.close()
                renderer.close()

                outputFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun String.md5(): String {
        val digest = MessageDigest.getInstance("MD5").digest(toByteArray())
        return buildString(digest.size * 2) {
            digest.forEach { append("%02x".format(it)) }
        }
    }
}
