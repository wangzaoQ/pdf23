// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.imagepdf

import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide
import com.pdf.editor.R
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.ui.detail.reader.setSequentialClickListener
import com.pdf.editor.databinding.ToolActivityMergeSuccessBinding
import com.pdf.editor.ui.tools.document.file.FileDisplayFormatter
import com.pdf.editor.ui.tools.document.file.PdfThumbnailGenerator
import com.pdf.editor.ui.detail.reader.ReaderSurfaceActivity
import com.pdf.editor.ui.tools.document.file.ContentSurfaceActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import com.pdf.editor.ui.tools.document.common.ImageExportResult

class MergeSuccessActivity : BaseActivity<ToolActivityMergeSuccessBinding>() {
    // PDF14-MIGRATION: 当前 BaseActivity 的绑定字段；源页面绑定调用保持不变。
    private val binding: ToolActivityMergeSuccessBinding get() = aBinding

    private var createdFile: File? = null
    private var firstImage: Uri? = null

    override fun getBinding(inflater: LayoutInflater): ToolActivityMergeSuccessBinding {
        return ToolActivityMergeSuccessBinding.inflate(inflater)
    }

    override fun setShowView() {
        // 图片结果仅传首图 URI 与统计，直接加载相册缩略图，不按本地 PDF 路径校验。
        intent.getStringExtra(EXTRA_FIRST_IMAGE)?.let { value ->
            val uri = Uri.parse(value)
            if (uri.scheme != "content" || intent.getIntExtra(EXTRA_IMAGE_COUNT, 0) <= 0) {
                Toast.makeText(this, R.string.app_images_unavailable, Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            firstImage = uri
            val count = intent.getIntExtra(EXTRA_IMAGE_COUNT, 0)
            binding.tvSuccessTips.text = getString(R.string.app_images_created, count)
            binding.tvFileName.text = intent.getStringExtra(EXTRA_ALBUM_NAME)
            val locale = resources.configuration.locales[0]
            binding.tvFileDate.text = FileDisplayFormatter.formatDate(intent.getLongExtra(EXTRA_CREATED_AT, 0), locale)
            binding.tvFileSize.text = " · ${FileDisplayFormatter.formatSize(intent.getLongExtra(EXTRA_TOTAL_BYTES, 0), locale)}"
            Glide.with(this).load(uri).into(binding.ivFile)
            return
        }
        val file = intent.getStringExtra(EXTRA_FILE_PATH)?.let(::File)
        if (file == null || !file.isFile) {
            Toast.makeText(this, R.string.app_result_file_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        createdFile = file
        // Word 输出使用 DOC 类型图标及对应成功文案，不尝试生成 PDF 缩略图。
        val isWord = file.extension.equals("docx", true)
        if (isWord) {
            binding.ivFile.setImageResource(R.drawable.ic_word)
            binding.tvSuccessTips.setText(R.string.app_word_created)
        }
        if (!isWord) binding.tvSuccessTips.setText(R.string.app_pdf_created)
        val appLocale = resources.configuration.locales[0]
        binding.tvFileName.text = file.name
        binding.tvFileDate.text = FileDisplayFormatter.formatDate(file.lastModified(), appLocale)
        binding.tvFileSize.text = " · ${FileDisplayFormatter.formatSize(file.length(), appLocale)}"
        if (!isWord) addLaunch(
            success = {
                val thumbnail = PdfThumbnailGenerator.generate(this@MergeSuccessActivity, file.absolutePath)
                withContext(Dispatchers.Main) {
                    if (thumbnail != null) Glide.with(binding.ivFile).load(thumbnail).into(binding.ivFile)
                }
            },
            failBack = { }
        )
    }

    override fun setListener() {
        binding.btnOpen.setSequentialClickListener {
            firstImage?.let {
                openImage(it)
                return@setSequentialClickListener
            }
            // 普通 Word 预览不携带转换来源，避免结果页打开后又出现“转 PDF”按钮。
            createdFile?.let {
                if (it.extension.equals("docx", true)) ContentSurfaceActivity.open(this, it.name, it.absolutePath)
                // PDF14-MIGRATION: 源 open 通过成功页类型判定纯预览；当前入口显式传参保持相同行为。
                else ReaderSurfaceActivity.open(this, it.name, it.absolutePath, recordHistory = false)
            }
            finish()
        }
    }

    /**
     * 用 ACTION_VIEW 交给系统图片查看器（通常是相册），仅授权首图读取权限。
     * 不指定厂商品牌或伪造目录 Intent；相册是否支持左右切换由接收应用决定。
     * 没有接收应用或文件已删除时保留成功页，便于用户返回或稍后从相册查找。
     */
    private fun openImage(uri: Uri) {
        lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) {
                runCatching { contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)
            }
            if (isFinishing || isDestroyed) return@launch
            if (!available) {
                Toast.makeText(this@MergeSuccessActivity, R.string.app_images_unavailable, Toast.LENGTH_LONG).show()
                return@launch
            }
            try {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/png")
                    clipData = ClipData.newRawUri("PNG", uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                finish()
            } catch (error: ActivityNotFoundException) {
                Toast.makeText(this@MergeSuccessActivity, R.string.app_no_image_viewer, Toast.LENGTH_LONG).show()
            } catch (error: SecurityException) {
                Toast.makeText(this@MergeSuccessActivity, R.string.app_images_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val EXTRA_FILE_PATH = "created_pdf_path"
        private const val EXTRA_FIRST_IMAGE = "first_image_uri"
        private const val EXTRA_IMAGE_COUNT = "image_count"
        private const val EXTRA_ALBUM_NAME = "album_name"
        private const val EXTRA_TOTAL_BYTES = "image_total_bytes"
        private const val EXTRA_CREATED_AT = "images_created_at"

        /** 首图和汇总使用少量标量传参，几千页也不会导致 Binder 传输超限。 */
        fun startImages(context: Context, result: ImageExportResult) {
            context.startActivity(Intent(context, MergeSuccessActivity::class.java).apply {
                putExtra(EXTRA_FIRST_IMAGE, result.firstUri)
                putExtra(EXTRA_IMAGE_COUNT, result.count)
                putExtra(EXTRA_ALBUM_NAME, result.albumName)
                putExtra(EXTRA_TOTAL_BYTES, result.totalBytes)
                putExtra(EXTRA_CREATED_AT, result.createdAtMillis)
            })
        }

        fun start(context: Context, absolutePath: String) {
            context.startActivity(
                Intent(context, MergeSuccessActivity::class.java).putExtra(EXTRA_FILE_PATH, absolutePath)
            )
        }
    }
}
