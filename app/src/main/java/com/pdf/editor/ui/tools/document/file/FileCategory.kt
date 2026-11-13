// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.file

enum class FileCategory {
    ALL,
    PDF,
    WORD,
    EXCEL,
    PPT,
    TEXT;

    /**
     * 对扫描结果执行首页标签过滤。
     * ALL 原样返回全部受支持文件，其余标签只返回与自身类型完全一致的文件。
     */
    fun filter(files: List<ScannedFile>): List<ScannedFile> {
        return if (this == ALL) files else files.filter { it.category == this }
    }

    companion object {
        /**
         * 当前项目首页只显示 All、PDF、Word、Excel 四个标签，但扫描数据还需要接收 PPT
         * 和 TXT。PPT/TEXT 作为内部文件类型存在，不会创建独立标签，只会通过 ALL 展示。
         *
         * Word 同时兼容旧版 doc 与新版 docx，Excel 同时兼容 xls 与 xlsx，PPT 同时兼容
         * ppt 与 pptx，避免遗漏设备上的常见 Office 格式。
         */
        fun fromFileName(fileName: String): FileCategory? {
            return when (fileName.substringAfterLast('.', "").lowercase()) {
                "pdf" -> PDF
                "doc", "docx" -> WORD
                "xls", "xlsx" -> EXCEL
                "ppt", "pptx" -> PPT
                "txt" -> TEXT
                else -> null
            }
        }

        val supportedExtensions: Set<String> = setOf(
            "pdf",
            "doc",
            "docx",
            "xls",
            "xlsx",
            "ppt",
            "pptx",
            "txt"
        )
    }
}