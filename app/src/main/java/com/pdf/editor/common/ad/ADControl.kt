package com.pdf.editor.common.ad

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.nativead.NativeAd
import com.pdf.editor.MyAPP
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.ad.loader.AdmobLoader
import com.pdf.editor.common.config.FirebaseManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.appDataReset
import com.pdf.editor.data.ADData
import com.pdf.editor.data.ADLimitConfig
import com.pdf.editor.data.ADRequestData
import com.pdf.editor.data.ADResultData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

object ADControl {

    var lastScreenDismissTime = 0L
    const val AD_PLATFORM_ADMOB = "admob"
    const val AD_PLATFORM_TOPON = "topon"

    const val AD_TYPE_OPEN = "open"
    const val AD_TYPE_INT = "int"
    const val AD_TYPE_NATIVE = "nat"
    const val AD_TYPE_BANNER = "ban"
    const val AD_TYPE_RV = "rv"

    const val AD_SHOW_TYPE_SUCCESS = "AD_SHOW_TYPE_SUCCESS"
    const val AD_SHOW_TYPE_FAILED = "AD_SHOW_TYPE_FAILED"

    var adUserType = 0
    //true从app跳转其他页面返回不展示广告
    var goOther = false
    var goBackType = ""
    //StartActivity 是否正在启动如果启动为false
    @Volatile
    var fullScreenAdShowing = false
    var languageAllow = true
    var showScreenNative = false
    var showNativeScreenRandom = 10

    var width:Int = 0


    var TAG = "ADControl"

    // 每个广告位保存一个库存池，展示时再按 ipn 取最高优先级。
    var adCache: HashMap<ADEnum, MutableList<ADResultData>> = HashMap()

    // 防止同一广告位在已有库存请求未结束时重复开新 session。
    private val loadingAdSet = mutableSetOf<ADEnum>()

    var adRootBean: ADData? = null

    var allowAds: Boolean = false

    /** 0: one launch request; 1: retry during the loading animation. */
    var newRequestConfig = 0
    var cd_hot: Int? = null
    var new_op_cd: Int? = null
    var kc_cd: Int? = null
    var a_kc_cd: Int? = null
    var adLimitConfig: ADLimitConfig? = null
    var adSwitchData: com.pdf.editor.data.AdSwitchData? = null

    fun isAdPointEnabled(pointTag: String): Boolean = adSwitchData?.isEnabled(pointTag) ?: true

    fun isOpenAd(adEnum: ADEnum): Boolean =
        adEnum == ADEnum.OPEN_AD || adEnum == ADEnum.OPEN_AD_ONLY

    /**
     * 判断开屏广告是否达到频控限制。
     *
     * 这里只判断当天展示数和点击数。
     */
    fun isOpenAdFrequencyLimitReached(logTag: String): Boolean {
        appDataReset()
        var limitReached = false
        val limitConfig = adLimitConfig
        if (limitConfig != null) {
            var showLimit = 100
            val configuredShowLimit = limitConfig.adShow
            if (configuredShowLimit != null) {
                showLimit = configuredShowLimit
            }
            if (showLimit < 0) {
                showLimit = 0
            }

            var clickLimit = 30
            val configuredClickLimit = limitConfig.adClick
            if (configuredClickLimit != null) {
                clickLimit = configuredClickLimit
            }
            if (clickLimit < 0) {
                clickLimit = 0
            }

            val showLimitReached = LocalDataManager.openAdShowEveryDay >= showLimit
            val clickLimitReached = LocalDataManager.openAdClickEveryDay >= clickLimit
            limitReached = showLimitReached || clickLimitReached
            AppLogs.dLog(
                TAG,
                "开屏频控限制详情:$logTag show:${LocalDataManager.openAdShowEveryDay}/$showLimit " +
                    "click:${LocalDataManager.openAdClickEveryDay}/$clickLimit"
            )
            if (limitReached) {
                AppLogs.dLog(TAG, "开屏触发每日频控限制:$logTag")
            }
        }

        return limitReached
    }


