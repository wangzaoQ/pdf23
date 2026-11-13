package com.pdf.editor.cache

import com.pdf.editor.common.nf.NFEnum

import com.pdf.editor.BuildConfig
import com.pdf.editor.common.config.UserLevel
import com.pdf.editor.common.tools.DataUtils
import com.pdf.editor.common.tools.ScanConfig
import com.pdf.editor.data.CacheCollect
import com.pdf.editor.data.FilesData
import com.tencent.mmkv.MMKV

object LocalDataManager {
    const val TAG = "LocalDataManager"

    val mmkv = MMKV.mmkvWithID("${BuildConfig.APPLICATION_ID}kv", MMKV.MULTI_PROCESS_MODE)

    var cacheLanguage: String
        get() {
            return mmkv.decodeString(CacheKey.KV_LANGUAGE,"")?:""
        }
        set(value) {
            mmkv.encode(CacheKey.KV_LANGUAGE, value)
        }


    var cacheLanguageName: String
        get() {
            return mmkv.decodeString(CacheKey.KV_LANGUAGE_NAME,"")?:""
        }
        set(value) {
            mmkv.encode(CacheKey.KV_LANGUAGE_NAME, value)
        }

    fun getSortTypeByFromType(fromType: Int): String {
        return mmkv.decodeString("${CacheKey.KV_SORT_TYPE}_${fromType}")?: ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST
    }

    fun setSortTypeByFromType(fromType: Int,sortType: String): Boolean {
        return mmkv.encode("${CacheKey.KV_SORT_TYPE}_${fromType}",sortType)
    }

