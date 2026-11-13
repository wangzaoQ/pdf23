// PDF14-MIGRATION: 源自 pdf14；适配包名/资源/当前基类，并复用宿主已有的独立签名页。
// 完整改动范围见 docs/pdf14-reader-migration.md；阅读、批注、编辑和保存逻辑保持原样。
package com.pdf.editor.ui.detail.reader

import android.app.Activity
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStateAtLeast
import com.pdf.editor.MyAPP
import com.pdf.editor.R
import com.pdf.editor.ui.tools.document.common.FileRenamePop
import com.pdf.editor.ui.tools.document.common.DocumentProcessActivity
import com.pdf.editor.ui.tools.document.common.DocumentTaskRequest
import com.pdf.editor.ui.tools.document.file.FileSelectAction
import com.pdf.editor.ui.tools.document.file.ToolPermissionManager
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.ui.detail.reader.ReaderHistoryStore as LocalCacheStore
import com.pdf.editor.common.config.PointConfig as EventConfig
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.DataUtils
import com.pdf.editor.data.FilesData
import com.pdf.editor.databinding.ActivityPdfPreviewBinding
import com.pdf.editor.ui.detail.add.SignActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * PDF 阅读与编辑页面的总协调器。
 *
 * 各层职责在这里汇合：
 * - [PdfiumDocumentView] 负责打开、渲染 PDF，并提供文字布局和页面坐标；
 * - [PdfAnnotationOverlayView] 负责接收手势、显示选区和尚未保存的批注预览；
 * - [PdfEditUtil] 使用 PDFBox 把最终编辑结果写回文件；
 * - 本 Activity 维护工具状态、操作顺序、保存/撤销流程以及面板交互。
 *
 * 编辑采用“先预览、后落盘”的模型。画笔、橡皮擦、高亮和下划线先进入
 * [pendingAnnotationEdits]，只有用户点击保存时才按原顺序写入 PDF。文字、图片和签名
 * 使用临时 View 作为覆盖层，保存时再将 View 坐标转换为 PDF 页面坐标。
 */
class ReaderSurfaceActivity : BaseActivity<ActivityPdfPreviewBinding>() {

    /** PDF14-MIGRATION: 源基类字段名为 binding，当前 BaseActivity 为 aBinding；仅做命名映射。 */
    private val binding get() = aBinding

    /**
     * 尚未写入 PDF 的批注操作。
     *
     * 必须保存为一个有序列表，不能拆成三个独立集合。例如“画一笔 -> 擦除 -> 再画一笔”
     * 的结果依赖执行顺序；保存和重建预览时都要严格按该顺序重放。
     */
    private sealed class PendingAnnotationEdit {
        /** 高亮或下划线；矩形已经是页面归一化坐标。 */
        data class Text(val markup: PdfEditUtil.TextMarkup) : PendingAnnotationEdit()

        /**
         * 自由画笔轨迹。id 只用于把待保存操作与界面预览对应起来，不写入 PDF。
         * widthFraction 是相对页面宽度的比例，缩放或重新渲染后视觉粗细仍然一致。
         */
        data class Ink(
            val id: Long,
            val points: List<PdfiumDocumentView.PagePosition>,
            val color: Int,
            val widthFraction: Float
        ) : PendingAnnotationEdit()

        /**
         * 橡皮擦轨迹。它不是简单删除屏幕像素，而是在重放时命中之前的批注对象。
         */
        data class Erase(
            val points: List<PdfiumDocumentView.PagePosition>,
            val radiusFraction: Float
        ) : PendingAnnotationEdit()
    }

    /**
     * 文字、图片和签名覆盖层在用户点击保存时生成的不可变快照。
     * 图片与签名最终都以 Bitmap 写入，但保留同一个 Image 类型即可复用完整落盘流程。
     */
    private sealed class PendingLayerEdit(open val pageIndex: Int) {
        data class Text(
            override val pageIndex: Int,
            val text: String,
            val placement: PdfEditUtil.TextPlacement
        ) : PendingLayerEdit(pageIndex)

        data class Image(
            override val pageIndex: Int,
            val bitmap: Bitmap,
            val placement: PdfEditUtil.ImagePlacement
        ) : PendingLayerEdit(pageIndex)
    }

    /** 当前正在阅读并会被原位修改的 PDF 文件。 */
    private lateinit var pdfFile: File
    /** 页码 UI 从 1 开始；PDFium/PDFBox 的 pageIndex 从 0 开始。 */
    private var currentPage = 1
    private var pageCount = 0

    /** 当前工具。TOOL_NONE 仍对应默认的文字选择模式。 */
    private var selectedTool = TOOL_NONE

    /** 文字/图片覆盖层所属的 0 基页面，-1 表示当前没有待提交覆盖层。 */
    private var editPageIndex = -1
    /** 用户点击图片工具时记录落点，系统选图返回后仍使用该位置。 */
    private var imagePlacementX = 0f
    private var imagePlacementY = 0f
    private var isImagePickerOpen = false

    /** 页码拖动条自己的手势状态，避免页面变化回调反向覆盖正在拖动的位置。 */
    private var isScrubberDragging = false
    private var lastScrubberRequestedPage = 1

    /** 当前批注颜色，同时用于文字标记和画笔。 */
    private var selectedColor = Color.rgb(213, 0, 0)

    /**
     * 文字命中计算在后台执行。每次发起新请求都递增版本号，旧请求即使较晚返回也会被丢弃，
     * 防止快速拖动选择手柄时旧结果覆盖新选区。
     */
    private var selectionRequestVersion = 0

    /** 已经落盘操作的临时备份，供编辑栏中的“上一步”恢复。 */
    private val undoFiles = ArrayDeque<File>()
    /** 本次会话尚未落盘的批注操作，也是重建预览的唯一真实数据源。 */
    private val pendingAnnotationEdits = mutableListOf<PendingAnnotationEdit>()
    private var nextInkPreviewId = 1L
    /** 驱动批注控件选中态。 */
    private var selectedAnnotationUi: View? = null
    /** 驱动“文字/图片/签名”入口的选中态。 */
    private var selectedEditUi: View? = null

    // PDF14-MIGRATION: 恢复源工具预览来源，普通阅读入口保持空字符串。
    var fromType = ""
    /** 工具来源只预览，不更新最近访问时间或阅读位置；普通文件入口保持原记录行为。 */
    private var recordHistory = true


