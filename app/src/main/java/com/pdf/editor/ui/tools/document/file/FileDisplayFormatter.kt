// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.file

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** 首页文件项的纯展示格式化工具，集中控制日期与文件大小的显示规则。 */
object FileDisplayFormatter {

    /** 按当前应用语言显示短日期；时间无效时显示占位符。 */
    fun formatDate(timeMillis: Long, locale: Locale): String {
        if (timeMillis <= 0L) return "--"
        return DateFormat.getDateInstance(DateFormat.SHORT, locale).format(Date(timeMillis))
    }

    /** 根据文件大小选择单位，并按当前应用语言显示小数分隔符。 */
    fun formatSize(sizeBytes: Long, locale: Locale): String {
        val bytes = sizeBytes.coerceAtLeast(0L).toDouble()
        return when {
            bytes < KILOBYTE -> formatUnit(bytes, "B", locale)
            bytes < MEGABYTE -> formatUnit(bytes / KILOBYTE, "KB", locale)
            bytes < GIGABYTE -> formatUnit(bytes / MEGABYTE, "MB", locale)
            else -> formatUnit(bytes / GIGABYTE, "GB", locale)
        }
    }

    private fun formatUnit(value: Double, unit: String, locale: Locale): String {
        val number = NumberFormat.getNumberInstance(locale).apply {
            isGroupingUsed = false
            minimumFractionDigits = 0
            maximumFractionDigits = if (value >= 100) 0 else 1
        }.format(value)
        return "$number $unit"
    }

    private const val KILOBYTE = 1024.0
    private const val MEGABYTE = KILOBYTE * 1024.0
    private const val GIGABYTE = MEGABYTE * 1024.0
}
