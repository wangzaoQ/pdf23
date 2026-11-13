// PDF14-MIGRATION: 从 pdf14 复制；仅包名及已标记宿主接口/资源适配，未优化转换算法。
package com.pdf.editor.ui.tools.document.common

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 全部页面逐页导出 PNG：保持方向和比例，白底，长边 2000 像素，无 JPG 有损压缩。
 * 使用系统 PdfRenderer 和 MediaStore，不引入网络或三方依赖；每次只持有一页 Bitmap。
 */
object PdfToImagesConverter {
    class Protected : IllegalArgumentException("PDF requires a password")
    class StoragePermission : IllegalStateException("Storage permission is required")

    /**
     * Android 10+ 用 IS_PENDING 隐藏未完成图片；旧系统依赖已有存储权限并指定 DATA 路径。
     * 清理在外层捕获取消，包括 withContext 返回主线程时的取消，避免留下没有成功交付的图片。
     */
    suspend fun convert(context: Context, source: File, albumName: String,
        onProgress: suspend (Int, Int) -> Unit): ImageExportResult {
        val resolver = context.contentResolver
        val inserted = mutableListOf<Uri>()
        val legacyFiles = mutableListOf<File>()
        var legacyDirectory: File? = null
        try {
            return withContext(Dispatchers.IO) {
                require(source.isFile && source.canRead()) { "PDF file is unavailable" }
                if (Build.VERSION.SDK_INT < 29 && ContextCompat.checkSelfPermission(context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) throw StoragePermission()
                val cleanName = albumName.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                    .trim('.').ifBlank { "PDF" }.take(60)
                // 随机批次后缀确保并行或同名转换互不覆盖，清理也只针对当前批次。
                val folder = "$cleanName-${UUID.randomUUID().toString().take(8)}"
                val relativePath = "${Environment.DIRECTORY_PICTURES}/PDF14/$folder/"
                if (Build.VERSION.SDK_INT < 29) {
                    legacyDirectory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PDF14/$folder")
                    check(legacyDirectory!!.mkdirs()) { "Unable to create image directory" }
                }
                val createdAt = System.currentTimeMillis()
                var totalBytes = 0L
                ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    val renderer = try { PdfRenderer(descriptor) } catch (error: SecurityException) { throw Protected() }
                    renderer.use {
                        check(it.pageCount > 0) { "PDF has no pages" }
                        onProgress(0, it.pageCount)
                        for (index in 0 until it.pageCount) {
                            currentCoroutineContext().ensureActive()
                            it.openPage(index).use { page ->
                                val scale = 2000f / max(page.width, page.height).coerceAtLeast(1)
                                val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                                val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try {
                                    bitmap.eraseColor(Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    currentCoroutineContext().ensureActive()
                                    val name = String.format(Locale.US, "page_%03d.png", index + 1)
                                    val values = ContentValues().apply {
                                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                        put(MediaStore.Images.Media.WIDTH, width)
                                        put(MediaStore.Images.Media.HEIGHT, height)
                                        put(MediaStore.Images.Media.DATE_TAKEN, createdAt)
                                        if (Build.VERSION.SDK_INT >= 29) {
                                            put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
                                            put(MediaStore.Images.Media.IS_PENDING, 1)
                                        } else {
                                            val file = File(legacyDirectory!!, name)
                                            legacyFiles.add(file)
                                            @Suppress("DEPRECATION")
                                            put(MediaStore.Images.Media.DATA, file.absolutePath)
                                        }
                                    }
                                    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                                        ?: error("Unable to create media entry")
                                    inserted.add(uri)
                                    val stream = resolver.openOutputStream(uri, "w") ?: error("Unable to write image")
                                    CountingStream(stream).use { output ->
                                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG encoding failed" }
                                        totalBytes += output.count
                                    }
                                } finally { bitmap.recycle() }
                            }
                            onProgress(index + 1, it.pageCount)
                        }
                    }
                }
                // 所有 PNG 写入成功才开始发布；发布失败或取消时也回滚当前批次。
                if (Build.VERSION.SDK_INT >= 29) for (uri in inserted) {
                    currentCoroutineContext().ensureActive()
                    check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) == 1)
                }
                currentCoroutineContext().ensureActive()
                ImageExportResult(inserted.first().toString(), inserted.size, folder, totalBytes, createdAt)
            }
        } catch (error: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) {
                // 删除当前任务自己创建的记录，不按文件夹批量查询或删除用户已有图片。
                inserted.forEach { uri -> runCatching { resolver.delete(uri, null, null) }.onFailure { error.addSuppressed(it) } }
                legacyFiles.forEach { it.delete() }
                legacyDirectory?.delete()
            }
            throw error
        }
    }

    /** PNG 直接写入 MediaStore，顺便统计实际字节数，不把压缩后的整张图片再复制到内存。 */
    private class CountingStream(output: OutputStream) : FilterOutputStream(output) {
        var count = 0L
        override fun write(value: Int) { out.write(value); count++ }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { out.write(bytes, offset, length); count += length }
    }
}