    var cacheShowGuide: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_GUIDE,true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_GUIDE, value)
        }
    var firstShowNF: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_FIRST_SHOW_NF,true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_FIRST_SHOW_NF, value)
        }

    var isFirstPermissionPoint: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_IS_FIRST_PERMISSION_POINT,true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_IS_FIRST_PERMISSION_POINT, value)
        }
    /** -1 未完成，0 不允许，1 允许。 */
    var ipAllow: Int
        get() = mmkv.decodeInt(CacheKey.KV_IP_ALLOW, -1)
        set(value) { mmkv.encode(CacheKey.KV_IP_ALLOW, value) }

    var startShowAIGuide: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_FIRST_START_SHOW_AI_GUIDE,true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_FIRST_START_SHOW_AI_GUIDE, value)
        }

    var firstHome: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_FIRST_HOME,true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_FIRST_HOME, value)
        }

    var isRiskyUser : Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_FIRST_HOME,true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_FIRST_HOME, value)
        }

    var cacheLanguageFirst: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_LANGUAGE_FIRST,true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_LANGUAGE_FIRST, value)
        }

    var cacheGuideFirst: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_GUIDE_FIRST,true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_GUIDE_FIRST, value)
        }


    var cacheFirstShowPdfSetting: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_FIRST_SHOW_DEFAULT_PDF_SETTING,true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_FIRST_SHOW_DEFAULT_PDF_SETTING, value)
        }
    var cacheFirstShowScanGuide: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_FIRST_SHOW_SCAN_GUIDE,true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_FIRST_SHOW_SCAN_GUIDE, value)
        }
    var cachePrivacyMode: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_PRIVACY_MODE, true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_PRIVACY_MODE, value)
        }
    var cacheFirstShowOpenAD: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_FIRST_SHOW_OPEN_AD,true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_FIRST_SHOW_OPEN_AD, value)
        }
    var lastFilePermissionType: Int
        get() {
            return mmkv.decodeInt("last_file_permission_type", 0)
        }
        set(value) {
            mmkv.encode("last_file_permission_type", value)
        }
    var lastNFPermissionType: Int
        get() {
            return mmkv.decodeInt("last_nf_permission_type", 0)
        }
        set(value) {
            mmkv.encode("last_nf_permission_type", value)
        }
    var isBUser: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_IS_BY_USER,false)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_IS_BY_USER, value)
        }
    var isFirstShowFilePermission: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_IS_FIST_SHOW_FILE_PERMISSION,true)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_IS_FIST_SHOW_FILE_PERMISSION, value)
        }
    var lastUserType: Int
        get() {
            return mmkv.decodeInt(CacheKey.KV_LAST_USER_TYPE,0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_LAST_USER_TYPE, value)
        }
    var cacheTimePushIndex: Int
        get() {
            return mmkv.decodeInt(CacheKey.KV_TIME_PUSH_INDEX,0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_TIME_PUSH_INDEX, value)
        }

    var nfTime1: Long
        get() {
            return mmkv.decodeLong(CacheKey.KV_NF_TIME_1,30L)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_NF_TIME_1, value)
        }

    var nfTime2: Long
        get() {
            return mmkv.decodeLong(CacheKey.KV_NF_TIME_2,39L)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_NF_TIME_2, value)
        }

    var nfTime3: Long
        get() {
            return mmkv.decodeLong(CacheKey.KV_NF_TIME_3,59L)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_NF_TIME_3, value)
        }

   var lastMediaPopTime: Long
        get() {
            return mmkv.decodeLong(CacheKey.KV_LAST_MEDIA_POP_TIME,0L)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_LAST_MEDIA_POP_TIME, value)
        }

    var nfTimeConfigInitialized: Boolean
        get() {
            return mmkv.decodeBool(CacheKey.KV_NF_TIME_CONFIG_INITIALIZED,false)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_NF_TIME_CONFIG_INITIALIZED, value)
        }

    var nfCount: Int
        get() {
            return mmkv.decodeInt(CacheKey.KV_NF_COUNT,0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_NF_COUNT, value)
        }

    var cacheUnlockPushIndex: Int
        get() {
            return mmkv.decodeInt(CacheKey.KV_UNLOCK_PUSH_INDEX,0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_UNLOCK_PUSH_INDEX, value)
        }
    var clickEveryDay: Int
        get() {
            return mmkv.decodeInt(CacheKey.KV_AD_CLICK_DAY,0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_AD_CLICK_DAY, value)
        }
    var showEveryDay: Int
        get() {
            return mmkv.decodeInt(CacheKey.KV_AD_SHOW_DAY,0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_AD_SHOW_DAY, value)
        }

    /** 当天开屏广告点击次数，不与其他广告类型共用。 */
    var openAdClickEveryDay: Int
        get() {
            return mmkv.decodeInt(CacheKey.KV_OPEN_AD_CLICK_DAY, 0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_OPEN_AD_CLICK_DAY, value)
        }

    /** 当天开屏广告实际展示次数，不与其他广告类型共用。 */
    var openAdShowEveryDay: Int
        get() {
            return mmkv.decodeInt(CacheKey.KV_OPEN_AD_SHOW_DAY, 0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_OPEN_AD_SHOW_DAY, value)
        }

    var lastUnlockPushTime: Long
        get() {
            return mmkv.decodeLong(CacheKey.KV_UNLOCK_PUSH_TIME,0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_UNLOCK_PUSH_TIME, value)
        }

    var collectString : String
        get() {
            return mmkv.decodeString(CacheKey.KV_FILE_COLLECT,"")?:""
        }
        set(value) {
            mmkv.encode(CacheKey.KV_FILE_COLLECT, value)
        }
    var fileSort : String
        get() {
            return mmkv.decodeString(CacheKey.KV_SORT_TYPE,ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST)?:ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST
        }
        set(value) {
            mmkv.encode(CacheKey.KV_SORT_TYPE, value)
        }
    var collectSort : String
        get() {
            return mmkv.decodeString(CacheKey.KV_COLLECT_SORT,ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST)?:ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST
        }
        set(value) {
            mmkv.encode(CacheKey.KV_COLLECT_SORT, value)
        }
    var deviceId: String
        get() {
            return mmkv.decodeString(CacheKey.KV_DEVICE_ID,"")?:""
        }
        set(value) {
            mmkv.encode(CacheKey.KV_DEVICE_ID, value)
        }

    var GID: String
        get() {
            return mmkv.decodeString(CacheKey.KV_GID,"")?:""
        }
        set(value) {
            mmkv.encode(CacheKey.KV_GID, value)
        }

    var webUA: String
        get() {
            return mmkv.decodeString(CacheKey.KV_WEB_UA,"")?:""
        }
        set(value) {
            mmkv.encode(CacheKey.KV_WEB_UA, value)
        }

    var installRefer: String
        get() {
            return mmkv.decodeString(CacheKey.KV_INSTALL_REFER,"")?:""
        }
        set(value) {
            mmkv.encode(CacheKey.KV_INSTALL_REFER, value)
        }
    var installReferNet: String
        get() {
            return mmkv.decodeString(CacheKey.KV_INSTALL_REFER_NET, "") ?: ""
        }
        set(value) {
            mmkv.encode(CacheKey.KV_INSTALL_REFER_NET, value)
        }
    var dayString: String
        get() {
            return mmkv.decodeString(CacheKey.KV_DAY_STRING,"")?:""
        }
        set(value) {
            mmkv.encode(CacheKey.KV_DAY_STRING, value)
        }
    var lastIn: Long
        get() {
            return mmkv.decodeLong(CacheKey.KV_TIME_LAST_IN)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_TIME_LAST_IN, value)
        }
    var afFrom: String
        get() {
            return mmkv.decodeString(CacheKey.KV_AF_FROM,"Organic")?:"Organic"
        }
        set(value) {
            mmkv.encode(CacheKey.KV_AF_FROM, value)
        }
    var adUserType: Int
        get() {
            return mmkv.decodeInt(CacheKey.KV_AD_USER_TYPE,0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_AD_USER_TYPE, value)
        }


    var ad001 :Double
        get() {
            return mmkv.decodeDouble(CacheKey.KV_AD_TOTAL_001,0.0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_AD_TOTAL_001, value)
        }
    var adDayValue :Double
        get() {
            return mmkv.decodeDouble(CacheKey.KV_AD_DAY_VALUE,0.0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_AD_DAY_VALUE, value)
        }
    var adDayEcpm :Long
        get() {
            return mmkv.decodeLong(CacheKey.KV_AD_DAY_ECPM,0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_AD_DAY_ECPM, value)
        }

    var last_kc_cd_time:Long
        get() {
            return mmkv.decodeLong(CacheKey.KV_LAST_KC_CD_TIME,0)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_LAST_KC_CD_TIME, value)
        }
    //ai用
    var recentFileData: FilesData?
        get() {
            return DataUtils.parseBeanByGson(mmkv.decodeString(CacheKey.KV_RECENT_FILES,""),
                FilesData::class.java)
        }
        set(value) {
            mmkv.encode(CacheKey.KV_RECENT_FILES, DataUtils.toJson(value))
        }

    fun saveRecentOpenFiles(data: FilesData) {
        val recentLit = getRecentOpenFiles()
        recentLit.removeAll { isSameRecentFile(it, data) }
        recentLit.add(0,data)
        mmkv.encode(CacheKey.KV_RECENT_FILES_OPEN, DataUtils.toJson(recentLit))
    }

    /**
     * 文件改名后同步更新所有依赖“旧路径 / 旧 fileId”的缓存数据。
     *
     * 最近打开列表、单个 recentFileData、收藏列表都不是重新扫描时自动修正的，
     * 如果这里不迁移，界面会出现文件名已变更，但最近列表或收藏仍然引用旧路径的情况。
     */
    fun updateRenamedFile(oldFilePath: String, data: FilesData) {
        val recentList = getRecentOpenFiles()
        var recentChanged = false
        recentList.forEach { item ->
            if (isSamePath(item.filePath, oldFilePath)) {
                item.fileName = data.fileName
                item.filePath = data.filePath
                item.fileSize = data.fileSize
                item.fileTime = data.fileTime
                item.fileType = data.fileType
                item.fileId = data.fileId
                item.fileImg = data.fileImg
                recentChanged = true
            }
        }
        if (recentChanged) {
            mmkv.encode(CacheKey.KV_RECENT_FILES_OPEN, DataUtils.toJson(recentList))
        }

        recentFileData?.let { item ->
            if (isSamePath(item.filePath, oldFilePath)) {
                item.fileName = data.fileName
                item.filePath = data.filePath
                item.fileSize = data.fileSize
                item.fileTime = data.fileTime
                item.fileType = data.fileType
                item.fileId = data.fileId
                item.fileImg = data.fileImg
                recentFileData = item
            }
        }

        // 收藏里只存 fileId。由于 fileId 依赖路径生成，改名后必须把旧 id 替换成新 id。
        val collectData = DataUtils.parseBeanByGson(collectString, CacheCollect::class.java) ?: CacheCollect()
        val collectIndex = collectData.collectList.indexOfFirst { it == data.fileId }
        val oldCollectIndex = collectData.collectList.indexOfFirst { it == getFileIdByPath(oldFilePath) }
        if (oldCollectIndex >= 0) {
            collectData.collectList[oldCollectIndex] = data.fileId
        } else if (collectIndex >= 0) {
            collectData.collectList[collectIndex] = data.fileId
        }
        collectString = DataUtils.toJson(collectData)
    }

    /**
     * openTime 也是按 fileId 分桶存储的。
     *
     * 重命名后如果不把旧 fileId 对应的打开时间迁到新 fileId，
     * 列表重新排序时会把这个文件当成“从没打开过”的新文件。
     */
    fun migrateOpenTime(oldFileId: String, newFileId: String, openTime: Long) {
        if (openTime > 0L) {
            setOpenTime(newFileId, openTime)
        }
        if (oldFileId.isNotEmpty() && oldFileId != newFileId) {
            mmkv.removeValueForKey("open_$oldFileId")
        }
    }

    fun migrateFilePage(oldFileId: String, newFileId: String) {
        if (newFileId.isNotEmpty()) {
            setFilePage(newFileId, getFilePage(oldFileId))
        }
        if (oldFileId.isNotEmpty() && oldFileId != newFileId) {
            mmkv.removeValueForKey(CacheKey.KV_FILE_PAGE_PREFIX + oldFileId)
        }
    }

    fun removeDeletedFile(data: FilesData) {
        val recentList = getRecentOpenFiles()
        val removedRecent = recentList.removeAll { item ->
            isSamePath(item.filePath, data.filePath) || (item.fileId.isNotEmpty() && item.fileId == data.fileId)
        }
        if (removedRecent) {
            mmkv.encode(CacheKey.KV_RECENT_FILES_OPEN, DataUtils.toJson(recentList))
        }

        recentFileData?.let { item ->
            if (isSamePath(item.filePath, data.filePath) || (item.fileId.isNotEmpty() && item.fileId == data.fileId)) {
                recentFileData = null
            }
        }

        val collectData = DataUtils.parseBeanByGson(collectString, CacheCollect::class.java) ?: CacheCollect()
        if (collectData.collectList.removeAll { it == data.fileId }) {
            collectString = DataUtils.toJson(collectData)
        }

        if (data.fileId.isNotEmpty()) {
            mmkv.removeValueForKey("open_${data.fileId}")
            mmkv.removeValueForKey(CacheKey.KV_FILE_PAGE_PREFIX + data.fileId)
            mmkv.removeValueForKey(chatHistoryKey(data.fileId))
        }
    }

    fun getRecentOpenFiles(): MutableList<FilesData> {
        val json = mmkv.decodeString(CacheKey.KV_RECENT_FILES_OPEN, "") ?: ""
        val list = DataUtils.getListByGson(json, FilesData::class.java) ?: mutableListOf()
        val distinctList = mutableListOf<FilesData>()
        list.forEach { item ->
            if (distinctList.none { isSameRecentFile(it, item) }) {
                distinctList.add(item)
            }
        }
        return distinctList
    }

    private fun isSameRecentFile(left: FilesData, right: FilesData): Boolean {
        val leftPath = left.filePath.trim().lowercase()
        val rightPath = right.filePath.trim().lowercase()
        if (leftPath.isNotEmpty() && rightPath.isNotEmpty()) return leftPath == rightPath

        val leftFileId = left.fileId.trim()
        val rightFileId = right.fileId.trim()
        return leftFileId.isNotEmpty() && leftFileId == rightFileId
    }

    private fun isSamePath(leftPath: String, rightPath: String): Boolean {
        val left = leftPath.trim().lowercase()
        val right = rightPath.trim().lowercase()
        return left.isNotEmpty() && left == right
    }

    // 收藏缓存里只有 fileId，没有原路径，所以这里用同一套规则反推旧路径对应的旧 fileId。
    private fun getFileIdByPath(filePath: String): String {
        return runCatching {
            val digest = java.security.MessageDigest.getInstance("MD5")
                .digest(filePath.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        }.getOrDefault(filePath)
    }

    private fun chatHistoryKey(docId: String): String {
        return CacheKey.KV_CHAT_HISTORY_PREFIX + docId
    }

//    fun saveChatHistory(docId: String, list: List<ChatData>) {
//        mmkv.encode(chatHistoryKey(docId), DataUtils.toJson(list))
//    }
//
//    fun getChatHistory(docId: String): MutableList<ChatData> {
//        val json = mmkv.decodeString(chatHistoryKey(docId), "") ?: ""
//        return DataUtils.getListByGson(json, ChatData::class.java) ?: mutableListOf()
//    }
    fun updateCollect(fileData: FilesData){
        var data = DataUtils.parseBeanByGson(collectString, CacheCollect::class.java)
        if (data == null){
            data = CacheCollect()
        }
        var selectedID =""
        for (id in data.collectList) {
            if (id == fileData.fileId){
                selectedID = id
                break
            }
        }
        if (selectedID.isNullOrEmpty()){
            //未收藏过
            data.collectList.add(fileData.fileId)
        }else{
            data.collectList.remove(selectedID)
        }
        collectString = DataUtils.toJson(data)
    }


    fun setOpenTime(fileId: String, time: Long) {
        mmkv.encode("open_$fileId", time)
    }

    fun getOpenTime(fileId: String): Long {
        return mmkv.decodeLong("open_$fileId", 0L)
    }

    fun setFilePage(fileId: String, page: Int) {
        mmkv.encode(CacheKey.KV_FILE_PAGE_PREFIX + fileId, page.coerceAtLeast(1))
    }

    fun getFilePage(fileId: String): Int {
        return mmkv.decodeInt(CacheKey.KV_FILE_PAGE_PREFIX + fileId, 1).coerceAtLeast(1)
    }

    fun getAlarmTime(alarmAction: String): Long {
        return mmkv.decodeLong("time_$alarmAction", 0L)
    }

    fun setAlarmTime(alarmAction: String, time: Long){
        mmkv.encode("time_$alarmAction", time)
    }

    var allowToSystem: Boolean
        get() = mmkv.decodeBool(CacheKey.KV_ALLOW_TO_SYSTEM, true)
        set(value) { mmkv.encode(CacheKey.KV_ALLOW_TO_SYSTEM, value) }

    var userTimeLimit: Int
        get() = mmkv.decodeInt(CacheKey.KV_USER_TIME_LIMIT, -1)
        set(value) { mmkv.encode(CacheKey.KV_USER_TIME_LIMIT, value) }

    var userTestAdLimit: Int
        get() = mmkv.decodeInt(CacheKey.KV_USER_TEST_AD_LIMIT, -1)
        set(value) { mmkv.encode(CacheKey.KV_USER_TEST_AD_LIMIT, value) }

    var userTestLabLimit: Int
        get() = mmkv.decodeInt(CacheKey.KV_USER_TEST_LAB_LIMIT, -1)
        set(value) { mmkv.encode(CacheKey.KV_USER_TEST_LAB_LIMIT, value) }

    var lastTimePushAll: Long
        get() = mmkv.decodeLong(CacheKey.KV_LAST_TIME_PUSH_ALL, 0L)
        set(value) { mmkv.encode(CacheKey.KV_LAST_TIME_PUSH_ALL, value) }

    var lastScreenImpressionTime: Long
        get() = mmkv.decodeLong(CacheKey.KV_AD_SCREEN_LAST_IMPRESSION_TIME, 0L)
        set(value) { mmkv.encode(CacheKey.KV_AD_SCREEN_LAST_IMPRESSION_TIME, value) }

    fun getLastPushTimeByEnum(enum: NFEnum):Long {
        val key = if (enum == NFEnum.NF_TIMER_1) {
            "time"
        } else if (enum == NFEnum.NF_NEWS_FCM) {
            "fcm"
        } else {
            "broadcast"
        }
        return mmkv.decodeLong("push_time_${key}", 0L)
    }
    fun savePushTimeByEnum(enum: NFEnum) {
        val key = if (enum == NFEnum.NF_TIMER_1) {
            "time"
        } else if (enum == NFEnum.NF_NEWS_FCM) {
            "fcm"
        } else {
            "broadcast"
        }
        mmkv.encode("push_time_${key}", System.currentTimeMillis())
    }


    fun getNfCountByEnum(enum: NFEnum):Int {
        val key = if (enum == NFEnum.NF_TIMER_1){
            "time"
        }else if (enum == NFEnum.NF_NEWS_FCM){
            "fcm"
        }else{
            "broadcast"
        }
        return mmkv.decodeInt("nf_count_${key}", 0)
    }

    fun saveNfCountByEnum(enum: NFEnum,count: Int) {
        val key = if (enum == NFEnum.NF_TIMER_1){
            "time"
        }else if (enum == NFEnum.NF_NEWS_FCM){
            "fcm"
        }else{
            "broadcast"
        }
        mmkv.encode("nf_count_${key}", count)
    }
    var userLevel: Int
        get() = mmkv.decodeInt(CacheKey.KV_USER_LEVEL, UserLevel.USER_LEVEL_1.level)
        set(value) { mmkv.encode(CacheKey.KV_USER_LEVEL, value) }
}
