// PDF14-MIGRATION: 从 pdf14 复制；仅包名及已标记宿主接口/资源适配，未优化转换算法。
package com.pdf.editor.ui.tools.document.common

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PointF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.contentstream.PDFGraphicsStreamEngine
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImage
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 离线 PDF 转 DOCX：纯文字页输出可编辑段落，图文页在文字后追加整页截图，
 * 扫描页只保留截图，空白页保留分页。不进行 OCR，也不重建 Word 表格或原始字体。
 * 文本提取复用 PDFBox，截图复用系统 PdfRenderer，整条链路不新增网络服务或依赖。
 */
object PdfToWordConverter {
    /** 输入不可用、加密或禁止提取时给上层可区分的失败原因，避免只显示泛化的 PDF 创建失败。 */
    class Unavailable : IllegalArgumentException("PDF file is unavailable")
    class Protected : IllegalArgumentException("PDF is encrypted or does not allow text extraction")

    /**
     * 在 IO 线程逐页转换；进度只在该页文字和截图已写入后推进。
     * 全部页处理完后仍需关闭 ZIP，调用方只有收到返回结果才可报告成功。
     */
    suspend fun convert(source: File, destination: File, onProgress: suspend (Int, Int) -> Unit) =
        withContext(Dispatchers.IO) {
            try {
                if (!source.isFile || !source.canRead()) throw Unavailable()
                PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly()).use { document ->
                    if (document.isEncrypted || !document.currentAccessPermission.canExtractContent()) throw Protected()
                    check(document.numberOfPages > 0) { "PDF has no pages" }
                    val stripper = PDFTextStripper().apply {
                        sortByPosition = true
                        lineSeparator = "\n"
                        pageStart = ""
                        pageEnd = ""
                    }
                    ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            check(renderer.pageCount == document.numberOfPages) { "Inconsistent PDF page count" }
                            DocxDocumentWriter(destination).use { writer ->
                                onProgress(0, document.numberOfPages)
                                for (index in 0 until document.numberOfPages) {
                                    currentCoroutineContext().ensureActive()
                                    stripper.startPage = index + 1
                                    stripper.endPage = index + 1
                                    val text = stripper.getText(document).replace("\r\n", "\n").replace('\r', '\n').trimEnd()
                                    val page = document.getPage(index)
                                    val graphics = PageGraphics(page).apply { processPage(page) }
                                    // 无可提取文字的页面也截图后判断空白，保留轮廓文字和扫描内容。
                                    val needsRaster = text.isBlank() || graphics.hasGraphics || page.annotations.isNotEmpty()
                                    val bitmap = if (needsRaster) renderPage(renderer, index) else null
                                    try {
                                        currentCoroutineContext().ensureActive()
                                        val visibleBitmap = bitmap?.takeUnless { text.isBlank() && isWhite(it) }
                                        writer.addPage(text, visibleBitmap)
                                    } finally { bitmap?.recycle() }
                                    onProgress(index + 1, document.numberOfPages)
                                }
                                currentCoroutineContext().ensureActive()
                                writer.finish()
                            }
                        }
                    }
                }
            } catch (error: Throwable) {
                // 只清理本次预留的输出，源 PDF 始终只读；取消也走相同资源释放路径。
                destination.delete()
                if (error is com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) throw Protected()
                throw error
            }
        }

    /** 长边限制为 1600 像素并保持宽高比；每次仅持有一页 Bitmap，横向和旋转页同样处理。 */
    private fun renderPage(renderer: PdfRenderer, index: Int): Bitmap = renderer.openPage(index).use { page ->
        val scale = 1600f / max(page.width, page.height).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap((page.width * scale).roundToInt().coerceAtLeast(1),
            (page.height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }

    /** 逐行检查白底页面，避免稀疏采样漏掉小文字；只用于没有提取出文字的页。 */
    private fun isWhite(bitmap: Bitmap): Boolean {
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            if (row.any { Color.red(it) < 250 || Color.green(it) < 250 || Color.blue(it) < 250 }) return false
        }
        return true
    }

    /**
     * 用 PDFBox 的内容流引擎检查实际绘制指令，自动遍历 Form 和内嵌图片。
     * 采用保守策略：图片、路径填充/描边、渐变都视为图形，文字页附带线框时也追加截图。
     * 路径坐标仅为引擎的曲线指令提供当前点；本类不承担真实渲染。
     */
    private class PageGraphics(page: PDPage) : PDFGraphicsStreamEngine(page) {
        var hasGraphics = false
        private var point = PointF()
        override fun drawImage(image: PDImage) { hasGraphics = true }
        override fun strokePath() { hasGraphics = true }
        override fun fillPath(rule: Path.FillType) { hasGraphics = true }
        override fun fillAndStrokePath(rule: Path.FillType) { hasGraphics = true }
        override fun shadingFill(name: COSName) { hasGraphics = true }
        override fun appendRectangle(p0: PointF, p1: PointF, p2: PointF, p3: PointF) { point = p0 }
        override fun moveTo(x: Float, y: Float) { point = PointF(x, y) }
        override fun lineTo(x: Float, y: Float) { point = PointF(x, y) }
        override fun curveTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) { point = PointF(x3, y3) }
        override fun getCurrentPoint() = point
        override fun clip(rule: Path.FillType) = Unit
        override fun closePath() = Unit
        override fun endPath() = Unit
    }
}
