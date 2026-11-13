// PDF14-MIGRATION: 从 pdf14 复制；仅包名及已标记宿主接口/资源适配，未优化转换算法。
package com.pdf.editor.ui.tools.document.common

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFCloneUtility
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** 离线提取保留页到一个新 PDF，保留文字和图片内容；源文件始终只读，不做栅格化转换。 */
object PdfSplitConverter {
    class Protected : IllegalArgumentException("Encrypted PDF cannot be split")
    class Unavailable : IllegalArgumentException("PDF file is unavailable")

    /** 页码采用从 0 开始的源页下标；拒绝空、重复、越界或乱序请求，不允许覆盖原件。 */
    suspend fun convert(source: File, destination: File, pageIndices: List<Int>,
        onProgress: suspend (Int, Int) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        // != 比较规范路径，防止输出指向源文件；此检查在清理逻辑外，避免误删原件。
        require(source.canonicalPath != destination.canonicalPath)
        try {
            // ! 表示取反，|| 表示任一条件不满足就报文件不可用。
            if (!source.isFile || !source.canRead()) throw Unavailable()
            val input = try {
                PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly())
            } catch (_: InvalidPasswordException) { throw Protected() }
            // use 无论成功或异常都关闭文档；源文档保持打开到目标保存结束，保证页面资源可读。
            input.use { document ->
                if (document.isEncrypted) throw Protected()
                // distinct 去重、sorted 升序；== 要求原列表已按原页顺序且没有重复。
                require(pageIndices.isNotEmpty() && pageIndices == pageIndices.distinct().sorted())
                // all 要求每个下标都合法；until 创建左闭右开区间，最后有效页为总页数减一。
                require(pageIndices.all { it in 0 until document.numberOfPages })
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { output ->
                    val cloner = PDFCloneUtility(output)
                    onProgress(0, pageIndices.size)
                    // forEachIndexed 同时提供输出位置和原始页下标；+ 1 将位置转为已处理数量。
                    pageIndices.forEachIndexed { completed, sourceIndex ->
                        currentCoroutineContext().ensureActive()
                        val page = document.getPage(sourceIndex)
                        val imported = output.importPage(page)
                        // importPage 不自动复制继承的资源；?.let 仅在资源存在时克隆，避免字体或图片丢失。
                        page.resources?.let { resources ->
                            imported.resources = PDResources(cloner.cloneForNewDocument(resources.cosObject) as COSDictionary)
                        }
                        onProgress(completed + 1, pageIndices.size)
                    }
                    currentCoroutineContext().ensureActive()
                    output.save(destination)
                    currentCoroutineContext().ensureActive()
                }
            }
        } catch (error: Throwable) {
            // 转换失败或协程取消均删除此次输出；调用方传入的是新预留文件。
            destination.delete()
            throw error
        }
    }
}
