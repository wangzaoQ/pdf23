package com.pdf.editor.common.tools

import android.content.ContentResolver
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.pdf.editor.MyAPP
import com.pdf.editor.common.nf.NFControl
import com.pdf.editor.common.nf.NFEnum
import com.pdf.editor.common.nf.NotifyShow
import com.pdf.editor.data.NFData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID

object SendUtils {

    const val EVENT_EXTERNAL_FILE_READY = "external_file_ready"

    val SUPPORTED_MIMETYPES = setOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "text/plain"
    )

    @Volatile
    var tempFile: File? = null

    var TAG = "SendUtils"

    /** 媒体库通知到达后先等一小段时间，避开插入时的 IS_PENDING 状态。 */
    private const val MEDIA_CHECK_DELAY_MS = 800L

    /** 媒体库里仍处于 pending 的记录，稍后重查。 */
    private const val PENDING_RETRY_DELAY_MS = 2000L
    private const val MAX_PENDING_RETRY = 4

    /** 文件系统事件到达后等待写入完成。 */
    private const val FS_CHECK_DELAY_MS = 700L

    private const val MAX_REMEMBERED_FILES = 200

    /** FileObserver.ISDIR 是 @hide 常量，这里按系统源码中的值判断目录事件。 */
    private const val IS_DIRECTORY = 0x40000000

    /** 注册监听时的秒级时间戳，早于该时间入库的文件视为历史文件，不触发通知。 */
    @Volatile
    private var baselineDateAdded = 0L

    @Volatile
    private var registered = false

    val handler = Handler(Looper.getMainLooper())

    private val observerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var mediaObserver: ContentObserver? = null
    private val fileObservers = mutableListOf<FileObserver>()

    private var mediaCheckScheduled = false
    private var pendingRetryCount = 0
    private var pendingRetryScheduled = false

    /** 已通知过的文件（绝对路径小写 / media:id），用于媒体库和文件系统两条链路互相去重。 */
    private val notifiedKeys = LinkedHashMap<String, Boolean>()

    fun getActualMimeType(uri: Uri, intent: Intent): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            return MyAPP.instance.contentResolver.getType(uri)
        }

        if (intent.type != null) {
            return intent.type
        }

        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(
            MimeTypeMap.getFileExtensionFromUrl(uri.toString()).lowercase()
        )
    }

    fun isSupported(mimeType: String?): Boolean {
        if (mimeType == null) return false
        val pureMimeType = mimeType.split(";")[0].lowercase()
        return SUPPORTED_MIMETYPES.contains(pureMimeType)
    }

    suspend fun saveFileToCache(uri: Uri): File? = withContext(Dispatchers.IO) {
        var startTime = System.currentTimeMillis()
        try {
            val originalFileName = getFileName(uri) ?: "temp_${UUID.randomUUID()}.file"
            val storageDir = File(MyAPP.instance.cacheDir, "shared_docs")
            if (!storageDir.exists()) storageDir.mkdirs()

            val destFile = File(storageDir, originalFileName)

            MyAPP.instance.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            AppLogs.dLog(TAG,"存储文件耗时 ${System.currentTimeMillis() - startTime}")
            return@withContext destFile // 直接返回结果
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun parseUri(uri: Uri?){
        if (uri == null)return
        tempFile = null
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            tempFile = saveFileToCache(uri)
            if (tempFile != null) {
                MyAPP.eventLiveData.postValue(EVENT_EXTERNAL_FILE_READY)
            }
        }
    }

    fun consumeTempFile(): File? {
        val file = tempFile
        tempFile = null
        return file
    }

    private fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = MyAPP.instance.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.lastPathSegment
        }
        return result
    }

    /**
     * 注册新文件监听。
     *
     * 注意这里不再在 onChange 里查询媒体库后就结束：媒体库插入通知到达时记录常常还是
     * IS_PENDING，别的进程查不到；而且直接落盘（adb push、文件管理器复制）不一定会走
     * MediaStore。因此同时监听媒体库和公共目录，并在去抖后统一查询/校验。
     */
    fun registerListener() {
        if (registered) return
        registered = true

        runCatching {
            // 以注册时刻为基线，避免把安装/首次启动时已经存在的文件误报成新文件。
            baselineDateAdded = System.currentTimeMillis() / 1000

            registerMediaObserver()
            registerFileObservers()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                !Environment.isExternalStorageManager()
            ) {
                AppLogs.dLog(
                    NFControl.TAG,
                    "新文件监听：未授予“所有文件访问权限”，其它应用创建的文件可能收不到"
                )
            }
            AppLogs.dLog(NFControl.TAG, "新文件监听已注册 baseline=$baselineDateAdded")
        }.onFailure {
            // 新文件通知是尽力而为的附加能力，初始化失败不能影响 Application 启动。
            AppLogs.eLog(NFControl.TAG, "初始化新文件监听失败: ${it.message}")
        }
    }

    private fun registerMediaObserver() {
        if (mediaObserver != null) return
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                AppLogs.dLog(NFControl.TAG, "文档监听: uri=$uri")
                // 新的媒体库事件到来，重新给 pending 记录计数。
                pendingRetryCount = 0
                scheduleMediaCheck()
            }
        }
        mediaObserver = observer
        runCatching {
            MyAPP.instance.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"),
                true,
                observer
            )
        }.onFailure {
            AppLogs.eLog(NFControl.TAG, "注册媒体库监听失败: ${it.message}")
        }
    }

    /**
     * 监听公共目录中的直接文件写入。
     *
     * 媒体库通知只覆盖“通过 MediaStore 创建”的文件；adb push、文件管理器解压/复制等
     * 直接写文件系统的场景不会产生可用的媒体库记录，这里用 FileObserver 兜底。
     * Download/Documents 是文档最常见的落点，外部存储根目录用于兜住少量直接写在根目录的文件。
     */
    @Suppress("DEPRECATION")
    private fun registerFileObservers() {
        val root = Environment.getExternalStorageDirectory() ?: return
        val directories = listOf(
            File(root, Environment.DIRECTORY_DOWNLOADS),
            File(root, Environment.DIRECTORY_DOCUMENTS),
            root
        ).distinctBy { it.absolutePath }

        val mask = FileObserver.CREATE or
            FileObserver.CLOSE_WRITE or
            FileObserver.MOVED_TO

        for (directory in directories) {
            if (!directory.isDirectory) continue
            val observer = object : FileObserver(directory.absolutePath, mask) {
                override fun onEvent(event: Int, path: String?) {
                    if (path.isNullOrEmpty()) return
                    if (event and IS_DIRECTORY != 0) return
                    val name = File(path).name
                    if (!hasSupportedExtension(name)) return
                    val file = File(directory, path)
                    AppLogs.dLog(
                        NFControl.TAG,
                        "文件系统事件 event=${eventName(event)} path=${file.absolutePath}"
                    )
                    scheduleFileSystemCheck(file)
                }
            }
            runCatching { observer.startWatching() }
                .onSuccess { fileObservers.add(observer) }
                .onFailure {
                    AppLogs.eLog(NFControl.TAG, "监听目录失败 ${directory.absolutePath}: ${it.message}")
                }
        }
    }

    private fun scheduleFileSystemCheck(file: File) {
        handler.postDelayed({
            runCatching {
                if (!file.exists() || !file.isFile) {
                    AppLogs.dLog(NFControl.TAG, "文件系统事件已消失: ${file.absolutePath}")
                    return@postDelayed
                }
                val mime = resolveMime(file.name, null) ?: return@postDelayed
                if (!isDocMime(mime)) return@postDelayed
                if (!markNotified(normalizePath(file.absolutePath))) {
                    AppLogs.dLog(NFControl.TAG, "文件已通知过，忽略: ${file.absolutePath}")
                    return@postDelayed
                }
                notifyNewDoc(file.name, mime, "file_observer")
            }.onFailure {
                AppLogs.eLog(NFControl.TAG, "处理文件系统事件失败: ${it.message}")
            }
        }, FS_CHECK_DELAY_MS)
    }

    private fun scheduleMediaCheck(delayMs: Long = MEDIA_CHECK_DELAY_MS) {
        if (mediaCheckScheduled) return
        mediaCheckScheduled = true
        handler.postDelayed({
            mediaCheckScheduled = false
            observerScope.launch { checkNewDocsInMediaStore() }
        }, delayMs)
    }

    /**
     * 查询媒体库中基线之后入库的文档，处理 pending 重试和去重。
     *
     * 查询放在 IO 线程，避免媒体库事件密集时卡主线程；真正发通知再切回主线程。
     */
    private fun checkNewDocsInMediaStore() {
        val resolver = MyAPP.instance.contentResolver
        val projection = mutableListOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_ADDED
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.MediaColumns.IS_PENDING)
            projection.add(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            projection.add(MediaStore.MediaColumns.DATA)
        }

        val since = baselineDateAdded
        var maxDateAdded = since
        var hasPending = false
        val newDocs = mutableListOf<Pair<String?, String>>()

        try {
            resolver.query(
                MediaStore.Files.getContentUri("external"),
                projection.toTypedArray(),
                "${MediaStore.Files.FileColumns.DATE_ADDED} >= ?",
                arrayOf(since.toString()),
                "${MediaStore.Files.FileColumns.DATE_ADDED} ASC, " +
                    "${MediaStore.Files.FileColumns._ID} ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
                val nameIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
                val dateIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
                val isPendingIndex =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
                    } else -1
                val pathIndex =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                    } else {
                        @Suppress("DEPRECATION")
                        cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    }

                while (cursor.moveToNext()) {
                    val dateAdded = if (dateIndex >= 0) cursor.getLong(dateIndex) else since
                    if (dateAdded > maxDateAdded) maxDateAdded = dateAdded

                    // pending 记录所属进程还没发布，等它发布后重查。
                    if (isPendingIndex >= 0 && cursor.getInt(isPendingIndex) == 1) {
                        hasPending = true
                        continue
                    }

                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val rawMime = if (mimeIndex >= 0) cursor.getString(mimeIndex) else null
                    val mime = resolveMime(name, rawMime)
                    if (mime == null || !isDocMime(mime)) {
                        AppLogs.dLog(NFControl.TAG, "跳过非文档 name=$name mime=$rawMime")
                        continue
                    }

                    val key = if (pathIndex >= 0) {
                        val relativePath = cursor.getString(pathIndex)
                        mediaFilePath(relativePath, name)?.let(::normalizePath)
                            ?: "media:${if (idIndex >= 0) cursor.getLong(idIndex) else name}"
                    } else {
                        "media:${if (idIndex >= 0) cursor.getLong(idIndex) else name}"
                    }
                    if (!markNotified(key)) {
                        AppLogs.dLog(NFControl.TAG, "文档已通知过，忽略: $name")
                        continue
                    }
                    newDocs.add(name to mime)
                }
            }
        } catch (e: Exception) {
            AppLogs.eLog(NFControl.TAG, "查询新文档失败: ${e.message}")
            return
        }

        // 同一秒入库的记录可能有多条，保留 1 秒重叠，重复的靠 notifiedKeys 去重。
        if (maxDateAdded > baselineDateAdded) {
            baselineDateAdded = (maxDateAdded - 1).coerceAtLeast(baselineDateAdded)
        }

        if (newDocs.isNotEmpty()) {
            // 一批文件同时落盘时，只对最新的一个发通知，避免连续弹多条。
            val (name, mime) = newDocs.last()
            AppLogs.dLog(NFControl.TAG, "媒体库发现 ${newDocs.size} 个新文档 latest=$name mime=$mime")
            notifyNewDoc(name, mime, "media_store")
        } else if (hasPending && pendingRetryCount < MAX_PENDING_RETRY && !pendingRetryScheduled) {
            pendingRetryCount++
            pendingRetryScheduled = true
            AppLogs.dLog(
                NFControl.TAG,
                "媒体库存在 pending 记录，${PENDING_RETRY_DELAY_MS}ms 后重试 ${pendingRetryCount}/$MAX_PENDING_RETRY"
            )
            handler.postDelayed({
                pendingRetryScheduled = false
                observerScope.launch { checkNewDocsInMediaStore() }
            }, PENDING_RETRY_DELAY_MS)
        }
    }

    private fun notifyNewDoc(name: String?, mime: String, source: String) {
        AppLogs.dLog(NFControl.TAG, "新增文档 source=$source name=$name mime=$mime")
        handler.post {
            runCatching {
                NotifyShow.showNF(
                    NFEnum.NF_NEW_FILE,
                    data = NFData().apply {
                        title = name?:""
                    },
                    from = "notifyNewDoc"
                )
                MyAPP.resetLiveData.postValue("resetAll")
            }.onFailure {
                AppLogs.eLog(NFControl.TAG, "新文档通知失败: ${it.message}")
            }
        }
    }

    /**
     * MIME_TYPE 在媒体库插入早期可能为空，此时用扩展名兜底。
     */
    private fun resolveMime(name: String?, mime: String?): String? {
        val pure = mime?.split(";")?.firstOrNull()?.trim()?.lowercase()
        if (!pure.isNullOrEmpty() && pure != "application/octet-stream") return pure
        val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (extension.isEmpty()) return pure
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: pure
    }

    private fun mediaFilePath(relativePath: String?, name: String?): String? {
        if (relativePath.isNullOrEmpty() || name.isNullOrEmpty()) return null
        val prefix = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
        return File(Environment.getExternalStorageDirectory(), prefix + name).absolutePath
    }

    private fun normalizePath(path: String): String = path.lowercase(Locale.ROOT)

    private fun hasSupportedExtension(name: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension.isEmpty()) return false
        return isDocMime(MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension))
    }

    private fun markNotified(key: String): Boolean = synchronized(notifiedKeys) {
        if (notifiedKeys.containsKey(key)) return false
        notifiedKeys[key] = true
        while (notifiedKeys.size > MAX_REMEMBERED_FILES) {
            val iterator = notifiedKeys.keys.iterator()
            if (!iterator.hasNext()) break
            iterator.next()
            iterator.remove()
        }
        true
    }

    private fun eventName(event: Int): String = when (event and FileObserver.ALL_EVENTS) {
        FileObserver.CREATE -> "CREATE"
        FileObserver.CLOSE_WRITE -> "CLOSE_WRITE"
        FileObserver.MOVED_TO -> "MOVED_TO"
        else -> event.toString()
    }

    private fun isDocMime(mime: String?): Boolean {
        return when (mime) {
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain" -> true
            else -> false
        }
    }
}
