package com.pdf.editor

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.pdf.editor.common.config.FirebaseManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.point.EventParamsManager
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.SendUtils
import com.pdf.editor.common.tools.isOtherPkg
import com.pdf.editor.common.nf.NFControl
import com.pdf.editor.common.nf.NotifyShow
import com.pdf.editor.common.user.UserControl
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.core.BaseLifeCycle
import com.pdf.editor.common.event.ProtectedUnPeekLiveData
import com.pdf.editor.common.point.SessionManager
import com.pdf.editor.data.FilesData
import com.tencent.mmkv.MMKV
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.math.abs

class MyAPP : Application() {
    var TAG = "MyAPP"

    val applicationScope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            CoroutineName("AppInitScope") +
            CoroutineExceptionHandler { _, throwable ->
                AppLogs.dLog(TAG, "Uncaught exception in applicationScope:${throwable.stackTrace}")
            }
    )

    @Volatile
    var count = 0
    var firstTime = 0L

    companion object {
        lateinit var instance: MyAPP
        val isDebug = BuildConfig.DEBUG
        val pdfSignLiveData by lazy { ProtectedUnPeekLiveData<Bitmap>() }
        val resetLiveData by lazy { ProtectedUnPeekLiveData<String>() }
        val eventLiveData by lazy { ProtectedUnPeekLiveData<String>() }
        val deleteFilesLiveData by lazy { ProtectedUnPeekLiveData<FilesData>() }
        var isAppInForeground = false
    }

    var lifecycleApp = BaseLifeCycle()
    var needRefreshFile = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (isOtherPkg(this)) return
        firstTime = System.currentTimeMillis()

        initOtherSdk()

        val language = LocalDataManager.cacheLanguage
        if (language.isNotEmpty()) {
            val localeList = LocaleListCompat.forLanguageTags(language)
            AppCompatDelegate.setApplicationLocales(localeList)
        }

        NFControl.registerNF()
        SendUtils.registerListener()
        NotifyShow.startForeground("APP")
        registerActivityLifecycleCallbacks(lifecycleApp)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                AppLogs.dLog(instance.TAG, "App entered foreground")
            }

            override fun onResume(owner: LifecycleOwner) {
                super.onResume(owner)
                AppLogs.dLog(instance.TAG, "App entered foreground onResume")
                NotifyShow.startForeground("onResume")
            }

            override fun onStop(owner: LifecycleOwner) {
                AppLogs.dLog(instance.TAG, "App entered background")
            }
        })
        applicationScope.launch{
            delay(1000)
            if (lifecycleApp.count > 0) {
                AppLogs.dLog(NFControl.TAG, "App 正常启动")
                return@launch
            }
            SessionManager.registerBackListener()
        }
    }

    private fun initOtherSdk() {
        MMKV.initialize(this)
        ADControl.initADPlatform()
        ADControl.adUserType = LocalDataManager.adUserType
        if (ADControl.adUserType == 0) {
            ADControl.adUserType = abs(EventParamsManager.getDeviceID().hashCode() % 8) + 1
            LocalDataManager.adUserType = ADControl.adUserType
            EventManager.addUserType(Bundle().apply {
                putInt("user_group", ADControl.adUserType)
            })
        }
        FirebaseManager.initData()
        if (!LocalDataManager.isBUser) {
            UserControl.getCloak()
            UserControl.getIP()
            UserControl.getTime()
        }
        applicationScope.launch {
            supervisorScope {
                launch {
                    PDFBoxResourceLoader.init(instance)
                }
                launch {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val processName = Application.getProcessName()
                        if (packageName != processName) {
                            WebView.setDataDirectorySuffix(processName)
                        }
                    }
                }
                launch {
                    initAF()
                }
            }
        }
    }

    private fun initAF() {
        val startTime = System.currentTimeMillis()
        runCatching {
            val key = "cUAgiJVDxJVzfo7wYNxbBf"
            val conversionDataListener = object : AppsFlyerConversionListener {
                override fun onConversionDataSuccess(data: Map<String, Any>?) {
                    if (LocalDataManager.afFrom.equals("organic", true).not()) return
                    data?.forEach {
                        AppLogs.dLog(TAG, "af onConversionDataSuccess: ${it.key} = ${it.value}")
                    }
                    runCatching {
                        val afStatus = data?.get("af_status") as? String
                        if (afStatus.isNullOrBlank()) return@runCatching
                        LocalDataManager.afFrom = afStatus
                        EventManager.posePoint(PointConfig.af_suc, Bundle().apply {
                            val userStatus = if (afStatus.equals("organic", true)) 0 else 1
                            putInt(PointKey.af_user, userStatus)
                            putLong(
                                PointKey.load_time,
                                (System.currentTimeMillis() - startTime) / 1000
                            )
                            putString(PointKey.network, afStatus)
                        })
                        UserControl.isBuyUser()
                    }
                }

                override fun onConversionDataFail(error: String?) {
                    AppLogs.dLog(TAG, "af onConversionDataFail : $error")
                }

                override fun onAppOpenAttribution(data: Map<String, String>?) {
                    data?.forEach {
                        AppLogs.dLog(TAG, "af onAppOpenAttribution: ${it.key} = ${it.value}")
                    }
                }

                override fun onAttributionFailure(error: String?) {
                    AppLogs.dLog(TAG, "af onAttributionFailure : $error")
                }
            }
            AppsFlyerLib.getInstance().setDebugLog(isDebug)
            AppsFlyerLib.getInstance().setCustomerUserId(EventParamsManager.getDeviceID())
            AppsFlyerLib.getInstance().init(key, conversionDataListener, applicationContext)
            AppsFlyerLib.getInstance().start(this)
        }.onFailure {
            AppLogs.eLog(TAG,"af init error :${it.stackTraceToString()}")
        }
    }
}
