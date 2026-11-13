// PDF14-MIGRATION: 从 pdf14 复制；仅包名及已标记宿主接口/资源适配，未优化转换算法。
package com.pdf.editor.ui.tools.document.common

import java.io.File
import java.io.Serializable

/**
 * 工具执行页的统一输入协议，使用可序列化的纯参数通过 Intent 传递。
 * 当前支持图片转 PDF、DOCX 与 PDF 转换、PDF 导出 PNG 和 PDF 合并；各工具使用专属参数子类型，
 * 避免提前增加未实现的入口，或使用大量可空字段表达不同任务。
 */
sealed class DocumentTaskRequest : Serializable {
    /** 原始页下标从 0 开始，必须按原顺序；全部保留页输出到一个 PDF，不逐页生成多个文件。 */
    data class SplitPdf(val sourcePath: String, val pageIndices: List<Int>, val fileName: String) : DocumentTaskRequest()
    /** 合并全部源 PDF 页面；sourcePaths 是队列快照，必须按该顺序执行且至少包含两个文件。 */
    data class MergePdfs(val sourcePaths: List<String>, val fileName: String) : DocumentTaskRequest()
    /** imageUris 保留队列排序，以字符串传递 URI；fileName 沿用现有输出命名规则。 */
    data class ImagesToPdf(val imageUris: List<String>, val fileName: String) : DocumentTaskRequest()
    /** 只接收本地 DOCX 文件路径；不传递预览 WebView，也不请求在线转换服务。 */
    data class WordToPdf(val sourcePath: String, val fileName: String) : DocumentTaskRequest()
    /** 转换本地 PDF 的全部页面，图文混合页在文字之后附加整页截图。 */
    data class PdfToWord(val sourcePath: String, val fileName: String) : DocumentTaskRequest()
    /** 全部 PDF 页面离线导出 PNG，albumName 为用户确认的相册目录名称。 */
    data class PdfToImages(val sourcePath: String, val albumName: String) : DocumentTaskRequest()
}

/** 相册结果只携带首图和汇总，避免将整份 PDF 的 URI 列表塞入 Intent。 */
data class ImageExportResult(val firstUri: String, val count: Int, val albumName: String,
    val totalBytes: Long, val createdAtMillis: Long)

/** 文档返回文件，图片返回相册信息；两种结果互斥，调用方不能把相册 URI 当作文件路径。 */
data class DocumentTaskResult(val files: List<File> = emptyList(), val images: ImageExportResult? = null) {
    init { require((files.isNotEmpty()) != (images != null)) }
}

/** 页面只根据任务状态更新 UI，不直接执行文件创建或缩略图生成。 */
sealed interface DocumentTaskState {
    /** 尚未提交请求，仅作为 ViewModel 的初始状态。 */
    data object Idle : DocumentTaskState
    /** completed 为 null 表示模拟/不确定进度；转换任务统计源页，合并任务统计已追加的源文件。 */
    data class Running(val total: Int, val completed: Int? = null) : DocumentTaskState
    /** 文件创建及原有收尾步骤已完成，可以进入现有成功页。 */
    data class Success(val result: DocumentTaskResult) : DocumentTaskState
    /** 统一失败出口；指定文案用于密码或文件错误，null 沿用旧任务的 PDF 创建失败提示。 */
    data class Failed(val messageRes: Int? = null) : DocumentTaskState
}
