package com.pdf.editor.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.pdf.editor.MyAPP
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADControl.preloadAD
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.nf.NotifyShow
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.FileManager
import com.pdf.editor.common.tools.PermissionManager
import com.pdf.editor.common.tools.ScanConfig
import com.pdf.editor.common.tools.SendUtils
import com.pdf.editor.common.tools.jumpToActivity
import com.pdf.editor.common.tools.setOneClick
import com.pdf.editor.data.FilesData
import com.pdf.editor.databinding.ActivityPdfMainBinding
import com.pdf.editor.pop.NFGuidePop
import com.pdf.editor.ui.detail.reader.ReaderSurfaceActivity // PDF14-MIGRATION: 直接启动 PDF 阅读器，移除 FunActivity 转发层。
import com.pdf.editor.ui.home.HomeFragment
import com.pdf.editor.ui.tools.ToolsFragment
import com.pdf.editor.ui.tools.document.imagepdf.ImageQueueActivity
import com.pdf.editor.ui.tools.document.scan.CaptureReviewActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class PdfMainActivity : BaseActivity<ActivityPdfMainBinding>() {

    private val fragments = arrayListOf<Fragment>()

    override fun getBinding(inflater: LayoutInflater): ActivityPdfMainBinding {
        return ActivityPdfMainBinding.inflate(inflater)
    }

    private val scanOptions by lazy {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(10)
            .setResultFormats(
                // PDF14-MIGRATION: 与源入口一致，JPEG 页交给迁入的扫描预览与创建流程。
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
            )
            .setScannerMode(SCANNER_MODE_FULL)
            .build()
    }

    private val scanner by lazy { GmsDocumentScanning.getClient(scanOptions) }

    private val scannerLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            AppLogs.dLog(aTAG, "scan resultCode:${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                EventManager.posePoint(PointConfig.scan_save)
                val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val pages = scanResult?.pages?.map { it.imageUri }.orEmpty()
                if (pages.isNotEmpty()) CaptureReviewActivity.open(this, pages)
            }
        }

    private val photosLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNullOrEmpty()) {
            return@registerForActivityResult
        }
            // PDF14-MIGRATION: 选图后直接进入源图片排序/删除/添加/确认链路。
            ImageQueueActivity.open(this, uris)
        }

    private var lastBackTime = 0L
    private var permissionTime = 0L
    private var fromType = 0
    private var nfContent = ""
    private var filePermissionWatchJob: Job? = null
    private var waitingForFilePermission = false

    var startTime = 0L


    override fun setShowView() {
        fromType = intent.getIntExtra("fromType", 0)
        nfContent = intent.getStringExtra("nfContent") ?: ""
        startTime = System.currentTimeMillis()
        setupPager()
        updateUI(0)
        aBinding.root.post {
            preloadAD(ADEnum.BANNER_AD_1)
            preloadAD(ADEnum.BANNER_AD_2)
            preloadAD(ADEnum.INT_AD_USER)
            preloadAD(ADEnum.INT_AD_EXIT)
            if (LocalDataManager.firstHome){
                EventManager.posePoint(PointConfig.ad_permission_home)
                LocalDataManager.firstHome = false
            }
            EventManager.posePoint(PointConfig.home_page)
            val allowNF = PermissionManager.allowNF(this)
            if (!allowNF) {
                NFGuidePop(this).createPop {
                    NotifyShow.startForeground("Main allowNF:$allowNF")
                }
            } else {
                NotifyShow.startForeground("Main allowNF:$allowNF")
            }
            preloadDocumentScanner()
            handleExternalFile()
            if (nfContent == "recentOpenPDF") {
                FileManager.resolveRecentPdfData(this)?.let {
                    ReaderSurfaceActivity.startFileActivity(this, it)
                }
            }else if (nfContent == "search"){
                aBinding.fragmentMain.setCurrentItem(0, false)
                (fragments.getOrNull(0) as? HomeFragment)?.focusSearch()
            }else if (nfContent == "scan"){
                aBinding.fragmentMain.setCurrentItem(0, false)
            }else if (nfContent == "convert"){
                aBinding.fragmentMain.setCurrentItem(1, false)
            }
        }
    }

    override fun setListener() {
        aBinding.llFile.setOneClick {
            aBinding.fragmentMain.setCurrentItem(0, true)
        }
        aBinding.llTool.setOneClick {
            aBinding.fragmentMain.setCurrentItem(1, true)
        }
        aBinding.ivScanEntry.setOneClick {
            toScan()
        }
        MyAPP.eventLiveData.observe(this) {
            if (it == "toScan") {
                toScan()
            } else if (it == SendUtils.EVENT_EXTERNAL_FILE_READY) {
                handleExternalFile()
            }
        }
    }

    private fun setupPager() {
        fragments.clear()
        fragments.add(HomeFragment())
        fragments.add(ToolsFragment())
        aBinding.fragmentMain.apply {
            offscreenPageLimit = fragments.size
            adapter = object : FragmentPagerAdapter(
                supportFragmentManager,
                BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT
            ) {
                override fun getItem(position: Int): Fragment = fragments[position]
                override fun getCount(): Int = fragments.size
            }
            addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = Unit

                override fun onPageSelected(position: Int) {
                    updateUI(position)
                    EventManager.posePoint(PointConfig.page_view, Bundle().apply {
                        putString(PointKey.page, if (position == 0)"files" else "tools")
                    })
                    if (System.currentTimeMillis()-startTime>1000){
                        ADShowManager(this@PdfMainActivity, "PdfMainActivity").showScreenAD(PointConfig.AD_POINT_INT1, ADEnum.INT_AD_USER){ type, hasValue ->

                        }
                    }
                }

                override fun onPageScrollStateChanged(state: Int) = Unit
            })
        }
    }

    fun toScan() {
        if (!PermissionManager.hasAllFilePermission(this)) {
            requestFilePermission {
                MyAPP.resetLiveData.postValue("resetAll")
                // PDF14-MIGRATION: 授权完成后继续用户已选择的工具流程。
                launchToolScanner()
            }
            return
        }
        launchToolScanner()
    }

    private fun launchToolScanner() {
        EventManager.posePoint(PointConfig.tool_scan)
        scanner.getStartScanIntent(this)
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                AppLogs.eLog(aTAG, "scanner init failed:${it.stackTraceToString()}")
            }
    }

    fun toPhotos() {
        if (!PermissionManager.hasAllFilePermission(this)) {
            requestFilePermission {
                MyAPP.resetLiveData.postValue("resetAll")
                launchToolPhotoPicker()
            }
            return
        }
        launchToolPhotoPicker()
    }

    private fun launchToolPhotoPicker() {
        photosLauncher.launch(
            PickVisualMediaRequest.Builder()
                .setMediaType(PickVisualMedia.ImageOnly)
                .build()
        )
    }

    private fun requestFilePermission(onGranted: () -> Unit) {
        filePermissionWatchJob?.cancel()
        waitingForFilePermission = true
        filePermissionWatchJob = lifecycleScope.launch {
            while (isActive && waitingForFilePermission) {
                delay(300)
                if (PermissionManager.hasAllFilePermission(this@PdfMainActivity)) {
                    waitingForFilePermission = false
                    onGranted()
                    // Settings 属于系统应用，只能把自己的任务重新置于前台。
                    startActivity(Intent(this@PdfMainActivity, this@PdfMainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    })
                    break
                }
            }
        }
        PermissionManager.requestManageAllFilesPermission(this, {
            if (waitingForFilePermission) {
                waitingForFilePermission = false
                onGranted()
            }
        }, {
            waitingForFilePermission = false
        })
    }


    private fun updateUI(index: Int) {
        if (index == 0){
            aBinding.llFile.getChildAt(0).isEnabled = false
            aBinding.llFile.getChildAt(1).isEnabled = false
            aBinding.llTool.getChildAt(0).isEnabled = true
            aBinding.llTool.getChildAt(1).isEnabled = true
        }else{
            aBinding.llFile.getChildAt(0).isEnabled = true
            aBinding.llFile.getChildAt(1).isEnabled = true
            aBinding.llTool.getChildAt(0).isEnabled = false
            aBinding.llTool.getChildAt(1).isEnabled = false
        }
    }

    private fun preloadDocumentScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setScannerMode(SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(this)
            .addOnSuccessListener {
                AppLogs.dLog(aTAG, "MLKit preload complete")
            }
            .addOnFailureListener {
                AppLogs.dLog(aTAG, "MLKit preload error:$it")
            }
    }

    private fun handleExternalFile() {
        val file = SendUtils.consumeTempFile() ?: return
        val data = FilesData.createDefault(file)
        if (data.fileType != ScanConfig.FILE_TYPE_PDF) {
            return
        }
        FileManager.ensurePdfThumbnail(this, data)
        data.fileOpenTime = System.currentTimeMillis()
        LocalDataManager.setOpenTime(data.fileId, data.fileOpenTime)
        LocalDataManager.recentFileData = data
        LocalDataManager.saveRecentOpenFiles(data)
        ReaderSurfaceActivity.startFileActivity(this, data)
    }

    override fun onBackPressed() {
        AppLogs.dLog(aTAG,"onBackPressed3:fromType${fromType} time:${(System.currentTimeMillis()-permissionTime)}")
        if ((System.currentTimeMillis()-lastBackTime) >1500){
            lastBackTime = System.currentTimeMillis()
            EventManager.posePoint(PointConfig.home_page_back)
            moveTaskToBack(true)
        }
    }

    override fun onDestroy() {
        MyAPP.eventLiveData.removeObservers(this)
        super.onDestroy()
    }
}
