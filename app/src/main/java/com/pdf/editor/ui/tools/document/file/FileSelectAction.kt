// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.file

import com.pdf.editor.ui.tools.document.file.FileCategory

/** Tool flow that opened the file picker and the file type it accepts. */
enum class FileSelectAction(val category: FileCategory) {
    WORD_TO_PDF(FileCategory.WORD),
    PDF_TO_IMAGE(FileCategory.PDF),
    MERGE_PDF(FileCategory.PDF),
    SPLIT_PDF(FileCategory.PDF),
    PDF_TO_WORD(FileCategory.PDF)
}
