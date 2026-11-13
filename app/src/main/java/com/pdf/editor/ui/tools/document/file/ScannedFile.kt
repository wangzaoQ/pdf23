// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.file

import android.content.Context
import java.io.File

/**
 * 首页文件列表使用的精简数据模型。
 *
 * `docflow` 原模型还保存收藏标识、最近打开时间等字段，这些字段都服务于收藏或文件
 * 详情页面。本项目只保留列表和悬浮窗需要的基础信息，避免把二级页面依赖带进来。
 *
 * @property name 文件名，包含扩展名，例如 `report.pdf`。首页直接使用该字段显示标题，
 * 同时扫描器会通过文件名判断是否为需要排除的 `sample_en.pdf`。
 * @property absolutePath 文件在设备或应用缓存目录中的绝对路径。PDF 缩略图生成器通过
 * 该路径读取源文件，后续文件打开、删除或分享等操作也应以它定位真实文件。内置示例 PDF
 * 对应的是复制到应用缓存目录后的真实路径，而不是 assets 内部路径。
 * @property sizeBytes 文件原始字节数，来源于 [java.io.File.length]。该字段不预先格式化，列表绑定
 * 时再根据数值转换为 B、KB、MB 或 GB，后续也可以直接用它进行文件大小排序。
 * @property modifiedAtMillis 文件最后修改时间的 Unix 毫秒值，通常来源于 [java.io.File.lastModified]。
 * 首页将它格式化为日期文本，扫描结果默认也按该字段从新到旧排列。
 * @property category 根据扩展名解析出的内部文件类型。All 分类不限制该字段；PDF、Word、
 * Excel 分类只接受对应类型；PPT 和 TEXT 没有独立标签，因此只会显示在 All 中。
 * @property thumbnailPath PDF 第一页缩略图在应用缓存目录中的绝对路径。该值只为成功生成
 * 缩略图的 PDF 设置；Word、Excel、PPT、TXT 或生成失败的 PDF 均为 null，列表会回退到
 * 对应的静态文件类型图标。
 * @property isBuiltInSample 是否为随应用打包的内置示例 PDF。普通扫描文件始终为 false；
 * 示例文件为 true，并由 ListFragment 在完成分类过滤后插入 All/PDF 列表首位。保留该标记
 * 便于后续排序、删除或其他操作区分示例文件与用户设备上的真实文件。
 * @property lastOpenedPage 上一次打开 PDF 时停留的页码，从 1 开始。
 * @property lastOpenedAtMillis 上一次打开 PDF 的 Unix 毫秒时间戳；未打开过时为 0。
 */
data class ScannedFile(
    val name: String,
    val absolutePath: String,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val category: FileCategory,
    val thumbnailPath: String? = null,
    val isBuiltInSample: Boolean = false,
    val lastOpenedPage: Int = 1,
    val lastOpenedAtMillis: Long = 0L
) {
    companion object {
        const val SAMPLE_ASSET_PATH = "cache/sample_en.pdf"
        const val SAMPLE_FILE_NAME = "sample_en.pdf"

        /**
         * 将磁盘文件转换为首页列表数据。
         *
         * 扫描阶段已经按扩展名过滤过文件，这里再次通过 [FileCategory.Companion.fromFileName]
         * 解析类型，使数据构建逻辑集中在一个地方，也便于后续单独复用。
         */
        fun fromFile(file: File): ScannedFile? {
            val category = FileCategory.fromFileName(file.name) ?: return null
            return ScannedFile(
                name = file.name,
                absolutePath = file.absolutePath,
                sizeBytes = file.length().coerceAtLeast(0L),
                modifiedAtMillis = file.lastModified().takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
                category = category
            )
        }

        /** 返回示例 PDF 在应用缓存目录中的固定目标位置。 */
        fun builtInSampleCacheFile(context: Context): File {
            return File(File(context.cacheDir, "asset_samples"), SAMPLE_FILE_NAME)
        }

        /**
         * 从缓存文件构建示例列表数据，并在缓存缺失时从 assets 自动恢复。
         *
         * SplashActivity 会在启动阶段提前复制，一般情况下这里直接复用缓存。若缓存被系统
         * 清理、文件不存在或长度为 0，则重新从 assets 复制，确保示例 PDF 不会因为缓存
         * 生命周期而永久消失。只有目录创建或文件复制失败时才返回 null。
         */
        fun fromBuiltInSample(context: Context): ScannedFile? = runCatching {
            val cachedSample = builtInSampleCacheFile(context)
            val sampleDirectory = cachedSample.parentFile ?: return null
            if (!sampleDirectory.exists() && !sampleDirectory.mkdirs()) {
                return null
            }

            if (!cachedSample.exists() || cachedSample.length() == 0L) {
                context.assets.open(SAMPLE_ASSET_PATH).use { input ->
                    cachedSample.outputStream().use { output -> input.copyTo(output) }
                }
            }

            fromFile(cachedSample)?.copy(isBuiltInSample = true)
        }.getOrNull()
    }
}
