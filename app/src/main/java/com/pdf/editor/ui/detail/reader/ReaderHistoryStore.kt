package com.pdf.editor.ui.detail.reader

import android.app.Activity
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.tools.DataUtils
import com.pdf.editor.data.FilesData
import java.io.File

/**
 * PDF14-MIGRATION: 将 pdf14 的三个阅读历史接口接到当前项目的 fileId 缓存。
 * 按绝对路径隔离会话，入口携带的数据优先于最近列表，确保演示文件的固定 fileId 被保留。
 */
object ReaderHistoryStore {
    const val EXTRA_FILES_DATA = "pdf_reader_files_data"

    private val sessionsByPath = mutableMapOf<String, LinkedHashMap<Activity, FilesData>>()

    fun attach(activity: Activity) {
        val serializedData = activity.intent.getStringExtra(EXTRA_FILES_DATA) ?: return
        val data = DataUtils.parseBeanByGson(
            serializedData,
            FilesData::class.java
        ) ?: return
        if (data.filePath.isBlank()) return
        sessionsByPath.getOrPut(pathKey(data.filePath)) { linkedMapOf() }[activity] = data
    }

    fun detach(activity: Activity) {
        val paths = sessionsByPath.entries.iterator()
        while (paths.hasNext()) {
            val entry = paths.next()
            entry.value.remove(activity)
            if (entry.value.isEmpty()) paths.remove()
        }
    }

    fun updateLastOpenedAtMillis(
        absolutePath: String,
        openedAtMillis: Long = System.currentTimeMillis()
    ) {
        val data = resolveData(absolutePath)
        data.fileOpenTime = openedAtMillis
        LocalDataManager.setOpenTime(data.fileId, openedAtMillis)
        LocalDataManager.recentFileData = data
        LocalDataManager.saveRecentOpenFiles(data)
    }

    fun getLastOpenedPage(absolutePath: String): Int {
        return LocalDataManager.getFilePage(resolveData(absolutePath).fileId)
    }

    fun updateLastOpenedPage(absolutePath: String, page: Int) {
        LocalDataManager.setFilePage(resolveData(absolutePath).fileId, page)
    }

    private fun resolveData(absolutePath: String): FilesData {
        val key = pathKey(absolutePath)
        sessionsByPath[key]?.values?.lastOrNull()?.let { return it }
        LocalDataManager.getRecentOpenFiles()
            .firstOrNull { pathKey(it.filePath) == key }
            ?.let { return it }
        LocalDataManager.recentFileData
            ?.takeIf { pathKey(it.filePath) == key }
            ?.let { return it }
        return FilesData.createDefault(File(absolutePath))
    }

    private fun pathKey(path: String): String = File(path).absolutePath
}
