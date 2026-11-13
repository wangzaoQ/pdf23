package com.pdf.editor.common.tools

import com.pdf.editor.MyAPP
import com.pdf.editor.data.FilesData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

class Scan1(
    private val parentDirectory: File,
    private val waitTime: Long = 0L,
    private val onProgressBatch: (List<File>) -> Unit = {},
    private val onCompleteFilesData: (List<FilesData>) -> Unit = {},
    private val tag: String = "Scan1"
) {
    private val logTag = "Scan1:$tag"
    private val processChunkSize = 8
    private val coolDownDelayMs = 12L
    private val allowedExt = setOf("pdf", "docx", "doc", "xls", "xlsx", "ppt", "pptx", "txt")

    private fun shouldInclude(file: File): Boolean {
        val idx = file.name.lastIndexOf('.')
        if (idx == -1) return false
        val ext = file.name.substring(idx + 1).lowercase()
        if (file.name.contains("log")) return false
        return ext in allowedExt
    }

    suspend fun scanStart() {
        val startTime = System.currentTimeMillis()
        AppLogs.dLog(logTag, "start scan")
        val allFiles = scanDirectoryIterative(parentDirectory)
        val elapsed = System.currentTimeMillis() - startTime
        AppLogs.dLog(logTag, "scan cost:$elapsed ms")
        if (elapsed < waitTime) {
            delay(waitTime - elapsed)
        }
        val filesData = withContext(Dispatchers.IO) {
            allFiles
                .chunked(processChunkSize)
                .flatMap { chunk ->
                    currentCoroutineContext().ensureActive()
                    val batchResult = chunk.map { file ->
                        async {
                            FilesData.createDefault(file).apply {
                                FileManager.ensurePdfThumbnail(MyAPP.instance, this)
                            }
                        }
                    }.awaitAll()
                    if (coolDownDelayMs > 0L) {
                        delay(coolDownDelayMs)
                    }
                    batchResult
                }
                .sortedByDescending { it.fileTime }
        }
        currentCoroutineContext().ensureActive()
        onCompleteFilesData(filesData)
    }

    private suspend fun scanDirectoryIterative(root: File): List<File> = withContext(Dispatchers.IO) {
        val stack = ArrayDeque<File>().apply { add(root) }
        val result = mutableListOf<File>()
        val batch = mutableListOf<File>()
        val batchSize = 100

        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val dir = stack.removeLast()
            val children = dir.listFiles() ?: continue
            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (child.isDirectory) {
                    if (child.name.startsWith("Android")) continue
                    stack.add(child)
                } else {
                    if (!shouldInclude(child)) continue
                    result.add(child)
                    batch.add(child)
                    if (batch.size >= batchSize) {
                        onProgressBatch(batch.toList())
                        batch.clear()
                        if (coolDownDelayMs > 0L) {
                            delay(coolDownDelayMs)
                        }
                    }
                }
            }
        }
        if (batch.isNotEmpty()) {
            onProgressBatch(batch.toList())
        }
        result
    }
}
