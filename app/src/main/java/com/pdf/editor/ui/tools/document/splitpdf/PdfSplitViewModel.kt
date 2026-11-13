// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.splitpdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pdf.editor.MyAPP
import com.pdf.editor.common.core.BaseDataModel
import com.pdf.editor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/** 原始页下标不会随删除改变，缩略图只保存文件路径，避免整份 PDF 的位图常驻内存。 */
data class PdfSplitPage(val sourceIndex: Int, val thumbnail: File)

/** 拆分队列独立持有数据；继承项目 CoreViewModel，配置重建时继续使用当前删除结果。 */
class PdfSplitViewModel : BaseDataModel() {
    private val mutablePages = MutableLiveData<List<PdfSplitPage>>(emptyList())
    val pages: LiveData<List<PdfSplitPage>> = mutablePages
    private val mutableLoading = MutableLiveData(true)
    val loading: LiveData<Boolean> = mutableLoading
    private val mutableError = MutableLiveData<Int>()
    val error: LiveData<Int> = mutableError
    private var started = false
    private var cleared = false
    private var thumbnailDirectory: File? = null

    /** 仅初始化一次；缩略图逐页在 IO 线程渲染，主线程只接收准备好的列表。 */
    fun load(sourcePath: String) {
        if (started) return
        started = true
        loadData(loadBack = {
            val prepared = withContext(Dispatchers.IO) {
                val directory = File.createTempFile("split_pages_", "", MyAPP.instance.cacheDir)
                check(directory.delete() && directory.mkdirs())
                thumbnailDirectory = directory
                try {
                    val result = mutableListOf<PdfSplitPage>()
                    // use 自动关闭文件描述符、渲染器与当前页；任意时刻只渲染一页，限制内存占用。
                    ParcelFileDescriptor.open(File(sourcePath), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            check(renderer.pageCount > 0)
                            repeat(renderer.pageCount) { index ->
                                currentCoroutineContext().ensureActive()
                                renderer.openPage(index).use { page ->
                                    // maxOf 取较长边计算适配比例；coerceAtLeast 保证位图尺寸至少一个像素。
                                    val scale = 180f / maxOf(page.width, page.height)
                                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1),
                                        (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                    try {
                                        bitmap.eraseColor(Color.WHITE)
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        val thumbnail = File(directory, "$index.png")
                                        thumbnail.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                                        result.add(PdfSplitPage(index, thumbnail))
                                    } finally { bitmap.recycle() }
                                }
                            }
                        }
                    }
                    result
                } catch (error: Throwable) {
                    directory.deleteRecursively()
                    throw error
                }
            }
            mutablePages.value = prepared
            mutableLoading.value = false
        }, failBack = {
            mutableLoading.value = false
            mutableError.value = R.string.app_pdf_split_load_failed
        })?.invokeOnCompletion {
            // ?. 仅在任务成功创建时注册；页面关闭后若 IO 刚退出，补清理期间产生的缓存文件。
            if (cleared) thumbnailDirectory?.deleteRecursively()
        }
    }

    /** filterNot 删除原始页号匹配的项，生成新列表；剩余页顺序和原始页号不变。 */
    fun remove(sourceIndex: Int) {
        mutablePages.value = mutablePages.value.orEmpty().filterNot { it.sourceIndex == sourceIndex }
    }

    override fun onCleared() {
        cleared = true
        thumbnailDirectory?.deleteRecursively()
        super.onCleared()
    }
}
