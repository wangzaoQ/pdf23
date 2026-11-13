// PDF14-MIGRATION: 从 pdf14 复制；仅包名及已标记宿主接口/资源适配，未优化转换算法。
package com.pdf.editor.ui.tools.document.common

import android.graphics.Bitmap
import android.util.Xml
import org.xmlpull.v1.XmlSerializer
import java.io.Closeable
import java.io.File
import java.io.StringWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * 基础 DOCX 写入器，使用标准 ZIP 和 XML API，自动转义文本而非手拼文档 XML。
 * 正文先流式写入临时文件，截图直接压缩到 ZIP；内存仅保留少量图片关系和页计数。
 * DOCX 固定 A4 和一英寸边距，保留源页顺序，不承诺与 PDF 的最终页数一致。
 */
internal class DocxDocumentWriter(private val destination: File) : Closeable {
    private val bodyFile = File.createTempFile("docx-body-", ".xml", destination.parentFile)
    private val bodyOutput = try { bodyFile.outputStream() } catch (error: Throwable) {
        bodyFile.delete()
        throw error
    }
    private val xml = Xml.newSerializer().apply { setOutput(bodyOutput, "UTF-8") }
    private val zip = try { ZipOutputStream(destination.outputStream()) } catch (error: Throwable) {
        // 初始化也可能因磁盘不足失败，此时 use 尚未接管资源，必须在这里释放正文临时文件。
        bodyOutput.close()
        bodyFile.delete()
        throw error
    }
    private val images = mutableListOf<String>()
    private var pages = 0

    init {
        try {
            xml.startDocument("UTF-8", true)
            xml.setPrefix("w", W); xml.setPrefix("r", R); xml.setPrefix("wp", WP)
            xml.setPrefix("a", A); xml.setPrefix("pic", PIC)
            xml.startTag(W, "document").startTag(W, "body")
        } catch (error: Throwable) { close(); throw error }
    }

    /** 每次调用对应一个真实 PDF 页，不用空行拆分页面，避免参考库中的页码错位。 */
    fun addPage(text: String, image: Bitmap?) {
        if (pages > 0) xml.element(W, "p") { element(W, "r") { element(W, "br") { attribute(W, "type", "page") } } }
        pages++
        if (text.isBlank()) {
            if (image == null) xml.element(W, "p") {}
        } else text.split('\n').forEach { line ->
            xml.element(W, "p") {
                // PDF 每个提取行对应一个段落，不附加默认段后间距，减少无意义的额外分页。
                element(W, "pPr") { element(W, "spacing") { attribute(W, "after", "0") } }
                element(W, "r") { element(W, "t") {
                    attribute("http://www.w3.org/XML/1998/namespace", "space", "preserve")
                    text(xmlText(line))
                } }
            }
        }
        if (image != null) appendImage(image)
    }

