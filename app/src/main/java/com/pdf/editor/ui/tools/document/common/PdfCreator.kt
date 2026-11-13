// PDF14-MIGRATION: 从 pdf14 复制；仅包名及已标记宿主接口/资源适配，未优化转换算法。
package com.pdf.editor.ui.tools.document.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import kotlin.math.max
import kotlin.math.min

object PdfCreator {
    private const val MAX_IMAGE_SIDE_PX = 2000

    fun create(context: Context, imageUris: List<Uri>, requestedName: String): File {
        require(imageUris.isNotEmpty()) { "At least one image is required" }
        val outputDirectories = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { File(it, "PDF14") },
            File(context.filesDir, "generated_pdfs")
        ).distinctBy(File::getAbsolutePath)

        var lastError: Throwable? = null
        outputDirectories.forEach { directory ->
            val result = runCatching {
                if (!directory.exists() && !directory.mkdirs()) {
                    error("Unable to create ${directory.absolutePath}")
                }
                val destination = uniqueDestination(directory, requestedName)
                createAt(context, imageUris, destination)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destination.absolutePath),
                    arrayOf("application/pdf"),
                    null
                )
                destination
            }
            result.getOrNull()?.let { return it }
            lastError = result.exceptionOrNull()
        }
        throw lastError ?: IllegalStateException("Unable to create PDF")
    }

    private fun createAt(context: Context, imageUris: List<Uri>, destination: File) {
        try {
            PDDocument().use { document ->
                imageUris.forEach { uri ->
                    val source = decodeBitmap(context, uri) ?: return@forEach
                    val rotated = rotateBitmap(source, readRotation(context, uri))
                    if (rotated !== source) source.recycle()
                    try {
                        val pageSize = if (rotated.width >= rotated.height) {
                            PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
                        } else {
                            PDRectangle.A4
                        }
                        val page = PDPage(pageSize)
                        document.addPage(page)
                        val image = LosslessFactory.createFromImage(document, rotated)
                        val scale = min(
                            page.mediaBox.width / rotated.width,
                            page.mediaBox.height / rotated.height
                        )
                        val width = rotated.width * scale
                        val height = rotated.height * scale
                        val x = (page.mediaBox.width - width) / 2f
                        val y = (page.mediaBox.height - height) / 2f
                        PDPageContentStream(document, page).use { stream ->
                            stream.drawImage(image, x, y, width, height)
                        }
                    } finally {
                        rotated.recycle()
                    }
                }
                check(document.numberOfPages > 0) { "No readable images were selected" }
                document.save(destination)
            }
        } catch (throwable: Throwable) {
            destination.delete()
            throw throwable
        }
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > MAX_IMAGE_SIDE_PX) {
            sampleSize *= 2
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            )
        }
    }

    private fun readRotation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            when (
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)

    private fun rotateBitmap(source: Bitmap, rotation: Int): Bitmap {
        if (rotation == 0) return source
        val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun uniqueDestination(directory: File, requestedName: String): File {
        val trimmedName = requestedName.trim()
        val sanitized = (if (trimmedName.endsWith(".pdf", ignoreCase = true)) {
            trimmedName.dropLast(4)
        } else {
            trimmedName
        })
            .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
            // 空文件名使用时间戳兜底，与默认命名一样不添加品牌前缀。
            .ifBlank { System.currentTimeMillis().toString() }
        var destination = File(directory, "$sanitized.pdf")
        var suffix = 1
        while (destination.exists()) {
            destination = File(directory, "$sanitized($suffix).pdf")
            suffix++
        }
        return destination
    }
}
