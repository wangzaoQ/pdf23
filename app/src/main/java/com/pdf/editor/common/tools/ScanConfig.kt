package com.pdf.editor.common.tools

import android.net.Uri
import com.pdf.editor.data.FilesData

object ScanConfig {

    final const  val FILE_TYPE_PDF = 0
    final const  val FILE_TYPE_DOC = 1
    final const  val FILE_TYPE_PPT = 2
    final const  val FILE_TYPE_EXCEL = 3
    final const  val FILE_TYPE_TEXT = 4
    final const  val FILE_TYPE_OTHER = 5
    final const  val FILE_TYPE_ALL = 100


    final const  val FROM_TAG_FILE = "FILE"
    final const  val FROM_TAG_COLLECT = "COLLECT"
    final const  val FROM_TAG_SEARCH = "SEARCH"


    final const val  SORT_TYPE_OPEN_TIME_NEWEST= "OPEN_TIME_NEWEST"
    final const val  SORT_TYPE_OPEN_TIME_OLDEST = "OPEN_TIME_OLDEST"
    final const val  SORT_TYPE_FILE_NAME_A_Z = "FILE_NAME_A_Z"
    final const val  SORT_TYPE_FILE_NAME_Z_A = "FILE_NAME_Z_A"

    var allFileList = mutableListOf<FilesData>()

    /**
     * 当前扫描会话的页面图片列表。
     *
     * ScanActivity 的预览、重拍、加页、最终导出 PDF
     * 都基于这份内存数据工作。
     */
    var scanImgUriList = mutableListOf<Uri>()

    /**
     * 图片转 PDF 流程使用的临时图片列表。
     */
    var imgToPdfUriList = mutableListOf<Uri>()

    /**
     * allFileList 是扫描结果的全量内存快照。
     *
     * 文件改名后如果这里只改当前列表 item，不更新 allFileList，
     * 后续搜索、分类页临时读取、排序重刷时又会把旧文件名带回来。
     */
    fun replaceFile(oldFilePath: String, newData: FilesData) {
        val index = allFileList.indexOfFirst {
            it.filePath.trim().lowercase() == oldFilePath.trim().lowercase()
        }
        if (index >= 0) {
            allFileList[index] = newData
        }
    }

    fun removeFile(filePath: String) {
        allFileList.removeAll {
            it.filePath.trim().lowercase() == filePath.trim().lowercase()
        }
    }
}