    @Synchronized
    fun clearShownOpenAdCache() {
        listOf(ADEnum.OPEN_AD_ONLY, ADEnum.OPEN_AD).forEach { adEnum ->
            val cacheList = adCache[adEnum] ?: return@forEach
            cacheList.removeAll { it.adShowReported.get() }
            if (cacheList.isEmpty()) adCache.remove(adEnum)
        }
    }

    @Volatile
    var admobInitialized = false


    // 0 表示不做超时切层，保持失败后再请求下一层。
    var adLoadTime = 0

    @Volatile
    var adSource = ""

    //广告有CD 里面有A有B
    var interceptNeedADLimit = listOf(
        PointConfig.AD_POINT_READ_BACK,
        PointConfig.AD_POINT_INT1,
        PointConfig.AD_POINT_INT2,
        PointConfig.AD_POINT_USE_END
    )

    //只有B包可以展示
    var interceptPointList_B = listOf(
        PointConfig.AD_POINT_READ_BACK,
        PointConfig.AD_POINT_UNLOAD1,
        PointConfig.AD_POINT_UNLOAD2,
        PointConfig.AD_POINT_LAN_INT,
        PointConfig.AD_POINT_GUIDE_FULL,
        PointConfig.AD_POINT_GUIDE_INT,
        //native
        PointConfig.AD_POINT_GUIDE1,
        PointConfig.AD_POINT_GUIDE2,
        PointConfig.AD_POINT_BANNER1,
        PointConfig.AD_POINT_BANNER2,
        PointConfig.AD_POINT_UNLOAD_NAT1,
    )

    //AB包可以展示
    var interceptPointList_AB = listOf(
        PointConfig.AD_POINT_INT1, PointConfig.AD_POINT_INT2, PointConfig.AD_POINT_USE_END
    )


    var refuseContinueLoadList = mutableListOf<String>(ADEnum.OPEN_AD_ONLY.adName,)


    fun initADPlatform(){
        AppLogs.dLog(MyAPP.instance.TAG,"admob init start")
        MobileAds.initialize(MyAPP.instance){
            admobInitialized = true
            EventManager.posePoint(PointConfig.sdk_initialization, Bundle().apply {
                putString(PointKey.ad_source_client,AD_PLATFORM_ADMOB)
            })
            AppLogs.dLog(MyAPP.instance.TAG,"admob init complete")
        }
    }

    fun updateADConfig(){
        val root = adRootBean
        if (root == null){
            AppLogs.dLog(MyAPP.instance.TAG,"updateADConfig error adRootBean == null")
            return
        }
        ADEnum.entries.forEach {
            when (it) {
                ADEnum.OPEN_AD_ONLY -> {
                    it.adRequestList = root.pr_new_launch ?: mutableListOf()
                }
                ADEnum.OPEN_AD -> {
                    it.adRequestList = root.pr_launch ?: mutableListOf()
                }

                ADEnum.BANNER_AD_1 -> {
                    it.adRequestList = root.pr_ban1 ?: mutableListOf()
                }
                ADEnum.BANNER_AD_2 -> {
                    it.adRequestList = root.pr_ban2 ?: mutableListOf()
                }
                ADEnum.INT_AD_USER -> {
                    it.adRequestList = root.pr_user_use ?: mutableListOf()
                }
                ADEnum.INT_AD_EXIT -> {
                    it.adRequestList = root.pr_exit ?: mutableListOf()
                }

                ADEnum.INT_AD_NEW  -> {
                    it.adRequestList = root.pr_new_int ?: mutableListOf()
                }
            }
            it.adRequestList = it.adRequestList.filterNotNull()
                .sortedByDescending { item -> item.ipn }
                .toMutableList()

        }
    }


    @Synchronized
    fun getCacheAD(enum: ADEnum): ADResultData? {
        pruneInvalidCache(enum)
        return adCache[enum]?.maxByOrNull { it.adRequestData?.ipn ?: Int.MIN_VALUE }
    }