    /**
     * 系统选图返回可能晚于工具切换，因此回调再次检查 TOOL_IMAGE，避免把过期结果放到页面上。
     */
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        isImagePickerOpen = false
        if (uri != null && selectedTool == TOOL_IMAGE) {
            placePickedImage(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showStatusBarText(false)
    }

    override fun getBinding(inflater: LayoutInflater): ActivityPdfPreviewBinding {
        return ActivityPdfPreviewBinding.inflate(inflater)
    }

    override fun setShowView() {
        // PDF14-MIGRATION: 原基类在 initializeView 前 attach 历史会话，这里保持相同时机。
        LocalCacheStore.attach(this)
        // PDF14-MIGRATION: 恢复源工具预览判断；工具流程不写普通阅读历史。
        fromType = intent.getStringExtra("fromType") ?: ""
        recordHistory = intent.getBooleanExtra(EXTRA_RECORD_HISTORY, true) && fromType.isEmpty()
        // Activity 只接受已经存在且可读的本地文件；后续 PDFium 和 PDFBox 共用这个路径。
        val file = intent.getStringExtra(EXTRA_FILE_PATH)?.let(::File)
        if (file == null || !file.isFile || !file.canRead()) {
            Toast.makeText(this, R.string.app_pdf_file_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        pdfFile = file
        EventManager.posePoint(EventConfig.pdf_page)
        val displayName = intent.getStringExtra(EXTRA_FILE_NAME)
            ?.takeIf(String::isNotBlank)
            ?: file.name
        binding.previewTitle.text = displayName
        binding.previewTitle2.text = displayName
        // pdf13 默认使用红色批注色，并在面板再次打开时保留该状态。
        renderColorSelection(binding.colorRed, animated = false)
        configureAnnotationOverlay()
        updateAnnotationMode()
        binding.pageScrubber.visibility = View.GONE
        if (recordHistory) LocalCacheStore.updateLastOpenedAtMillis(file.absolutePath)
        // 缓存页码为 1 基，传给渲染控件前转换成 0 基 pageIndex。
        // 工具默认从第一页预览；拆分条目可以传入原始下标，getIntExtra 的第二参数是未传值时的默认位置。
        val defaultPage = if (recordHistory) LocalCacheStore.getLastOpenedPage(file.absolutePath) - 1 else 0
        renderDocument(intent.getIntExtra(EXTRA_INITIAL_PAGE, defaultPage))
        // ! 取反；工具队列的纯预览隐藏编辑入口，但不显示转换/拆分按钮。
        if (!recordHistory) binding.pdfBottomControl.visibility = View.GONE
        // PDF14-MIGRATION: 以下工具确认逻辑原样恢复自 pdf14，仅映射包名与资源前缀。
        if (fromType.isNotEmpty()){
            binding.btnSave.visibility = View.VISIBLE
            binding.pdfBottomControl.visibility = View.GONE
            if (fromType == FileSelectAction.PDF_TO_WORD.name){
                binding.btnSave.text = getString(R.string.app_convert_to_word)
            }
            if (fromType == FileSelectAction.PDF_TO_IMAGE.name) {
                binding.btnSave.setText(R.string.app_convert_to_images)
            }
            if (fromType == FileSelectAction.SPLIT_PDF.name) {
                binding.btnSave.setText(R.string.app_select_pages)
            }
            binding.btnSave.setSequentialClickListener {
                showUseEndAdBySave{
                    // == 判断拆分入口，使用独立队列，不修改合并或图片队列的数据。
                    if (fromType == FileSelectAction.SPLIT_PDF.name) {
                        com.pdf.editor.ui.tools.document.splitpdf.PdfSplitQueueActivity.open(this, pdfFile.absolutePath)
                        finish()
                    }
                    if (fromType == FileSelectAction.PDF_TO_WORD.name){
                        // 复用预留的转换入口：确认文件名后才提交任务，当前 PDF 保持只读预览。
                        FileRenamePop(this).createPop(pdfFile.nameWithoutExtension, R.string.app_pdf_to_word, R.string.app_convert_to_word) { name ->
                            startActivity(DocumentProcessActivity.intent(this,
                                DocumentTaskRequest.PdfToWord(pdfFile.absolutePath, name)))
                            finish()
                        }
                    }
                    if (fromType == FileSelectAction.PDF_TO_IMAGE.name) {
                        // 旧系统写入公共相册需要存储权限；新系统由 MediaStore 管理本应用创建的图片。
                        val startExport = {
                            FileRenamePop(this).createPop(pdfFile.nameWithoutExtension, R.string.app_pdf_to_image, R.string.app_convert_to_images, R.string.app_album_name_hint) { name ->
                                startActivity(DocumentProcessActivity.intent(this,
                                    DocumentTaskRequest.PdfToImages(pdfFile.absolutePath, name)))
                                finish()
                            }
                        }
                        if (android.os.Build.VERSION.SDK_INT < 29) {
                            ToolPermissionManager.request(this, startExport, {
                                Toast.makeText(this, R.string.app_pdf_images_permission, Toast.LENGTH_LONG).show()
                            })
                        } else startExport()
                    }
                }
            }
        }
    }
    private var useEndContinuationConsumed = false

    /** 扫描图片成功转换为 PDF 后展示 pr_use_end 广告，再打开生成的文件。 */
    private fun showUseEndAdBySave(result: () -> Unit) {
        useEndContinuationConsumed = false
        runCatching {
            val adShowManager = ADShowManager(this, "ReaderSurfaceActivity")
            adShowManager.showScreenAD(
                EventConfig.AD_POINT_USE_END,
                ADEnum.INT_AD_USER
            ) { _, _ ->
                continueAfterUseEndAd(result)
            }
        }.onFailure {
            AppLogs.eLog(aTAG, "showScreenAD error:${it.stackTraceToString()}")
            continueAfterUseEndAd(result)
        }
    }


    /**
     * 广告结束或被拦截后只打开一次生成结果。
     *
     * 广告关闭回调可能早于 Activity.onResume()，因此不能在回调中立即跳转。
     * 与启动页一致，先等待生命周期恢复到 RESUMED，再检查 Activity 当前状态。
     */
    private fun continueAfterUseEndAd(result: () -> Unit) {
        if (useEndContinuationConsumed) {
            return
        }
        useEndContinuationConsumed = true
        lifecycleScope.launch {
            lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                if (isFinishing || isDestroyed || !getActivityStatus()) {
                    return@withStateAtLeast
                }
                result.invoke()
            }
        }
    }

    override fun setListener() {
        // 与 docflow 的 PdfPreviewActivity 一致：签名页通过全局事件返回 Bitmap。
        MyAPP.pdfSignLiveData.observe(this) { bitmap ->
            placeSignature(bitmap)
        }
        onBackPressedDispatcher.addCallback(this) {
            binding.ivBack.performClick()
        }
        binding.ivBack.setSequentialClickListener {
            EventManager.posePoint(EventConfig.pdf_page_back)
            ADShowManager(this@ReaderSurfaceActivity, "ReaderSurfaceActivity").showScreenAD(EventConfig.AD_POINT_READ_BACK, ADEnum.INT_AD_EXIT){ type, hasValue ->
                finish()
            }
        }
        binding.ivBack2.setSequentialClickListener {
            // 仅退出 PDF 编辑态，不触发阅读页返回或退出广告；未保存的文字覆盖层会被清理。
            finishEditingSession()
        }
        binding.pageScrubber.setOnTouchListener { _, event -> processScrubberGesture(event) }
        binding.tvDone.setSequentialClickListener { finishTextInput() }
        binding.notesAction.setSequentialClickListener {
            EventManager.posePoint(EventConfig.editor_entry)
            showAnnotationUi()
        }
        binding.editAction.setSequentialClickListener {
            EventManager.posePoint(EventConfig.editor_entry)
            stageCurrentTextMarkup()
            showEditUi()
        }
        binding.annotationDone.setSequentialClickListener {
            // 离开焦点/结束编辑也要先把当前文字选区转成待保存操作，避免选区无声丢失。
            stageCurrentTextMarkup()
            finishEditingSession()
        }
        // 该按钮的优先级由 undoLastEdit 统一定义：选区 -> 待保存操作 -> 已保存备份 -> 退出。
        binding.llPdfBack.setSequentialClickListener { undoLastEdit() }
        binding.saveAction.setSequentialClickListener {
            EventManager.posePoint(EventConfig.editor_save_click)
            stageCurrentTextMarkup()
            val pendingLayer = capturePendingLayerEdit()
            // 空文字不应阻止其他批注保存，也不应留下一个看似可保存的空编辑框。
            if (hasPendingLayer() && pendingLayer == null) discardPendingChange()
            when {
                pendingAnnotationEdits.isNotEmpty() || pendingLayer != null -> {
                    persistPendingEdits(pendingLayer)
                }
                else -> finishEditingSession()
            }
        }
        binding.highlightTool.setSequentialClickListener {
            selectImportedTool(it, true, TOOL_HIGHLIGHT)
        }
        binding.drawTool.setSequentialClickListener { selectImportedTool(it, true, TOOL_DRAW) }
        binding.underlineTool.setSequentialClickListener {
            selectImportedTool(it, true, TOOL_UNDERLINE)
        }
        binding.eraseTool.setSequentialClickListener { selectImportedTool(it, true, TOOL_ERASER) }
        binding.addTextTool.setSequentialClickListener {
            selectImportedTool(it, false, TOOL_TEXT)
            if (selectedTool == TOOL_TEXT) displayToolHint(R.string.app_add_text_tips)
        }
        binding.addImageTool.setSequentialClickListener {
            selectImportedTool(it, false, TOOL_IMAGE)
            if (selectedTool == TOOL_IMAGE) displayToolHint(R.string.app_add_img_tips)
        }
        binding.addSignTool.setSequentialClickListener {
            selectImportedTool(it, false, TOOL_SIGNATURE)
            if (selectedTool == TOOL_SIGNATURE) {
                startActivity(Intent(this, SignActivity::class.java))
            }
        }
        listOf(
            binding.colorRed,
            binding.colorOrange,
            binding.colorGreen,
            binding.colorBlue,
            binding.colorPurple
        ).forEach { color ->
            color.setSequentialClickListener { selectColorUi(color) }
        }
    }

    private fun configureAnnotationOverlay() {
        // Overlay 只认识屏幕触点；具体落在哪一页以及页内比例由 PDFiumDocumentView 计算。
        binding.annotationOverlay.positionResolver = { x, y, page, clamp ->
            binding.pdfView.mapViewToPage(x, y, page, clamp)
        }
        // 下面两个 mapper 用于把归一化页面坐标重新投影到屏幕，供选区、手柄和画笔预览绘制。
        binding.annotationOverlay.pageRectMapper = binding.pdfView::mapPageRectToView
        binding.annotationOverlay.pagePositionMapper = binding.pdfView::mapPagePositionToView
        binding.annotationOverlay.attachMagnifier(binding.viewerRoot)
        // 滚动和缩放不会改变页面坐标，但会改变屏幕投影，因此只需通知 Overlay 重绘。
        binding.pdfView.onViewportChanged = binding.annotationOverlay::invalidate
        // 高亮/下划线工具开启时，点击别处会先把刚才的选区暂存成预览。
        binding.annotationOverlay.onSelectionFocusLost = { stageCurrentTextMarkup() }
        // SELECT 模式没有消费的拖动事件转交给文档，以保留页面滚动和缩放行为。
        binding.annotationOverlay.navigationEventHandler = { event ->
            binding.pdfView.onTouchEvent(event)
        }
        binding.annotationOverlay.onSelectionRequested = { start, end, expandWord, copyWhenResolved ->
            val requestVersion = ++selectionRequestVersion
            binding.pdfView.findTextRects(start, end, expandWord) { rects, selectedText ->
                // 后台取字完成后，只接收最后一次请求；空结果继续保留已有选区。
                if (requestVersion == selectionRequestVersion && rects.isNotEmpty()) {
                    val viewRects = rects.mapNotNull(binding.pdfView::mapPageRectToView)
                    val first = rects.first()
                    val last = rects.last()
                    // 锚点略微缩进到首尾字符内部，减少边界恰好命中相邻字符导致的跳动。
                    val resolvedStart = PdfiumDocumentView.PagePosition(
                        first.pageIndex,
                        (first.leftFraction + SELECTION_EDGE_INSET).coerceAtMost(first.rightFraction),
                        (first.topFraction + first.bottomFraction) / 2f
                    )
                    val resolvedEnd = PdfiumDocumentView.PagePosition(
                        last.pageIndex,
                        (last.rightFraction - SELECTION_EDGE_INSET).coerceAtLeast(last.leftFraction),
                        (last.topFraction + last.bottomFraction) / 2f
                    )
                    binding.annotationOverlay.updateTextSelection(
                        resolvedStart,
                        resolvedEnd,
                        rects,
                        viewRects
                    )
                    if (copyWhenResolved && selectedText.isNotBlank()) {
                        copySelectionToClipboard(selectedText)
                    }
                }
            }
        }
        binding.annotationOverlay.onInkStroke = { points, widthPx ->
            points.firstOrNull()?.pageIndex?.let { page ->
                // 屏幕笔宽换算为相对页面宽度的比例，避免保存或缩放后笔迹宽度失真。
                val pageWidthPx = binding.pdfView.getPageSize(page).width * binding.pdfView.zoom
                if (pageWidthPx > 0f) {
                    val widthFraction = widthPx / pageWidthPx
                    val edit = PendingAnnotationEdit.Ink(
                        id = nextInkPreviewId++,
                        points = points,
                        color = selectedColor,
                        widthFraction = widthFraction
                    )
                    // 此处只加入队列和预览，绝不写文件；用户显式点击保存后才会落盘。
                    pendingAnnotationEdits += edit
                    binding.annotationOverlay.addInkPreview(
                        edit.id,
                        edit.points,
                        edit.color,
                        edit.widthFraction
                    )
                }
            }
        }
        binding.annotationOverlay.onEraseStroke = { points, radiusPx ->
            points.firstOrNull()?.let { first ->
                // 与画笔同理，擦除半径也持久化为页面宽度比例。
                val pageWidthPx = binding.pdfView.getPageSize(first.pageIndex).width * binding.pdfView.zoom
                if (pageWidthPx > 0f) {
                    pendingAnnotationEdits += PendingAnnotationEdit.Erase(
                        points = points,
                        radiusFraction = radiusPx / pageWidthPx
                    )
                    // 擦除会影响队列中更早的笔迹，因此从头重放比局部修改预览更可靠。
                    rebuildPendingAnnotationPreviews()
                }
            }
        }
    }

    /** Copy the latest resolved selection and provide immediate English feedback. */
    private fun copySelectionToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("PDF text", text))
        Toast.makeText(this, getString(R.string.app_copy_tips), Toast.LENGTH_SHORT).show()
    }

