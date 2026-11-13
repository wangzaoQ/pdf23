// PDF14-MIGRATION: 从 pdf14 复制；仅包名及已标记宿主接口/资源适配，未优化转换算法。
package com.pdf.editor.ui.tools.document.common

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** 使用已有 PDFBox 离线拼接原始页面，不转成截图，保留文字、图片和矢量内容。 */
object PdfMergeConverter {
    class Protected : IllegalArgumentException("Encrypted PDF cannot be merged")
    class Unavailable : IllegalArgumentException("PDF file is unavailable")

    /**
     * 文件列表顺序即合并顺序；采用磁盘临时缓存，避免将所有源文件字节同时放入堆内存。
     * 源文档保留到目标保存完成，再统一关闭，确保复制的表单、资源等仍可被 PDFBox 读取。
     * 失败和取消删除此次输出，绝不修改源文件；调用方应传入新预留的输出路径。
     */
    suspend fun convert(sources: List<File>, destination: File, onProgress: suspend (Int, Int) -> Unit = { _, _ -> }) =
        // suspend (Int, Int) -> Unit 是接收完成数、总数的挂起回调；默认 { _, _ -> } 忽略两参数，不做处理。
        withContext(Dispatchers.IO) {
            // 路径别名也不能把同一文件重复加入，且禁止输出覆盖输入。
            // map 提取规范路径，distinct 去重；去重后数量 == 原数量才说明无重复。
            // >= 2 与无重复通过 && 同时校验；require 在条件为 false 时抛出参数异常。
            require(sources.size >= 2 && sources.map { it.canonicalPath }.distinct().size == sources.size)
            // none 表示没有任何源路径与输出路径相同；it 是当前源文件，== 比较规范路径字符串。
            require(sources.none { it.canonicalPath == destination.canonicalPath })
            val opened = mutableListOf<PDDocument>()
            try {
                // use 在代码块结束时自动关闭目标文档，包括抛异常的情况；output 是代码块参数。
                PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { output ->
                    val merger = PDFMergerUtility()
                    onProgress(0, sources.size)
                    // forEachIndexed 按原顺序遍历，同时提供从 0 开始的下标 index 和当前文件 file。
                    sources.forEachIndexed { index, file ->
                        currentCoroutineContext().ensureActive()
                        // ! 表示取反，|| 表示任一条件成立就失败：不是文件，或文件不可读。
                        if (!file.isFile || !file.canRead()) throw Unavailable()
                        val source = try {
                            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly())
                        // _ 表示不使用原密码异常对象，统一转换为页面可识别的 Protected 异常。
                        } catch (_: InvalidPasswordException) { throw Protected() }
                        opened.add(source)
                        if (source.isEncrypted) throw Protected()
                        // > 0 要求至少一页；check 条件失败时，用后面的 Lambda 文案构造异常。
                        check(source.numberOfPages > 0) { "PDF has no pages" }
                        merger.appendDocument(output, source)
                        // index + 1 将从 0 开始的下标转换成已处理文件数。
                        onProgress(index + 1, sources.size)
                    }
                    // completed == total 仅表示追加完成；保存仍在进行，界面此时显示保存提示。
                    currentCoroutineContext().ensureActive()
                    output.save(destination)
                    currentCoroutineContext().ensureActive()
                }
            } catch (error: Throwable) {
                destination.delete()
                throw error
            } finally {
                // forEach 逐个关闭源文档；runCatching 捕获关闭异常，保证某个关闭失败后仍继续关闭其余文件。
                opened.forEach { runCatching { it.close() } }
            }
        }
}