    // 只有真正进入展示流程时才消费库存；轮询库存继续用 getCacheAD。
    @Synchronized
    fun takeCacheAD(enum: ADEnum): ADResultData? {
        pruneInvalidCache(enum)
        val cacheList = adCache[enum] ?: return null
        val bestAd = cacheList.maxByOrNull { it.adRequestData?.ipn ?: Int.MIN_VALUE } ?: return null
        cacheList.remove(bestAd)
        if (cacheList.isEmpty()) {
            adCache.remove(enum)
        }
        AppLogs.dLog(
            TAG,
            "展示取库存 takeCacheAD enum:${enum.adName} ipn:${bestAd.adRequestData?.ipn} id:${bestAd.adRequestData?.jsk} 剩余库存:${cacheList.size}"
        )
        return bestAd
    }

    @Synchronized
    fun hasCacheAD(enum: ADEnum): Boolean {
        return getCacheAD(enum) != null
    }

    fun clearAllAD(){
        // 先保存待释放的广告对象，再清空库存。
        // 不能只调用 adCache.clear()：NativeAd 持有图片、视频等原生资源，
        // 从集合中移除引用并不会主动释放这些资源，必须额外调用 NativeAd.destroy()。
        val cachedAds = mutableListOf<ADResultData>()
        synchronized(this) {
            // 这里只在锁内复制库存引用并清空集合，避免其他线程在遍历过程中修改 adCache。
            // NativeAd.destroy() 不放在锁内执行，防止资源释放耗时导致其他广告缓存操作长时间等待。
            for (cacheList in adCache.values) {
                for (adResultData in cacheList) {
                    cachedAds.add(adResultData)
                }
            }
            adCache.clear()
        }
        // 库存已经清空，此时逐个释放原生广告资源。
        for (adResultData in cachedAds) {
            releaseCachedNativeAd(adResultData)
        }
    }

    @Synchronized
    fun saveCacheAD(enum: ADEnum, mcadpBean: ADResultData) {
        if (mcadpBean.adRequestData == null || mcadpBean.adAny == null) {
            // 请求配置或广告对象缺失时不能入库。
            // 如果 adAny 实际上已经是一个 NativeAd，仍需在丢弃前主动释放，避免资源泄漏。
            releaseCachedNativeAd(mcadpBean)
            return
        }
        val cacheList = adCache.getOrPut(enum) { mutableListOf() }
        cacheList.add(mcadpBean)
        cacheList.sortByDescending { it.adRequestData?.ipn ?: Int.MIN_VALUE }
        AppLogs.dLog(
            TAG,
            "保存库存 saveCacheAD enum:${enum.adName} ipn:${mcadpBean.adRequestData?.ipn} id:${mcadpBean.adRequestData?.jsk} 当前库存:${cacheList.size}"
        )
    }

    fun preloadAD(enum: ADEnum, tag: String = "") {
        appDataReset()
        FirebaseManager.refreshLatestADConfig()
        AppLogs.dLog(TAG, "预加载位置:${tag} 加载类型:${enum.adName}")
        if (!allowAds){
            AppLogs.dLog(TAG, "UMP未通过")
            return
        }
        // 开屏预加载只检查每日展示次数和点击次数。
        if (isOpenAd(enum) && isOpenAdFrequencyLimitReached("preloadAD")) {
            AppLogs.dLog(TAG, "开屏广告频控拦截预加载:${enum.adName}")
            return
        }
        // 所有补加载都走这里；只要还有库存，就不再发起新请求。
        if (hasCacheAD(enum)) {
            AppLogs.dLog(TAG, "库存未空，跳过补加载:${enum.adName} tag:${tag}")
            return
        }
        if (isLoading(enum)) {
            AppLogs.dLog(TAG, "当前广告正在加载:${enum.adName}")
            return
        }
//        var isBUser = LocalDataManager.isBUser
//        if (interceptPointList.contains(enum.adName) && !isBUser){
//            AppLogs.dLog(TAG, "当前广告只允许买量用户展示:${enum.adName}")
//            return
//        }
//        NewsAPP.singleApp.showToast("load:${enum}_size:${enum.originalList.size}")
        // 一个 ADLoadSession 代表同一个广告位的一轮补库存请求。
        // 这轮里可能因为超时同时存在多个在途层级请求。
        val session = ADLoadSession(enum)
        synchronized(this) {
            // session 创建后立即标记广告位加载中，后续 preloadAD 会被挡住。
            loadingAdSet.add(enum)
        }
        AppLogs.dLog(TAG, "请求轮次开始 adSession start sid:${session.id} enum:${enum.adName} 层级数:${enum.adRequestList.size}")
        load(session, mutableListOf<ADRequestData>().apply { addAll(enum.adRequestList) })
    }