    /**
     * 将当前文字选区转换为高亮/下划线待保存操作。
     *
     * 默认 SELECT 模式只负责选择文字，不生成批注；只有当前工具是高亮或下划线时才会暂存。
     * 成功暂存后清空活动选区，但保留一份不可编辑预览，给用户明确的保存前反馈。
     */
    private fun stageCurrentTextMarkup(): Boolean {
        val type = selectedTextMarkupType() ?: return false
        val rects = binding.annotationOverlay.getSelectedPageRects()
        if (rects.isEmpty()) return false
        pendingAnnotationEdits += PendingAnnotationEdit.Text(
            PdfEditUtil.TextMarkup(rects, type, selectedColor)
        )
        binding.annotationOverlay.addTextMarkupPreview(
            pageRects = rects,
            color = selectedColor,
            underline = type == PdfEditUtil.TextMarkupType.UNDERLINE
        )
        binding.annotationOverlay.clearTextSelection()
        return true
    }

    private fun selectedTextMarkupType(tool: Int = selectedTool): PdfEditUtil.TextMarkupType? {
        return when (tool) {
            TOOL_HIGHLIGHT -> PdfEditUtil.TextMarkupType.HIGHLIGHT
            TOOL_UNDERLINE -> PdfEditUtil.TextMarkupType.UNDERLINE
            else -> null
        }
    }

