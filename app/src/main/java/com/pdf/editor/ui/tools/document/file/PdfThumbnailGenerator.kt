// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * PDF 第一页缩略图生成器。
 *
 * 核心逻辑迁移自 `docflow` 的 `FileManager.generatePdfThumbnailFileSync`：通过系统
 * [PdfRenderer] 渲染第一页，并把结果写入应用缓存目录。调用方必须在 IO 线程执行。
 */
object PdfThumbnailGenerator {

    /**
     * 为 [filePath] 对应的 PDF 生成列表尺寸缩略图。
     *
     * 缓存键包含路径、文件大小、修改时间和目标尺寸。只要原 PDF 没有变化，后续扫描就会
     * 直接复用已有 PNG；PDF 被替换或修改后缓存键随之变化，会自动生成新的缩略图。
     */
    fun generate(
        context: Context,
        filePath: String,
        widthDp: Int = 40,
        heightDp: Int = 44
    ): File? {
        return runCatching {
            val sourceFile = File(filePath)
            if (!sourceFile.exists() || !sourceFile.isFile) return null

            val density = context.resources.displayMetrics.density
            val widthPx = (widthDp * density).toInt().coerceAtLeast(1)
            val heightPx = (heightDp * density).toInt().coerceAtLeast(1)
            val cacheKey = buildString {
                append(sourceFile.absolutePath)
                append('|').append(sourceFile.length())
                append('|').append(sourceFile.lastModified())
                append('|').append(widthPx)
                append('|').append(heightPx)
            }
            val outputFile = File(context.cacheDir, "pdf_thumb_${cacheKey.md5()}.png")
            if (outputFile.exists() && outputFile.length() > 0L) return outputFile

            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount <= 0) return null
                    renderer.openPage(0).use { page ->
                        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            FileOutputStream(outputFile).use { output ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 90, output)
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }

            outputFile.takeIf { it.exists() && it.length() > 0L }
        }.getOrNull()
    }

    private fun String.md5(): String {
        val digest = MessageDigest.getInstance("MD5").digest(toByteArray())
        return buildString(digest.size * 2) {
            digest.forEach { byte -> append("%02x".format(byte)) }
        }
    }
}