    // 一个 session 内按 ipn 顺序请求；失败走下一层，超时则并行启动下一层。
    private fun load(session: ADLoadSession, list: MutableList<ADRequestData>) {
        val adEnum = session.adEnum
        // session 关闭后不再开启新层级；晚到回调只能收尾或入库存。
        if (session.closed) {
            AppLogs.dLog(TAG, "请求轮次已关闭 adSession skip sid:${session.id} enum:${adEnum.adName} 不再开启新层")
            return
        }
        val data = list.removeFirstOrNull()
        if (data != null) {
            AppLogs.dLog(
                TAG,
                "开始请求层级 adSession request sid:${session.id} enum:${adEnum.adName} ipn:${data.ipn} id:${data.jsk} type:${data.iwn} platform:${data.iwk} 剩余层级:${list.size} grp:${data.grp} adUserType:${adUserType}"
            )
            if (!data.grp.contains(0) && !data.grp.contains(adUserType)){
                AppLogs.dLog(
                    TAG,
                    "跳过层级 adSession skipGrp sid:${session.id} enum:${adEnum.adName} ipn:${data.ipn} id:${data.jsk} 原因:grp不匹配"
                )
                load(session, list)
                return
            }
            EventManager.posePoint(PointConfig.ad_request, Bundle().apply {
                putString(PointKey.scene,adEnum.adName)
                putString(PointKey.ad_format,data.iwn)
                putString(PointKey.ad_code_id,data.jsk)
                putString(PointKey.ad_source,adSource)
                putString(PointKey.ad_source_client,data.iwk)
            })
            val adLoader: BaseLoader = AdmobLoader(data, adEnum)
            synchronized(session) {
                // 记录这层请求已发出。超时后即使开启下一层，当前层仍会占一个在途数。
                session.inFlightCount += 1
                AppLogs.dLog(
                    TAG,
                    "请求发出 adSession inFlightAdd sid:${session.id} enum:${adEnum.adName} ipn:${data.ipn} id:${data.jsk} 在途数:${session.inFlightCount}"
                )
            }
            adLoader?.apply {
                // 当前层是否已经回调，避免超时任务在回调后再推进下一层。
                var callbackFinished = false
                // 当前层是否已经因为超时推进过下一层，避免失败回调重复推进。
                var movedNext = false
                var timeoutJob: Job? = null
                AppLogs.dLog(
                    TAG,
                    "sid:${session.id} 广告请求限制时间 adLoadTime:$adLoadTime"
                )
                if (adLoadTime > 0) {
                    // 超时只推进下一层，不丢弃当前层；当前层晚到成功仍会进库存池。
                    AppLogs.dLog(TAG, "超时任务创建 sid:${session.id} adLoadTime:$adLoadTime time:${System.currentTimeMillis()}")
                    timeoutJob = MyAPP.instance.applicationScope.launch {
                        AppLogs.dLog(TAG, "超时任务启动 sid:${session.id} time:${System.currentTimeMillis()}")
                        delay(adLoadTime * 1000L)
                        AppLogs.dLog(TAG, "超时任务唤醒 sid:${session.id} callbackFinished:$callbackFinished hasSuccess:${session.hasSuccess} time:${System.currentTimeMillis()}")
                        var shouldLoadNext = false
                        synchronized(session) {
                            // 只有当前层未回调、且整轮还没有成功库存时，超时才启动下一层。
                            if (!callbackFinished && !session.hasSuccess) {
                                shouldLoadNext = true
                                movedNext = true
                            }
                        }
                        if (shouldLoadNext) {
                            AppLogs.dLog(
                                TAG,
                                "请求超时切层 adSession timeout sid:${session.id} enum:${adEnum.adName} ipn:${data.ipn} id:${data.jsk} 超时秒:${adLoadTime} 在途数:${session.inFlightCount} 说明:当前层不取消,继续等晚到回调"
                            )
                            load(session, list)
                        }
                    }
                }
                setSuccessCall {
                    synchronized(session) {
                        // 当前层成功后，标记整轮已有库存，并关闭 session，停止继续开新层。
                        // 已经发出去的其他层不会取消，晚到成功仍然可以补进库存池。
                        callbackFinished = true
                        session.hasSuccess = true
                        session.closed = true
                        // 当前层回调完成，从在途请求数里扣掉。
                        session.inFlightCount -= 1
                        AppLogs.dLog(
                            TAG,
                            "请求成功 adSession success sid:${session.id} enum:${adEnum.adName} ipn:${data.ipn} id:${data.jsk} 在途数:${session.inFlightCount} 说明:关闭本轮,不再开启新层"
                        )
                    }
                    timeoutJob?.cancel()
                    AppLogs.dLog(
                        TAG,
                        "adLoader:${adLoader.javaClass.simpleName} LoadSuccess:${adEnum}-id:${data.jsk}-time:${it.adRequestTime}"
                    )
                    saveCacheAD(adEnum, it)
                    finishLoadSessionIfIdle(session)
                    EventManager.posePoint(PointConfig.ad_load_success, Bundle().apply {
                        putString(PointKey.scene,adEnum.adName)
                        putString(PointKey.ad_format,data.iwn)
                        putString(PointKey.ad_code_id,data.jsk)
                        putString(PointKey.ad_source,adSource)
                        putString(PointKey.ad_network,it.adNetWork)
                        putString(PointKey.ad_source_client,data.iwk)
                    })
                }
                setFailedCall {
                    var shouldLoadNext: Boolean
                    var inFlightCount: Int
                    var closed: Boolean
                    synchronized(session) {
                        callbackFinished = true
                        // 当前层失败只有在“未通过超时推进过下一层”且“整轮未关闭”时才继续。
                        // 如果 session 已关闭，说明已有成功库存或没有剩余层级，不再开新请求。
                        shouldLoadNext = !movedNext && !session.closed
                        // 当前层回调完成，从在途请求数里扣掉。
                        session.inFlightCount -= 1
                        inFlightCount = session.inFlightCount
                        closed = session.closed
                    }
                    timeoutJob?.cancel()
                    AppLogs.dLog(
                        TAG,
                        "请求失败 adSession fail sid:${session.id} enum:${adEnum.adName} ipn:${data.ipn} id:${data.jsk} movedNext:${movedNext} closed:${closed} shouldNext:${shouldLoadNext} 在途数:${inFlightCount} code:${it.error_code} message:${it.error_message}"
                    )
                    AppLogs.dLog(TAG, "adLoader:${adLoader.javaClass.simpleName} loadFail:${adEnum}-id:${data.jsk}-code:${it.error_code}-message:${it.error_message}")
                    EventManager.posePoint(PointConfig.ad_load_fail, Bundle().apply {
                        putString(PointKey.scene,adEnum.adName)
                        putString(PointKey.ad_format,data.iwn)
                        putString(PointKey.ad_code_id,data.jsk)
                        putString(PointKey.ad_source,adSource)
                        putString(PointKey.ad_network,it.adNetwork)
                        putString(PointKey.ad_source_client,data.iwk)
                        putString(PointKey.error_code,it.error_code)
                        putString(PointKey.error_message,it.error_message)
                    })
                    if (shouldLoadNext) {
                        AppLogs.dLog(TAG, "失败继续下一层 adSession next sid:${session.id} enum:${adEnum.adName}")
                        load(session, list)
                    } else {
                        finishLoadSessionIfIdle(session)
                    }
                }
                startLoadAD()
            }

        } else {
            // 没有可请求层级了，关闭 session；若还有超时后的晚到请求，等它们回调后再清 loading。
            session.closed = true
            AppLogs.dLog(TAG, "层级耗尽 adSession noMore sid:${session.id} enum:${adEnum.adName} 关闭本轮")
            finishLoadSessionIfIdle(session)
        }
    }