    /**
     * 重新打开当前 PDF 并定位到指定的 0 基页面。
     *
     * PDFBox 写完文件后必须让 PDFium 重新打开文件，页面内容和原生批注才会反映到位图。
     * 页面回调则统一转换为 1 基页码更新界面和最近阅读记录。
     */
    private fun renderDocument(pageIndex: Int) {
        binding.pdfLoading.visibility = View.VISIBLE
        binding.pdfView.load(
            file = pdfFile,
            defaultPage = pageIndex.coerceAtLeast(0),
            onLoad = { total ->
                refreshPageState(binding.pdfView.currentPage + 1, total)
            },
            onPageChange = { page, total ->
                // 文字/图片覆盖层只能属于一个页面，翻页时不能把它错误写到新页面。
                if (editPageIndex >= 0 && editPageIndex != page) discardPendingChange()
                refreshPageState(page + 1, total)
                // 工具预览翻页也不改变最近阅读页；不能只拦截打开时的时间更新。
                if (recordHistory) LocalCacheStore.updateLastOpenedPage(pdfFile.absolutePath, currentPage)
            },
            onFirstRender = {
                binding.pdfLoading.visibility = View.GONE
            },
            onTap = { x, y -> routeCanvasTap(x, y) },
            onError = { error ->
                binding.pdfLoading.visibility = View.GONE
                Toast.makeText(
                    this,
                    error.message ?: getString(R.string.app_pdf_file_unavailable),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    /**
     * 根据工具决定是否消费一次文档点击。
     * 返回 false 时 PDFiumDocumentView 可以继续处理链接点击、文字选择或普通导航。
     */
    private fun routeCanvasTap(x: Float, y: Float): Boolean {
        // 使用实际命中的页面，而不是“当前页”估算值；相邻两页同时可见时二者可能不同。
        val pagePosition = binding.pdfView.mapViewToPage(x, y) ?: return false
        return when (selectedTool) {
            TOOL_TEXT -> {
                placeTextEditor(x, y, pagePosition.pageIndex)
                true
            }
            TOOL_IMAGE -> {
                // 先保存落点再拉起系统选择器，因为 ActivityResult 返回时原始 MotionEvent 已不存在。
                imagePlacementX = x
                imagePlacementY = y
                editPageIndex = pagePosition.pageIndex
                if (!isImagePickerOpen) {
                    isImagePickerOpen = true
                    pickImageLauncher.launch("image/*")
                }
                true
            }
            TOOL_SIGNATURE -> true
            else -> false
        }
    }

    /**
     * 在点击位置放置 Android EditText。
     * 这里仍是屏幕覆盖层；保存时 [capturePendingLayerEdit] 才采集基线、字号并换算成 PDF 坐标。
     */
    private fun placeTextEditor(x: Float, y: Float, pageIndex: Int) {
        binding.imageOverlay.clearImage()
        val editor = binding.pdfTextEditor
        val params = editor.layoutParams as RelativeLayout.LayoutParams
        params.leftMargin = x.toInt().coerceAtLeast(0)
        params.topMargin = y.toInt().coerceAtLeast(0)
        editor.layoutParams = params
        editor.setText("")
        editor.visibility = View.VISIBLE
        editor.requestFocus()
        editPageIndex = pageIndex
        binding.finishButton.visibility = View.VISIBLE
        editor.post {
            val keyboard = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            keyboard?.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /**
     * 对应 pdf13 顶部 Done：只结束输入并恢复被键盘上移的阅读区，不提交或清空文字。
     * 用户仍可检查页面上的文字框，最后点击底部 Save 才会写入 PDF。
     */
    private fun finishTextInput() {
        val editor = binding.pdfTextEditor
        hideKeyBoard(editor)
        editor.clearFocus()
        binding.viewerRoot.restoreAfterIme()
    }

    /**
     * 将系统选择器返回的图片放入可拖动/缩放覆盖层。
     * post 可确保 Overlay 已完成测量，同时再次验证当前工具，避免异步返回污染其他模式。
     */
    private fun placePickedImage(uri: Uri) {
        val bitmap = runCatching { decodeBitmapAsset(uri) }.getOrNull()
        if (bitmap == null) {
            displayToolHint(R.string.app_pdf_edit_failed)
            return
        }
        binding.pdfTextEditor.visibility = View.GONE
        binding.pdfTextEditor.clearFocus()
        binding.imageOverlay.post {
            if (selectedTool != TOOL_IMAGE) return@post
            binding.imageOverlay.setImage(bitmap, imagePlacementX, imagePlacementY)
            binding.finishButton.visibility = View.VISIBLE
        }
    }

    /**
     * 按尺寸限制解码位图，并应用 EXIF 方向信息。
     * 先只读取宽高，再以 2 的幂设置 inSampleSize，避免直接解码相机原图造成内存峰值。
     */
    private fun decodeBitmapAsset(uri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        var sampleSize = 1
        while (options.outWidth / sampleSize > MAX_IMAGE_DIMENSION ||
            options.outHeight / sampleSize > MAX_IMAGE_DIMENSION
        ) {
            sampleSize *= 2
        }
        val source = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(
                it,
                null,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            )
        } ?: return null
        val rotation = readBitmapRotation(uri)
        if (rotation == 0) return source
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true
        )
        if (rotated !== source) source.recycle()
        return rotated
    }

    /** 读取图片 EXIF 元数据中的旋转角度。 */
    private fun readBitmapRotation(uri: Uri): Int {
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                when (
                    ExifInterface(input).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
    }

    /**
     * 切换当前编辑模式，并清除未完成的文字/图片覆盖层。
     * 重复点击同一工具等价于关闭工具；批注队列不在这里清除，仍可等待保存或撤销。
     */
    private fun changeToolMode(clickedTool: Int): Boolean {
        val shouldSelect = selectedTool != clickedTool
        discardPendingChange()
        selectedTool = if (shouldSelect) clickedTool else TOOL_NONE
        updateAnnotationMode()
        return shouldSelect
    }

    /**
     * 展开批注工具面板。
     *
     * 与 pdf13 的交互保持一致：入口本身只切换界面，不立即创建任何批注。工具未选中时
     * Overlay 仍处于 SELECT 模式，所以用户进入面板前后都可以直接长按、拖动选择文字。
     */
    private fun showAnnotationUi() {
        binding.editToolsBar.visibility = View.GONE
        binding.pdfBottomControl.visibility = View.GONE
        showEditCommandBar()
        selectedTool = TOOL_NONE
        updateAnnotationMode()
        val panel = binding.annotationPanel
        if (panel.visibility == View.VISIBLE) return
        panel.post {
            panel.animate().cancel()
            val offset = if (panel.height > 0) panel.height.toFloat() else 180f
            panel.translationY = -offset
            panel.alpha = 0f
            panel.visibility = View.VISIBLE
            panel.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(PANEL_SHOW_DURATION)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun showEditUi() {
        // 编辑入口需先收起可能存在的批注面板，再露出文字、图片、签名工具。
        binding.pdfBottomControl.visibility = View.GONE
        showEditCommandBar()
        hideAnnotationUi {
            binding.editToolsBar.visibility = View.VISIBLE
        }
    }

    private fun closeImportedToolsUi() {
        // 退出编辑态时取消仍在执行的文字解析结果，防止关闭后异步回调重新显示选区。
        discardPendingChange()
        selectedTool = TOOL_NONE
        selectionRequestVersion++
        binding.annotationOverlay.clearTextSelection()
        updateAnnotationMode()
        hideAnnotationUi {
            binding.editToolsBar.visibility = View.GONE
            hideEditCommandBar()
        }
        clearImportedToolSelection()
    }

    /** 与 pdf13 一致，从底部向上展示撤销/保存操作栏。 */
    private fun showEditCommandBar() {
        val commandBar = binding.editCommandBar
        if (commandBar.visibility == View.VISIBLE) return
        commandBar.post {
            commandBar.animate().cancel()
            val offset = if (commandBar.height > 0) commandBar.height.toFloat() else 120f
            commandBar.translationY = offset
            commandBar.alpha = 0f
            commandBar.visibility = View.VISIBLE
            commandBar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(PANEL_SHOW_DURATION)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                .start()
        }
    }

    /**
     * 向上收起批注面板。
     */
    private fun hideAnnotationUi(onHidden: () -> Unit = {}) {
        val panel = binding.annotationPanel
        if (panel.visibility != View.VISIBLE) {
            onHidden()
            return
        }
        val offset = if (panel.height > 0) panel.height.toFloat() else 180f
        panel.animate().cancel()
        panel.animate()
            .translationY(-offset)
            .alpha(0f)
            .setDuration(PANEL_HIDE_DURATION)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction {
                panel.visibility = View.GONE
                panel.translationY = 0f
                panel.alpha = 1f
                onHidden()
            }
            .start()
    }

    /** 与 pdf13 一致，收起后恢复底部“批注 / 编辑”入口。 */
    private fun hideEditCommandBar() {
        val commandBar = binding.editCommandBar
        val offset = if (commandBar.height > 0) commandBar.height.toFloat() else 120f
        commandBar.animate().cancel()
        commandBar.animate()
            .translationY(offset)
            .alpha(0f)
            .setDuration(PANEL_HIDE_DURATION)
            .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
            .withEndAction {
                commandBar.visibility = View.GONE
                commandBar.translationY = 0f
                commandBar.alpha = 1f
                binding.pdfBottomControl.visibility = View.VISIBLE
            }
            .start()
    }

    /** 退出面板时清除工具高亮；颜色属于用户偏好，因此保持选择。 */
    private fun clearImportedToolSelection() {
        selectedAnnotationUi?.let { reflectToolState(it, false) }
        selectedEditUi?.let { reflectToolState(it, false) }
        selectedAnnotationUi = null
        selectedEditUi = null
    }

    /**
     * 切换导入的工具按钮及实际编辑模式。
     *
     * 高亮与下划线之间切换时保留活动文字选区，用户无需重新选择；离开文字标记工具时，
     * 当前选区会先按旧工具暂存。切到画笔、橡皮擦或编辑工具时则清空文字选择。
     * 同类按钮互斥，重复点击同一项会恢复到默认 SELECT 模式。
     */
    private fun selectImportedTool(tool: View, isAnnotation: Boolean, toolId: Int) {
        discardPendingChange()
        val previousTool = selectedTool
        val previouslySelected = if (isAnnotation) selectedAnnotationUi else selectedEditUi
        val isSameTool = previouslySelected === tool
        val nextTool = if (isSameTool) TOOL_NONE else toolId
        if (selectedTextMarkupType(previousTool) != null && selectedTextMarkupType(nextTool) == null) {
            stageCurrentTextMarkup()
        }
        if (selectedTextMarkupType(nextTool) == null) {
            selectionRequestVersion++
            binding.annotationOverlay.clearTextSelection()
        }
        previouslySelected?.let { reflectToolState(it, false) }
        if (isSameTool) {
            if (isAnnotation) selectedAnnotationUi = null else selectedEditUi = null
            selectedTool = TOOL_NONE
            updateAnnotationMode()
            return
        }
        if (isAnnotation) {
            selectedEditUi?.let { reflectToolState(it, false) }
            selectedEditUi = null
        } else {
            selectedAnnotationUi?.let { reflectToolState(it, false) }
            selectedAnnotationUi = null
        }
        reflectToolState(tool, true)
        if (isAnnotation) selectedAnnotationUi = tool else selectedEditUi = tool
        selectedTool = toolId
        updateAnnotationMode()
    }

    private fun selectColorUi(color: View) {
        selectedColor = when (color) {
            binding.colorOrange -> Color.rgb(245, 124, 0)
            binding.colorGreen -> Color.rgb(71, 201, 90)
            binding.colorBlue -> Color.rgb(64, 147, 241)
            binding.colorPurple -> Color.rgb(155, 93, 229)
            else -> Color.rgb(213, 0, 0)
        }
        renderColorSelection(color, animated = true)
        binding.annotationOverlay.color = selectedColor
    }

    /** 选中色块放大、其余色块恢复原尺寸；初始化阶段可跳过动画。 */
    private fun renderColorSelection(selectedColorView: View, animated: Boolean) {
        listOf(
            binding.colorRed,
            binding.colorOrange,
            binding.colorGreen,
            binding.colorBlue,
            binding.colorPurple
        ).forEach { view ->
            val selected = view === selectedColorView
            val scale = if (selected) COLOR_SELECTED_SCALE else COLOR_NORMAL_SCALE
            view.isSelected = selected
            view.animate().cancel()
            if (animated) {
                view.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(COLOR_SCALE_DURATION)
                    .start()
            } else {
                view.scaleX = scale
                view.scaleY = scale
            }
        }
    }

    /**
     * 将业务工具映射为 Overlay 的三种手势模式。
     * 除画笔和橡皮擦外一律使用 SELECT，因此“未选择工具”也始终可以选择文字。
     */
    private fun updateAnnotationMode() {
        binding.annotationOverlay.mode = when (selectedTool) {
            TOOL_DRAW -> PdfAnnotationOverlayView.Mode.DRAW
            TOOL_ERASER -> PdfAnnotationOverlayView.Mode.ERASER
            else -> PdfAnnotationOverlayView.Mode.SELECT
        }
        // pdf13 仅在“添加文字”模式显示带 Done 的编辑标题栏。
        updateTextEditingTopBar(selectedTool == TOOL_TEXT)
    }

    /**
     * 以顶部滑入/滑出配合透明度变化展示文字编辑标题栏。
     * 重复设置相同状态时不重新播放；快速切换工具会先取消上一段动画，再从当前位置衔接。
     */
    private fun updateTextEditingTopBar(show: Boolean) {
        val topBar = binding.rlTop2
        topBar.animate().cancel()
        if (show) {
            if (topBar.visibility == View.VISIBLE &&
                topBar.translationY == 0f &&
                topBar.alpha == 1f
            ) {
                return
            }
            // 与 annotationPanel 相同：先让 GONE 的层参与一次布局，再开始动画。
            topBar.post {
                // post 执行前用户可能已经切走工具，不能让过期任务重新显示标题栏。
                if (selectedTool != TOOL_TEXT) return@post
                topBar.animate().cancel()
                val fallbackHeight = 89f * resources.displayMetrics.density
                val offset = if (topBar.height > 0) topBar.height.toFloat() else fallbackHeight
                topBar.translationY = -offset
                topBar.alpha = 0f
                topBar.visibility = View.VISIBLE
                topBar.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(TOP_BAR_SHOW_DURATION)
                    .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                    .start()
            }
        } else {
            if (topBar.visibility != View.VISIBLE) {
                topBar.translationY = 0f
                topBar.alpha = 1f
                return
            }
            val fallbackHeight = 89f * resources.displayMetrics.density
            val offset = if (topBar.height > 0) topBar.height.toFloat() else fallbackHeight
            topBar.animate()
                .translationY(-offset)
                .alpha(0f)
                .setDuration(TOP_BAR_HIDE_DURATION)
                .setInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction {
                    if (selectedTool != TOOL_TEXT) {
                        topBar.visibility = View.GONE
                        topBar.translationY = 0f
                        topBar.alpha = 1f
                    }
                }
                .start()
        }
    }

    /** 同步工具按钮及其子视图的选中状态。 */
    private fun reflectToolState(button: View, selected: Boolean) {
        button.isSelected = selected
        if (button is ViewGroup) {
            for (index in 0 until button.childCount) {
                button.getChildAt(index).isSelected = selected
            }
        }
    }

    /**
     * 移除当前页面中尚未提交的文字或图片覆盖层，并收起键盘。
     * 该方法不碰 [pendingAnnotationEdits]，批注预览拥有独立的保存/撤销生命周期。
     */
    private fun discardPendingChange() {
        binding.pdfTextEditor.setText("")
        binding.pdfTextEditor.visibility = View.GONE
        binding.pdfTextEditor.clearFocus()
        binding.imageOverlay.clearImage()
        binding.finishButton.visibility = View.GONE
        editPageIndex = -1
        hideKeyBoard(binding.pdfTextEditor)
        binding.viewerRoot.restoreAfterIme()
    }

    private fun hasPendingLayer(): Boolean =
        binding.pdfTextEditor.visibility == View.VISIBLE || binding.imageOverlay.hasImage()

    /**
     * 在主线程采集文字、图片或签名覆盖层的最终状态。
     *
     * PDFium 随后会在后台写入前释放，因此坐标转换必须在这里完成。文字采集首行基线，
     * 图片/签名采集当前拖动缩放后的矩形；返回对象不再依赖 View 的后续状态。
     */
    private fun capturePendingLayerEdit(): PendingLayerEdit? {
        val editor = binding.pdfTextEditor
        if (editor.visibility == View.VISIBLE) {
            val text = editor.text.toString().trimEnd()
            if (text.isBlank() || editPageIndex < 0) return null
            val placement = PdfEditUtil.captureTextPlacement(
                pdfView = binding.pdfView,
                pageIndex = editPageIndex,
                textX = editor.x + editor.compoundPaddingLeft,
                baselineY = editor.y + editor.baseline,
                textSizePx = editor.textSize,
                lineHeightPx = editor.lineHeight.toFloat()
            ) ?: return null
            return PendingLayerEdit.Text(editPageIndex, text, placement)
        }

        val overlay = binding.imageOverlay
        val bitmap = overlay.getSourceBitmap() ?: return null
        if (editPageIndex < 0) return null
        val rect = overlay.getImageRect()
        val placement = PdfEditUtil.captureImagePlacement(
            pdfView = binding.pdfView,
            pageIndex = editPageIndex,
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom
        ) ?: return null
        return PendingLayerEdit.Image(editPageIndex, bitmap, placement)
    }

    /** 将签名页返回的图片放到当前可见页面中央，后续仍可拖动、缩放并统一保存。 */
    private fun placeSignature(bitmap: Bitmap) {
        editPageIndex = binding.pdfView.currentPage
        val overlay = binding.imageOverlay
        overlay.visibility = View.VISIBLE
        overlay.setImage(bitmap, overlay.width / 2f, overlay.height / 2f)
    }

    /** 在应用缓存目录创建一次写入事务使用的 PDF 临时副本。 */
    private fun createUndoFile(): File = File.createTempFile("pdf-edit-", ".pdf", cacheDir)

    /** 保存可撤销副本并限制数量，避免长时间编辑占用过多缓存空间。 */
    private fun pushUndo(file: File) {
        undoFiles.addLast(file)
        while (undoFiles.size > MAX_UNDO_COUNT) undoFiles.removeFirst().delete()
    }

    /**
     * 执行编辑栏“返回/上一步”的分层状态机。
     *
     * 优先级依次为：移除文字/图片/签名覆盖层、关闭活动文字选区、移除最后一个待保存批注、
     * 恢复最后一次文件备份；如果这些状态都不存在，则直接关闭整个编辑状态。
     */
    private fun undoLastEdit() {
        // 文字、图片或签名覆盖层是最后创建的可见步骤，先撤掉它但保留编辑工具栏。
        if (hasPendingLayer()) {
            discardPendingChange()
            return
        }
        if (binding.annotationOverlay.hasTextSelection()) {
            binding.annotationOverlay.clearTextSelection()
            return
        }
        if (pendingAnnotationEdits.isNotEmpty()) {
            pendingAnnotationEdits.removeAt(pendingAnnotationEdits.lastIndex)
            rebuildPendingAnnotationPreviews()
            return
        }
        val backup = undoFiles.removeLastOrNull()
        if (backup == null) {
            finishEditingSession()
            return
        }
        val page = binding.pdfView.currentPage
        binding.pdfLoading.visibility = View.VISIBLE
        binding.pdfView.recycle()
        addLaunch(
            success = {
                backup.copyTo(pdfFile, overwrite = true)
                backup.delete()
                withContext(Dispatchers.Main) { renderDocument(page) }
            },
            failBack = {
                renderDocument(page)
                displayToolHint(R.string.app_pdf_edit_failed)
            }
        )
    }

    /** 清理会话级撤销信息，并统一关闭工具栏、覆盖层和选中态。 */
    private fun finishEditingSession() {
        clearUndoHistory()
        closeImportedToolsUi()
    }

    /**
     * 用户点击保存后，提交文字标记、画笔、擦除以及当前文字/图片/签名覆盖层。
     *
     * 队列先复制为不可变快照，保存期间禁用 Overlay，避免后台写入时又加入新手势。PDFium
     * 句柄会在写入前释放；任一操作失败都用备份恢复整份文件，不留下部分提交的结果。
     */
    private fun persistPendingEdits(pendingLayer: PendingLayerEdit?) {
        if (pendingAnnotationEdits.isEmpty() && pendingLayer == null) return
        val edits = pendingAnnotationEdits.toList()
        val page = pendingLayer?.pageIndex ?: binding.pdfView.currentPage
        val backup = createUndoFile()
        binding.annotationOverlay.isEnabled = false
        binding.saveAction.isEnabled = false
        binding.pdfLoading.visibility = View.VISIBLE
        binding.pdfView.recycle()
        addLaunch(
            success = {
                pdfFile.copyTo(backup, overwrite = true)
                // 顺序不可交换：Erase 只能擦除它之前已经存在于文件中的可命中批注。
                edits.forEach { edit ->
                    when (edit) {
                        is PendingAnnotationEdit.Text -> PdfEditUtil.addTextMarkup(
                            pdfFile,
                            edit.markup.rects,
                            edit.markup.type,
                            edit.markup.color
                        )
                        is PendingAnnotationEdit.Ink -> PdfEditUtil.addInk(
                            pdfFile,
                            edit.points,
                            edit.color,
                            edit.widthFraction
                        )
                        is PendingAnnotationEdit.Erase -> PdfEditUtil.eraseAnnotations(
                            pdfFile,
                            edit.points,
                            edit.radiusFraction
                        )
                    }
                }
                // 普通内容层在批注之后写入；图片与签名共享 Bitmap 写入路径。
                when (pendingLayer) {
                    is PendingLayerEdit.Text -> PdfEditUtil.addText(
                        pdfFile,
                        assets,
                        pendingLayer.text,
                        pendingLayer.placement
                    )
                    is PendingLayerEdit.Image -> PdfEditUtil.addImage(
                        pdfFile,
                        pendingLayer.bitmap,
                        pendingLayer.placement
                    )
                    null -> Unit
                }
                withContext(Dispatchers.Main) {
                    binding.annotationOverlay.isEnabled = true
                    binding.saveAction.isEnabled = true
                    pendingAnnotationEdits.clear()
                    // 成功后同时清空活动选区和两类预览；真实内容随后由 PDFium 重绘。
                    binding.annotationOverlay.clearTextSelection()
                    binding.annotationOverlay.clearTextMarkupPreviews()
                    binding.annotationOverlay.clearInkPreviews()
                    backup.delete()
                    discardPendingChange()
                    clearUndoHistory()
                    if (pendingLayer != null) {
                        // 添加文字、图片或签名沿用旧流程：成功保存后结束本次编辑。
                        finishEditingSession()
                    } else {
                        // 单纯保存批注时保留编辑操作栏，便于继续添加下一条批注。
                        selectedTool = TOOL_NONE
                        clearImportedToolSelection()
                        updateAnnotationMode()
                    }
                    renderDocument(page)
                    // PDF 编辑内容已经成功写入文件后，再触发功能完成插屏。
                    // 展示入口会统一校验 pr_use_end 开关、kc_cd、用户分组和广告库存。
                    showUseEndAdAfterSave()
                }
            },
            failBack = {
                binding.annotationOverlay.isEnabled = true
                binding.saveAction.isEnabled = true
                if (backup.isFile) backup.copyTo(pdfFile, overwrite = true)
                backup.delete()
                renderDocument(page)
                displayToolHint(R.string.app_pdf_edit_failed)
            }
        )
    }

    /**
     * PDF 保存成功后的 pr_use_end 广告入口。
     *
     * 保存失败、没有待保存内容或仅进入编辑态时都不会调用这里，避免把未完成操作计为功能完成。
     */
    private fun showUseEndAdAfterSave() {
        val adShowManager = ADShowManager(this, "ReaderSurfaceActivity")
        adShowManager.showScreenAD(
            EventConfig.AD_POINT_USE_END,
            ADEnum.INT_AD_USER
        ) { _, _ ->
            // PDF 已经保存完成，此处只等待广告流程结束，不需要继续执行其他业务。
        }
    }

    /**
     * 从 [pendingAnnotationEdits] 完整重建未保存预览。
     *
     * 这是撤销和新增擦除轨迹后的统一恢复路径。先清空派生 UI，再顺序重放，确保擦除只影响
     * 它之前的画笔预览，预览语义与最终 PDFBox 写入语义一致。
     */
    private fun rebuildPendingAnnotationPreviews() {
        binding.annotationOverlay.clearTextMarkupPreviews()
        binding.annotationOverlay.clearInkPreviews()
        pendingAnnotationEdits.forEach { edit ->
            when (edit) {
                is PendingAnnotationEdit.Text -> binding.annotationOverlay.addTextMarkupPreview(
                    edit.markup.rects,
                    edit.markup.color,
                    edit.markup.type == PdfEditUtil.TextMarkupType.UNDERLINE
                )
                is PendingAnnotationEdit.Ink -> binding.annotationOverlay.addInkPreview(
                    edit.id,
                    edit.points,
                    edit.color,
                    edit.widthFraction
                )
                is PendingAnnotationEdit.Erase -> binding.annotationOverlay.applyEraserPreview(
                    edit.points,
                    edit.radiusFraction
                )
            }
        }
    }

    /** 删除会话遗留的临时 PDF 副本；不影响用户的原始文件。 */
    private fun clearUndoHistory() {
        while (undoFiles.isNotEmpty()) undoFiles.removeFirst().delete()
    }

    /**
     * 页面变化后刷新页码和导航控件。
     * 入参 page 为 1 基页码，和用户看到的页码保持一致。
     */
    private fun refreshPageState(page: Int, total: Int) {
        if (total <= 0) return
        currentPage = page.coerceIn(1, total)
        pageCount = total
        lastScrubberRequestedPage = currentPage
        binding.pageScrubberText.text = currentPage.toString()
        binding.pageInfoCurrent.text = currentPage.toString()
        binding.pageInfoTotal.text = total.toString()
        binding.pageScrubber.visibility = if (total > 1) View.VISIBLE else View.GONE
        if (!isScrubberDragging) syncScrubberPosition(currentPage, total)
    }

    /**
     * 将页码拖动条同步到当前文档页面。
     * 使用可移动距离而不是容器总高度，确保滑块中心不会越过文档上下边界。
     */
    private fun syncScrubberPosition(page: Int, total: Int) {
        if (total <= 1 || binding.pageScrubber.visibility != View.VISIBLE) return
        binding.viewerRoot.post {
            val travel = (binding.pdfView.height - binding.pageScrubber.height)
                .coerceAtLeast(0)
                .toFloat()
            val progress = (page - 1).toFloat() / (total - 1).toFloat()
            binding.pageScrubber.y = binding.pdfView.top + travel * progress.coerceIn(0f, 1f)
        }
    }

    /**
     * 处理页码滑块的连续拖动导航。
     * MOVE 阶段实时预览页面；UP 阶段确定目标并恢复自动同步。父容器在拖动期间不得拦截事件。
     */
    private fun processScrubberGesture(event: MotionEvent): Boolean {
        if (pageCount <= 1) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isScrubberDragging = true
                binding.viewerRoot.parent?.requestDisallowInterceptTouchEvent(true)
                previewScrubbedPage(resolveScrubberPage(event.rawY))
                revealPageIndicator()
                true
            }
            MotionEvent.ACTION_MOVE -> {
                previewScrubbedPage(resolveScrubberPage(event.rawY))
                revealPageIndicator()
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val targetPage = if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    lastScrubberRequestedPage
                } else {
                    resolveScrubberPage(event.rawY)
                }
                isScrubberDragging = false
                binding.viewerRoot.parent?.requestDisallowInterceptTouchEvent(false)
                binding.pdfView.jumpTo(targetPage - 1, false)
                syncScrubberPosition(targetPage, pageCount)
                concealPageIndicator()
                true
            }
            else -> false
        }
    }

    /**
     * 将屏幕 rawY 换算为从 1 开始的文档页码。
     * 先减去容器的屏幕坐标，再把滑块顶部限制到合法行程，最后线性映射到页数。
     */
    private fun resolveScrubberPage(rawY: Float): Int {
        val scrubber = binding.pageScrubber
        val container = binding.viewerRoot
        if (scrubber.height == 0 || container.height == 0) return lastScrubberRequestedPage
        val containerLocation = IntArray(2)
        container.getLocationOnScreen(containerLocation)
        val touchY = rawY - containerLocation[1]
        val top = binding.pdfView.top.toFloat()
        val travel = (binding.pdfView.height - scrubber.height).coerceAtLeast(0).toFloat()
        val targetY = (touchY - scrubber.height / 2f).coerceIn(top, top + travel)
        scrubber.y = targetY
        val progress = if (travel == 0f) 0f else (targetY - top) / travel
        val targetPage = (1 + (pageCount - 1) * progress).roundToInt().coerceIn(1, pageCount)
        binding.pageScrubberText.text = targetPage.toString()
        binding.pageInfoCurrent.text = targetPage.toString()
        return targetPage
    }

    /** 预览拖动位置对应的页面；相同页码不重复调用 jumpTo，减少无效渲染。 */
    private fun previewScrubbedPage(page: Int) {
        val targetPage = page.coerceIn(1, pageCount)
        if (targetPage == lastScrubberRequestedPage) return
        lastScrubberRequestedPage = targetPage
        binding.pdfView.jumpTo(targetPage - 1, false)
    }

    /** 显示临时页码位置提示。 */
    private fun revealPageIndicator() {
        binding.pageInfo.animate().cancel()
        binding.pageInfo.alpha = 1f
        binding.pageInfo.visibility = View.VISIBLE
    }

    /** 渐隐临时页码位置提示。 */
    private fun concealPageIndicator() {
        binding.pageInfo.animate().cancel()
        binding.pageInfo.animate()
            .alpha(0f)
            .setDuration(500L)
            .withEndAction { binding.pageInfo.visibility = View.GONE }
            .start()
    }

    /** 显示编辑工具对应的简短提示。 */
    private fun displayToolHint(messageResId: Int) {
        Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        // PDFium 持有原生句柄和渲染资源，Activity 销毁时必须显式释放。
        clearUndoHistory()
        pendingAnnotationEdits.clear()
        binding.annotationOverlay.clearTextMarkupPreviews()
        binding.annotationOverlay.clearInkPreviews()
        binding.pdfView.recycle()
        // PDF14-MIGRATION: 与原基类一致，销毁时解绑当前 Activity 的阅读历史会话。
        LocalCacheStore.detach(this)
        super.onDestroy()
    }



    companion object {
        /** Intent 参数。 */
        private const val EXTRA_FILE_NAME = "pdf_file_name"
        private const val EXTRA_FILE_PATH = "pdf_file_path"
        private const val EXTRA_RECORD_HISTORY = "pdf_record_history"
        private const val EXTRA_INITIAL_PAGE = "pdf_initial_page"

        /**
         * PDF14-MIGRATION: 现有功能页（文件列表、主页最近文件、扫描结果、合并结果）的统一入口。
         * 直接进入阅读页，不再经过已删除的 FunActivity 转发层；完整透传数据以保留 fileId 和历史。
         * [fromType] 仅为兼容旧调用保留，int 来源标记不是 pdf14 的转换工具类型。
         */
        @Suppress("UNUSED_PARAMETER")
        fun startFileActivity(activity: Activity, data: FilesData, fromType: Int = 0) {
            activity.startActivity(
                Intent(activity, ReaderSurfaceActivity::class.java).apply {
                    putExtra(EXTRA_FILE_NAME, data.fileName)
                    putExtra(EXTRA_FILE_PATH, data.filePath)
                    putExtra(EXTRA_RECORD_HISTORY, true)
                    putExtra(LocalCacheStore.EXTRA_FILES_DATA, DataUtils.toJson(data))
                }
            )
        }

        /** Activity 业务工具编号；不直接暴露给 PDFium 或 PDFBox。 */
        private const val TOOL_NONE = -1
        private const val TOOL_TEXT = 0
        private const val TOOL_IMAGE = 1
        private const val TOOL_SIGNATURE = 2
        private const val TOOL_DRAW = 3
        private const val TOOL_ERASER = 4
        private const val TOOL_HIGHLIGHT = 5
        private const val TOOL_UNDERLINE = 6

        /** 编辑缓存、坐标稳定性、图片内存和动画相关参数。 */
        private const val MAX_UNDO_COUNT = 10
        private const val SELECTION_EDGE_INSET = 0.0005f
        private const val MAX_IMAGE_DIMENSION = 2048
        private const val PANEL_SHOW_DURATION = 260L
        private const val PANEL_HIDE_DURATION = 220L
        private const val TOP_BAR_SHOW_DURATION = 220L
        private const val TOP_BAR_HIDE_DURATION = 180L
        private const val COLOR_SELECTED_SCALE = 1.25f
        private const val COLOR_NORMAL_SCALE = 1f
        private const val COLOR_SCALE_DURATION = 140L

        /** 打开可读取的 PDF，并定位到最近访问的页面。 */
        // PDF14-MIGRATION: 不引用源项目合并/转换成功页；历史记录仅由调用参数控制。
        fun open(context: Context, fileName: String, absolutePath: String, type: String? = "",
            initialPageIndex: Int = -1,
            recordHistory: Boolean = type.isNullOrEmpty()) {
            context.startActivity(
                Intent(context, ReaderSurfaceActivity::class.java).apply {
                    putExtra(EXTRA_FILE_NAME, fileName)
                    putExtra(EXTRA_FILE_PATH, absolutePath)
                    putExtra("fromType", type)
                    putExtra(EXTRA_RECORD_HISTORY, recordHistory)
                    // >= 0 才是明确指定的源页下标，未指定时沿用页面初始化中的默认值。
                    if (initialPageIndex >= 0) putExtra(EXTRA_INITIAL_PAGE, initialPageIndex)
                }
            )
        }
    }
}
