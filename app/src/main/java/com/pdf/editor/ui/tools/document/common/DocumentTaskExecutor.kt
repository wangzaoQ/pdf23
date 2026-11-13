// PDF14-MIGRATION: 从 pdf14 复制；仅包名及已标记宿主接口/资源适配，未优化转换算法。
package com.pdf.editor.ui.tools.document.common

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.media.MediaScannerConnection
import java.io.File
import com.pdf.editor.MyAPP
import com.pdf.editor.data.FilesData
import com.pdf.editor.common.tools.FileManager
import com.pdf.editor.common.tools.ScanConfig
import com.pdf.editor.ui.tools.document.file.ScannedFile
import com.pdf.editor.ui.tools.document.file.ToolSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 统一任务分发层，集中调用已有处理器并完成缩略图和文件列表通知。
 * 由 ViewModel 传入 Application Context，避免持有已销毁的 Activity。
 * 后续增加任务时扩展 when 分支，页面不承担格式转换逻辑。
 */
class DocumentTaskExecutor(private val context: Context) {
    /** 在 IO 线程执行现有同步创建流程；异常交给 ViewModel 转为失败状态。 */
    suspend fun execute(request: DocumentTaskRequest, onProgress: suspend (Int, Int) -> Unit = { _, _ -> }): DocumentTaskResult = withContext(Dispatchers.IO) {
        // when 按请求类型选择分支；is 判断对象类型，-> 右侧是该分支结果；listOf 将单个输出包装成列表。
        val files = when (request) {
            // is 按请求类型分发；listOf 包装唯一输出，继续复用现有成功页。
            is DocumentTaskRequest.SplitPdf -> listOf(splitPdf(request, onProgress))
            is DocumentTaskRequest.MergePdfs -> listOf(mergePdfs(request, onProgress))
            is DocumentTaskRequest.ImagesToPdf -> listOf(
                // map 将每个 URI 字符串转换为 Uri；Uri::parse 是方法引用，等价于 { Uri.parse(it) }。
                PdfCreator.create(context, request.imageUris.map(Uri::parse), request.fileName)
            )
            is DocumentTaskRequest.WordToPdf -> listOf(createWordPdf(request))
            is DocumentTaskRequest.PdfToWord -> listOf(createDocx(request, onProgress))
            // 相册由 MediaStore 管理，不能走 ScannedFile 的 PDF/Word 列表通知和缩略图流程。
            // return@withContext 直接返回当前 IO 代码块的结果，跳过后面的普通文档收尾逻辑。
            is DocumentTaskRequest.PdfToImages -> return@withContext DocumentTaskResult(images =
                PdfToImagesConverter.convert(context, File(request.sourcePath), request.albumName, onProgress))
        }
        files.forEach { file ->
            // PDF14-MIGRATION: 仅接入当前文件数据、缩略图和刷新事件；保留读取→缩略图→通知的源收尾顺序。
            val data = FilesData.createDefault(file)
            // DOCX 不经过 PDF 缩略图解析器，缩略图失败不影响已经完成的文件任务。
            data.fileImg = if (file.extension.equals("pdf", true)) runCatching {
                FileManager.generatePdfThumbnailFileSync(context, file.absolutePath)?.absolutePath
            }.getOrNull().orEmpty() else ""
            // PDF14-MIGRATION: 源 fileCreatedEvent 的文件缓存通知映射为当前 ScanConfig 和 resetLiveData。
            withContext(Dispatchers.Main) {
                ScanConfig.allFileList.removeAll { it.filePath == data.filePath }
                ScanConfig.allFileList.add(0, data)
                // PDF14-MIGRATION: 同步迁入工具选择页的文件快照，保留源 fileCreatedEvent 的缓存可见性。
                ScannedFile.fromFile(file)?.copy(thumbnailPath = data.fileImg.takeIf(String::isNotBlank))
                    ?.let(ToolSessionStore::addCreatedFile)
                MyAPP.resetLiveData.value = "resetAll"
            }
        }
        DocumentTaskResult(files)
    }