    @Synchronized
    private fun isLoading(enum: ADEnum): Boolean {
        return loadingAdSet.contains(enum)
    }

    @Synchronized
    private fun finishLoadSessionIfIdle(session: ADLoadSession) {
        synchronized(session) {
            // 只要还有晚到请求没回调，就继续保持广告位 loading，避免新一轮请求叠加。
            if (session.inFlightCount > 0) {
                AppLogs.dLog(
                    TAG,
                    "请求轮次等待收尾 adSession wait sid:${session.id} enum:${session.adEnum.adName} 在途数:${session.inFlightCount}"
                )
                return
            }
        }
        // 本轮所有已发出的请求都收尾后，广告位才允许下一轮 preloadAD。
        loadingAdSet.remove(session.adEnum)
        AppLogs.dLog(
            TAG,
            "请求轮次结束 adSession finish sid:${session.id} enum:${session.adEnum.adName} 库存数:${adCache[session.adEnum]?.size ?: 0}"
        )
    }

    private fun isValidCache(adResultData: ADResultData): Boolean {
        val requestData = adResultData.adRequestData ?: return false
        if (adResultData.adAny == null) return false
        return (System.currentTimeMillis() - adResultData.adCreateTime) / 1000 <= requestData.isk
    }

    // 每次读写库存前清掉空对象和过期广告，避免展示侧拿到无效库存。
    @Synchronized
    private fun pruneInvalidCache(enum: ADEnum) {
        val cacheList = adCache[enum] ?: return
        // 使用同一个迭代器完成检查和删除，避免遍历过程中直接修改集合引发并发修改异常。
        val iterator = cacheList.iterator()
        while (iterator.hasNext()) {
            val adResultData = iterator.next()
            if (!isValidCache(adResultData)) {
                // 先从库存中删除，确保后续读取不会再次取得该广告；
                // 再释放 NativeAd 持有的底层资源。
                iterator.remove()
                releaseCachedNativeAd(adResultData)
            }
        }
        if (cacheList.isEmpty()) {
            adCache.remove(enum)
        } else {
            cacheList.sortByDescending { it.adRequestData?.ipn ?: Int.MIN_VALUE }
        }
    }