    /** 截图按可用纸张空间等比缩放，图文页在可编辑文字之后插入，超出时由 Word 自然分页。 */
    private fun appendImage(bitmap: Bitmap) {
        val id = "image${images.size + 1}"
        images.add(id)
        zip.putNextEntry(ZipEntry("word/media/$id.png"))
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)) { "Unable to encode page screenshot" }
        zip.closeEntry()
        val scale = min(5_730_000.0 / bitmap.width, 7_850_000.0 / bitmap.height)
        val cx = (bitmap.width * scale).roundToLong().toString()
        val cy = (bitmap.height * scale).roundToLong().toString()
        xml.element(W, "p") { element(W, "r") { element(W, "drawing") {
            element(WP, "inline") {
                element(WP, "extent") { attribute(null, "cx", cx); attribute(null, "cy", cy) }
                element(WP, "docPr") { attribute(null, "id", images.size.toString()); attribute(null, "name", "PDF page $pages") }
                element(A, "graphic") { element(A, "graphicData") {
                    attribute(null, "uri", PIC)
                    element(PIC, "pic") {
                        element(PIC, "nvPicPr") {
                            element(PIC, "cNvPr") { attribute(null, "id", images.size.toString()); attribute(null, "name", id) }
                            element(PIC, "cNvPicPr") {}
                        }
                        element(PIC, "blipFill") {
                            element(A, "blip") { attribute(R, "embed", id) }
                            element(A, "stretch") { element(A, "fillRect") {} }
                        }
                        element(PIC, "spPr") {
                            element(A, "xfrm") {
                                element(A, "off") { attribute(null, "x", "0"); attribute(null, "y", "0") }
                                element(A, "ext") { attribute(null, "cx", cx); attribute(null, "cy", cy) }
                            }
                            element(A, "prstGeom") { attribute(null, "prst", "rect"); element(A, "avLst") {} }
                        }
                    }
                } }
            }
        } } }
    }

    /** 结束正文并补全包类型与关系文件；finish 返回前 ZIP 中央目录已写入。 */
    fun finish() {
        xml.element(W, "sectPr") {
            element(W, "pgSz") { attribute(W, "w", "11906"); attribute(W, "h", "16838") }
            element(W, "pgMar") {
                listOf("top", "right", "bottom", "left").forEach { attribute(W, it, "1440") }
            }
        }
        xml.endTag(W, "body").endTag(W, "document").endDocument()
        bodyOutput.close()
        zip.putNextEntry(ZipEntry("word/document.xml"))
        bodyFile.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
        writeXml("[Content_Types].xml", "http://schemas.openxmlformats.org/package/2006/content-types", "Types") {
            listOf("rels" to "application/vnd.openxmlformats-package.relationships+xml", "xml" to "application/xml", "png" to "image/png").forEach { (extension, type) ->
                element(namespace, "Default") { attribute(null, "Extension", extension); attribute(null, "ContentType", type) }
            }
            element(namespace, "Override") {
                attribute(null, "PartName", "/word/document.xml")
                attribute(null, "ContentType", "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml")
            }
        }
        writeXml("_rels/.rels", REL, "Relationships") {
            relationship("document", "$R/officeDocument", "word/document.xml")
        }
        writeXml("word/_rels/document.xml.rels", REL, "Relationships") {
            images.forEach { relationship(it, "$R/image", "media/$it.png") }
        }
        zip.finish()
    }

    /** 小型元数据通过 XmlSerializer 生成，正文和图片不进入这个内存缓冲。 */
    private fun writeXml(path: String, ns: String, root: String, content: XmlSerializer.() -> Unit) {
        val buffer = StringWriter()
        val serializer = Xml.newSerializer().apply { setOutput(buffer); startDocument("UTF-8", true); setPrefix("", ns) }
        serializer.element(ns, root, content); serializer.endDocument()
        zip.putNextEntry(ZipEntry(path)); zip.write(buffer.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }

    /** 清理正文临时文件；异常时外层转换器负责删除尚未完成的 DOCX。 */
    override fun close() {
        try { bodyOutput.close() } finally {
            try { zip.close() } finally { bodyFile.delete() }
        }
    }

    companion object {
        private const val W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
        private const val R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
        private const val WP = "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
        private const val A = "http://schemas.openxmlformats.org/drawingml/2006/main"
        private const val PIC = "http://schemas.openxmlformats.org/drawingml/2006/picture"
        private const val REL = "http://schemas.openxmlformats.org/package/2006/relationships"

        /** 删除 XML 1.0 不允许的控制码，保留中文和有效的补充平面字符。 */
        private fun xmlText(value: String): String = buildString {
            value.codePoints().forEach { cp ->
                if (cp == 9 || cp == 10 || cp == 13 || cp in 32..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF) append(String(Character.toChars(cp)))
            }
        }

        /** 统一配对 XML 标签，嵌套块中的文本交给序列化器转义。 */
        private fun XmlSerializer.element(ns: String, name: String, content: XmlSerializer.() -> Unit) {
            startTag(ns, name); content(); endTag(ns, name)
        }

        /** 关系 ID 与图片引用一致，确保 Word 和现有 DOCX 预览器均能找到截图。 */
        private fun XmlSerializer.relationship(id: String, type: String, target: String) = element(REL, "Relationship") {
            attribute(null, "Id", id); attribute(null, "Type", type); attribute(null, "Target", target)
        }
    }
}