    /** 拆分独立执行，沿用文件名去重和系统索引；失败删除预留文件，源文档始终保留。 */
    private suspend fun splitPdf(request: DocumentTaskRequest.SplitPdf, onProgress: suspend (Int, Int) -> Unit): File {
        val destination = reserveOutput(request.fileName, "pdf")
        try {
            PdfSplitConverter.convert(File(request.sourcePath), destination, request.pageIndices, onProgress)
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
        MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf("application/pdf"), null)
        return destination
    }

    /** 合并沿用不重名输出和系统索引；异常删除预留文件，防止失败后出现空 PDF。 */
    private suspend fun mergePdfs(request: DocumentTaskRequest.MergePdfs, onProgress: suspend (Int, Int) -> Unit): File {
        val destination = reserveOutput(request.fileName, "pdf")
        try {
            // map 按顺序将路径转成 File；::File 是构造函数引用，等价于 { path -> File(path) }。
            PdfMergeConverter.convert(request.sourcePaths.map(::File), destination, onProgress)
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
        MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf("application/pdf"), null)
        return destination
    }

    /**
     * 为 Word 输出原子预留不重名文件，优先 Downloads，无法写入时使用应用文档目录。
     * 仅目录不可写时切换位置；转换异常直接上抛，避免同一坏文档被重复渲染。
     */
    private suspend fun createWordPdf(request: DocumentTaskRequest.WordToPdf): File {
        val destination = reserveOutput(request.fileName, "pdf")
        try {
            WordPdfConverter.convert(context, File(request.sourcePath), destination)
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
        MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath), arrayOf("application/pdf"), null)
        return destination
    }

    /** DOCX 生成成功后通知系统文件索引；失败和取消均删除此次预留文件，原 PDF 不修改。 */
    private suspend fun createDocx(request: DocumentTaskRequest.PdfToWord, onProgress: suspend (Int, Int) -> Unit): File {
        val destination = reserveOutput(request.fileName, "docx")
        try {
            PdfToWordConverter.convert(File(request.sourcePath), destination, onProgress)
        } catch (error: Throwable) { destination.delete(); throw error }
        MediaScannerConnection.scanFile(context, arrayOf(destination.absolutePath),
            arrayOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document"), null)
        return destination
    }

    /** 文档转换和合并共用不重名输出分配，扩展名由任务指定，不接受用户指定的路径。 */
    private fun reserveOutput(requestedName: String, extension: String): File {
        val trimmed = requestedName.trim()
        // if 表达式决定是否去掉已有扩展名；dropLast 的 + 1 包含扩展名前的点。
        // replace 替换非法字符，ifBlank 为空白名称提供默认值，take(100) 最多保留 100 个字符。
        val name = (if (trimmed.endsWith(".$extension", true)) trimmed.dropLast(extension.length + 1) else trimmed)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "Document" }.take(100)
        // listOfNotNull 创建候选目录列表，同时过滤系统可能返回的 null 目录。
        val directories = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            File(context.filesDir, "generated_pdfs")
        )
        // firstNotNullOfOrNull 按顺序尝试，遇到首个非 null 输出即停止；全部失败时返回 null。
        // runCatching 配合 getOrNull 将某个目录中的创建失败转为 null，继续尝试下一目录。
        val destination = directories.firstNotNullOfOrNull { directory ->
            runCatching {
                // || 是短路或：目录已存在时不再调用 mkdirs，否则要求创建目录成功。
                check(directory.isDirectory || directory.mkdirs())
                var index = 0
                var file: File
                do {
                    // == 0 时不添加编号；$name 和 $index 是字符串模板，++ 每次将编号增加 1。
                    file = File(directory, if (index == 0) "$name.$extension" else "$name($index).$extension")
                    index++
                // ! 将创建结果取反：重名导致返回 false 时继续尝试下一个编号。
                } while (!file.createNewFile())
                file
            }.getOrNull()
        // ?: 只在所有目录均未得到输出文件时执行 error，向上抛出失败。
        } ?: error("No writable output directory")
        return destination
    }
}