    /**
     * 释放已经不再由广告库存持有的原生广告资源。
     *
     * 这里只处理 NativeAd，其他广告类型保持原来的生命周期管理方式，不在此处销毁。
     * NativeAd.destroy() 必须在主线程调用，因此后台线程触发缓存清理时，需要切换到主线程执行。
     */
    private fun releaseCachedNativeAd(adResultData: ADResultData) {
        val adObject = adResultData.adAny
        if (adObject !is NativeAd) {
            return
        }
        // 在真正执行 destroy() 前先断开 ADResultData 对 NativeAd 的引用。
        // 这样即使释放任务被投递到主线程稍后执行，也不会被其他流程再次当作有效库存使用，
        // 同一个 ADResultData 再次进入清理流程时也不会重复调用 destroy()。
        adResultData.adAny = null
        if (Looper.myLooper() == Looper.getMainLooper()) {
            // 当前已经位于主线程，可以直接释放。
            adObject.destroy()
        } else {
            // 当前位于后台线程，将资源释放任务投递到主线程执行。
            Handler(Looper.getMainLooper()).post(object : Runnable {
                override fun run() {
                    adObject.destroy()
                }
            })
        }
    }

    // 记录同一轮库存请求的状态，用来协调超时切层、成功收敛和晚到回调。
    private class ADLoadSession(
        // 当前 session 归属的广告位。所有请求结果都会写入这个广告位的库存池。
        val adEnum: ADEnum
    ) {
        // 日志串联用，同一轮请求的 start/request/timeout/success/fail/finish 都带同一个 sid。
        val id: String = System.currentTimeMillis().toString().takeLast(6)

        @Volatile
        // 是否已经有任意层级成功返回。为 true 后，超时任务不再主动推进下一层。
        var hasSuccess = false
        @Volatile
        // 是否停止继续开启新层级。成功返回或层级耗尽时置 true。
        var closed = false
        // 当前 session 下已经发出、但还没成功/失败回调的请求数。
        // 归零后才能清 loadingAdSet，避免晚到请求未收尾时开启新一轮请求。
        var inFlightCount = 0
    }
    fun onAdClicked(currentADEnum: ADEnum, requestBean: ADRequestData?, pointTag: String?="",ad_network: String?="") {
        // 只有开屏点击才消耗开屏点击限额，其他广告点击不再影响开屏展示。
        if (isOpenAd(currentADEnum)) {
            LocalDataManager.openAdClickEveryDay = LocalDataManager.openAdClickEveryDay + 1
        }
        EventManager.posePoint(PointConfig.ad_click, Bundle().apply {
            putString(PointKey.scene,currentADEnum.adName)
            putString(PointKey.ad_network,ad_network)
            putString(PointKey.ad_format,requestBean?.iwn?:"")
            putString(PointKey.ad_code_id,requestBean?.jsk?:"")
            putString(PointKey.ad_source_client,requestBean?.iwk?:"")
            putString(PointKey.ad_pos_id,pointTag)
        })
    }

    fun allowShowScreenAD(): Boolean {
        if (!allowAds) return false
        var cooldownSeconds = 30
        val configuredCooldownSeconds = kc_cd
        if (configuredCooldownSeconds != null) {
            cooldownSeconds = configuredCooldownSeconds
        }
        return System.currentTimeMillis() - lastScreenDismissTime > cooldownSeconds * 1000L
    }

    fun addChance(enum: ADEnum, pointId: String) {
        if (!allowAds)return
        // 开屏机会事件也遵守同一套限额，避免被拦截的请求仍上报为可展示机会。
        if (isOpenAd(enum) && isOpenAdFrequencyLimitReached("addChance")) {
            AppLogs.dLog(TAG, "开屏广告频控拦截机会事件:${enum.adName} pointId:$pointId")
            return
        }
        AppLogs.dLog(TAG,"addChance enum:${enum} pointId:${pointId}")
        EventManager.posePoint(PointConfig.ad_chance, Bundle().apply {
            putString(PointKey.scene, enum.adName)
            putString(PointKey.ad_pos_id, pointId)
        })
    }

    fun addChance2(enum: ADEnum, pointId: String) {
        if (!allowAds)return
        var isBUser = LocalDataManager.isBUser
        if (!isBUser)return
        AppLogs.dLog(TAG,"addChance2 enum:${enum} pointId:${pointId}")
        EventManager.posePoint(PointConfig.ad_chance, Bundle().apply {
            putString(PointKey.scene, enum.adName)
            putString(PointKey.ad_pos_id, pointId)
        })
    }

    fun poseAdImpressionFail(
        adEnum: ADEnum,
        adResultData: ADResultData?,
        pointTag: String,
        error_code: String,
        error_message: String,
    ) {
        EventManager.posePoint(PointConfig.ad_impression_fail, Bundle().apply {
            putString(PointKey.scene, adEnum.adName)
            putString(PointKey.ad_pos_id, pointTag)
            putString(PointKey.error_message, error_message)
            putString(PointKey.error_code,if (error_code.isEmpty())"unknow" else error_code)
            if(adResultData!=null){
                putString(PointKey.ad_format, adResultData.adRequestData?.iwn ?: "")
                putString(PointKey.ad_code_id, adResultData.adRequestData?.jsk ?: "")
                putString(PointKey.ad_network, adResultData.adNetWork)
                putString(PointKey.ad_source_client, adResultData.adRequestData?.iwk?:"")
            }
        })
    }

    const val AD_IMPRESSION_FAIL_NO_CACHE = "ad_nocache"
    const val AD_IMPRESSION_FAIL_SHOW = "impfail"
    const val AD_IMPRESSION_FAIL_UNINITIALIZED = "uninitialized"
    const val AD_IMPRESSION_FAIL_NOT_PREPARED = "notPrepared"

}
