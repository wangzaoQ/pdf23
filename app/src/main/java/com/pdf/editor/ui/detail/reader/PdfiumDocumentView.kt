// PDF14-MIGRATION: 从 pdf14 原样迁移；本文件仅变更包名，未修改业务逻辑。
package com.pdf.editor.ui.detail.reader

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.util.LruCache
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfiumCore
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 基于 PDFiumAndroidKt 的原生 PDF 阅读与文字几何查询视图。
 *
 * PDFiumAndroidKt 只暴露“打开页、渲染、读取文字/链接”等底层能力，本类补齐产品需要的：
 * 连续纵向排版、滚动/惯性、双指和双击缩放、页面跳转、位图缓存、链接点击，以及供编辑层
 * 使用的坐标转换和文字选择。
 *
 * 线程模型：
 * - 主线程维护 View 状态、页面布局、滚动位置及所有回调；
 * - render executor 串行执行页面位图渲染；
 * - text executor 串行读取字符框；
 * - PDFium 的同一 PdfDocument 不能并发访问，两条后台线程最终通过 documentLock 串行化。
 *
 * 对外编辑坐标一律采用“页面左上角为原点、0..1 归一化”，从而不受屏幕宽度、zoom、滚动
 * 和渲染位图分辨率影响。
 */
class PdfiumDocumentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** 页面在 zoom=1 时的 View 布局尺寸，单位为像素。 */
    data class PageSize(val width: Float, val height: Float)

    /** 页面内归一化点；x/y 均限制在 0..1，原点位于显示页面左上角。 */
    data class PagePosition(
        val pageIndex: Int,
        val xFraction: Float,
        val yFraction: Float
    )

    /** 页面内归一化矩形，用于文字选择、批注和预览重映射。 */
    data class PageRect(
        val pageIndex: Int,
        val leftFraction: Float,
        val topFraction: Float,
        val rightFraction: Float,
        val bottomFraction: Float
    )

    /** PDFium 按 72dpi 返回的原始页面宽高。 */
    private data class RawPageSize(val width: Int, val height: Int)

    /** 位图缓存键包含目标像素尺寸，同一页不同缩放级别不会错误复用。 */
    private data class RenderKey(val pageIndex: Int, val width: Int, val height: Int)

    /** 单个 PDF 字符及其归一化包围框；lineIndex 用来合并连续字符为逐行矩形。 */
    private data class TextCharacter(val unicode: Char, val bounds: RectF, val lineIndex: Int)

    /** 某一页完整的字符布局缓存，首次建立后手柄拖动不再调用 PDFium。 */
    private data class TextLayout(
        val pageIndex: Int,
        val characters: List<TextCharacter?>,
        val heightToWidthRatio: Float
    )

    /** 字符布局尚未就绪时暂存的选择请求；布局完成后在主线程按顺序回调。 */
    private data class TextSelectionRequest(
        val start: PagePosition,
        val end: PagePosition,
        val expandWord: Boolean,
        val callback: (List<PageRect>, String) -> Unit
    )

    /** render/text 两个 executor 共享同一 native document，任何 PDFium 调用都必须持有此锁。 */
    private val documentLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val placeholderPaint = Paint().apply { color = Color.WHITE }
    private val scroller = OverScroller(context)
    // pending 集合既负责去重，也避免同一页在连续 invalidate 中重复排队。
    private val pendingRenders = mutableSetOf<RenderKey>()
    private val textLayouts = mutableMapOf<Int, TextLayout>()
    private val pendingTextRequests = mutableMapOf<Int, MutableList<TextSelectionRequest>>()
    private val pendingTextLoads = mutableSetOf<Int>()
    private val pendingPageMetadata = mutableSetOf<Int>()
    private val resolvedPageMetadata = mutableSetOf<Int>()
    /** LRU 淘汰时立即 recycle native 像素内存，避免长文档快速滑动导致峰值过高。 */
    private val bitmapCache = object : LruCache<RenderKey, Bitmap>(cacheSizeKb()) {
        override fun sizeOf(key: RenderKey, value: Bitmap): Int = value.allocationByteCount / 1024

        override fun entryRemoved(
            evicted: Boolean,
            key: RenderKey,
            oldValue: Bitmap,
            newValue: Bitmap?
        ) {
            if (oldValue !== newValue && !oldValue.isRecycled) oldValue.recycle()
        }
    }

    private var executor: ExecutorService? = null
    private var textExecutor: ExecutorService? = null
    private var metadataExecutor: ExecutorService? = null
    private var document: PdfDocument? = null
    private var rawPageSizes = mutableListOf<RawPageSize>()
    private var fittedPageSizes = emptyList<PageSize>()
    private var pageTops = FloatArray(0)
    private var contentWidth = 0f
    private var contentHeight = 0f
    private var requestedDefaultPage = 0
    /** 每次 load/recycle 递增；后台旧任务回主线程时据此丢弃过期结果。 */
    private var loadGeneration = 0
    private var firstRenderDelivered = false
    private var metadataReady = false
    private var renderGeneration = 0
    private var isScaling = false

    var currentPage: Int = 0
        private set

    var pageCount: Int = 0
        private set

    var zoom: Float = MIN_ZOOM
        private set

    val isSwipeVertical: Boolean = true
    val currentXOffset: Float get() = -scrollX.toFloat()
    val currentYOffset: Float get() = -scrollY.toFloat()

    private var onLoad: ((Int) -> Unit)? = null
    private var onPageChange: ((Int, Int) -> Unit)? = null
    private var onFirstRender: (() -> Unit)? = null
    private var onTap: ((Float, Float) -> Boolean)? = null
    private var onError: ((Throwable) -> Unit)? = null
    var onViewportChanged: (() -> Unit)? = null

    // ---------- PDF 页面自身的缩放、滚动和点击手势 ----------
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                scroller.abortAnimation()
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                setZoomAround(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean {
                scroller.abortAnimation()
                return true
            }

            override fun onScroll(
                first: MotionEvent?,
                current: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (!isScaling) scrollByClamped(distanceX.roundToInt(), distanceY.roundToInt())
                return true
            }

            override fun onFling(
                first: MotionEvent?,
                current: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                scroller.fling(
                    scrollX,
                    scrollY,
                    (-velocityX).roundToInt(),
                    (-velocityY).roundToInt(),
                    0,
                    maxScrollX(),
                    0,
                    maxScrollY()
                )
                postInvalidateOnAnimation()
                return true
            }

            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                performClick()
                val consumed = onTap?.invoke(event.x, event.y) == true
                if (!consumed) openLinkAt(event.x, event.y)
                return consumed
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                val target = if (zoom > MIN_ZOOM + 0.1f) MIN_ZOOM else DOUBLE_TAP_ZOOM
                setZoomAround(target / zoom, event.x, event.y)
                return true
            }
        }
    )

    /**
     * 异步打开文件并读取全部页面尺寸。所有回调均在主线程触发。
     *
     * 页面尺寸先全部读取，再一次性建立连续布局，避免用户滑动过程中页面高度不断变化。
     * load 开头调用 recycle，因此同一个 View 可以安全重载保存后的原文件。
     */
    fun load(
        file: File,
        defaultPage: Int = 0,
        onLoad: (Int) -> Unit = {},
        onPageChange: (Int, Int) -> Unit = { _, _ -> },
        onFirstRender: () -> Unit = {},
        onTap: (Float, Float) -> Boolean = { _, _ -> false },
        onError: (Throwable) -> Unit = {}
    ) {
        recycle()
        this.onLoad = onLoad
        this.onPageChange = onPageChange
        this.onFirstRender = onFirstRender
        this.onTap = onTap
        this.onError = onError
        requestedDefaultPage = defaultPage.coerceAtLeast(0)
        if (width == 0) {
            post { load(file, defaultPage, onLoad, onPageChange, onFirstRender, onTap, onError) }
            return
        }

        val generation = loadGeneration
        val worker = Executors.newSingleThreadExecutor()
        executor = worker
        textExecutor = Executors.newSingleThreadExecutor()
        metadataExecutor = Executors.newSingleThreadExecutor()
        worker.execute {
            var descriptor: ParcelFileDescriptor? = null
            var openedDocument: PdfDocument? = null
            try {
                descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                openedDocument = PdfiumCore(context.applicationContext).newDocument(descriptor)
                descriptor = null // PdfDocument owns it after a successful open.
                val count = openedDocument.getPageCount()
                require(count > 0) { "PDF has no pages" }
                val cacheKey = file.absolutePath + ":" + file.length() + ":" + file.lastModified()
                val cachedSizes = synchronized(PAGE_SIZE_CACHE) { PAGE_SIZE_CACHE.get(cacheKey) }
                val initialSize = cachedSizes?.getOrNull(requestedDefaultPage.coerceIn(0, count - 1))
                    ?: synchronized(documentLock) {
                        openedDocument.openPage(requestedDefaultPage.coerceIn(0, count - 1))?.use { page ->
                            val size = page.getPageSize(PDF_POINT_DPI)
                            RawPageSize(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
                        } ?: RawPageSize(595, 842)
                    }
                val initialSizes = cachedSizes ?: MutableList(count) { initialSize }
                mainHandler.post {
                    if (generation != loadGeneration) {
                        openedDocument.close()
                        return@post
                    }
                    synchronized(documentLock) { document = openedDocument }
                    rawPageSizes = initialSizes.toMutableList()
                    resolvedPageMetadata.clear()
                    pageCount = count
                    // 页面按需渲染；尚未访问的页面沿用当前页版式占位，不做全量元数据扫描。
                    metadataReady = true
                    zoom = MIN_ZOOM
                    rebuildLayout()
                    currentPage = requestedDefaultPage.coerceIn(0, count - 1)
                    resolvedPageMetadata.add(currentPage)
                    scrollToPage(currentPage)
                    // 当前页字符框提前缓存，用户第一次长按不会等待一次完整 PDFium 查询。
                    preloadTextLayout(currentPage)
                    this.onLoad?.invoke(count)
                    this.onPageChange?.invoke(currentPage, count)
                    invalidate()
                }
            } catch (error: Throwable) {
                descriptor?.close()
                openedDocument?.close()
                mainHandler.post {
                    if (generation == loadGeneration) this.onError?.invoke(error)
                }
            }
        }
    }

    /** 跳转到目标页顶部；动画模式使用 OverScroller 保持与惯性滚动一致。 */
    fun jumpTo(pageIndex: Int, withAnimation: Boolean) {
        if (pageCount == 0) return
        val target = pageIndex.coerceIn(0, pageCount - 1)
        renderGeneration++
        pendingRenders.clear()
        // 丢弃跳转前排队的整页任务，目标页直接成为队列中的第一项。
        executor?.shutdownNow()
        executor = Executors.newSingleThreadExecutor()
        // 先按稳定占位位置跳转，目标页进入视口后再读取真实尺寸；锚点补偿
        // 会修正最终位置，不需要依次打开中间页面。
        schedulePageMetadata(target)
        scheduleRender(renderKey(target, pageRect(target)))
        val targetY = (pageTops[target] * zoom).roundToInt().coerceIn(0, maxScrollY())
        if (withAnimation) {
            scroller.startScroll(scrollX, scrollY, 0, targetY - scrollY, PAGE_JUMP_DURATION_MS)
            postInvalidateOnAnimation()
        } else {
            scrollTo(scrollX.coerceIn(0, maxScrollX()), targetY)
            updateCurrentPage()
            invalidate()
        }
    }

    fun getPageSize(pageIndex: Int): PageSize =
        fittedPageSizes.getOrElse(pageIndex) { PageSize(0f, 0f) }

    /**
     * 将视图坐标转换为页面归一化坐标。
     *
     * pageIndex=null 时只接受真正落在某页内的点；拖动手柄/画笔时传入固定页面并设置
     * clamp=true，可将越界手指裁剪到该页边缘，避免一次批注跨页产生非法坐标。
     */
    fun mapViewToPage(
        viewX: Float,
        viewY: Float,
        pageIndex: Int? = null,
        clamp: Boolean = false
    ): PagePosition? {
        if (pageCount == 0) return null
        val documentX = scrollX + viewX
        val documentY = scrollY + viewY
        val resolvedPage = pageIndex ?: (0 until pageCount).firstOrNull {
            pageRect(it).contains(documentX, documentY)
        } ?: return null
        if (resolvedPage !in 0 until pageCount) return null
        val rect = pageRect(resolvedPage)
        if (!clamp && !rect.contains(documentX, documentY)) return null
        return PagePosition(
            pageIndex = resolvedPage,
            xFraction = ((documentX - rect.left) / rect.width()).coerceIn(0f, 1f),
            yFraction = ((documentY - rect.top) / rect.height()).coerceIn(0f, 1f)
        )
    }

    /** 将页面归一化矩形转换回当前缩放、滚动状态下的视图坐标。 */
    fun mapPageRectToView(rect: PageRect): RectF? {
        if (rect.pageIndex !in 0 until pageCount) return null
        val page = pageRect(rect.pageIndex)
        return RectF(
            page.left - scrollX + rect.leftFraction * page.width(),
            page.top - scrollY + rect.topFraction * page.height(),
            page.left - scrollX + rect.rightFraction * page.width(),
            page.top - scrollY + rect.bottomFraction * page.height()
        )
    }

    /** 将归一化页面点映射到当前屏幕，用于重绘待保存的画笔轨迹。 */
    fun mapPagePositionToView(position: PagePosition): Pair<Float, Float>? {
        if (position.pageIndex !in 0 until pageCount) return null
        val page = pageRect(position.pageIndex)
        return Pair(
            page.left - scrollX + position.xFraction * page.width(),
            page.top - scrollY + position.yFraction * page.height()
        )
    }

    /** 将当前渲染缓存中的页面局部绘制到放大镜；拖动时只做一次位图裁剪，不触发 PDF 重渲染。 */
    fun drawMagnifier(canvas: Canvas, sourceX: Float, sourceY: Float, destX: Float, destY: Float, radius: Float) {
        val documentX = scrollX + sourceX
        val documentY = scrollY + sourceY
        val pageIndex = (0 until pageCount).firstOrNull { pageRect(it).contains(documentX, documentY) } ?: return
        val rect = pageRect(pageIndex)
        val bitmap = bitmapCache.get(renderKey(pageIndex, rect)) ?: return
        val srcX = ((documentX - rect.left) / rect.width() * bitmap.width).roundToInt()
        val srcY = ((documentY - rect.top) / rect.height() * bitmap.height).roundToInt()
        // 只取手柄附近的选区和下一行，避免放大窗展示整块页面。
        val srcRadius = (radius / rect.width() * bitmap.width * 1.15f).roundToInt().coerceAtLeast(2)
        val src = Rect((srcX - srcRadius).coerceIn(0, bitmap.width - 1), (srcY - srcRadius).coerceIn(0, bitmap.height - 1),
            (srcX + srcRadius).coerceIn(1, bitmap.width), (srcY + srcRadius).coerceIn(1, bitmap.height))
        val dst = RectF(destX - radius, destY - radius, destX + radius, destY + radius)
        canvas.drawBitmap(bitmap, src, dst, pagePaint)
    }

    /**
     * 返回 start/end 覆盖的真实字符行矩形。
     *
     * 缓存命中时 callback 会在当前主线程调用栈内同步返回，这是拖动手柄不卡的关键；缓存
     * 未命中时请求会合并到 pendingTextRequests，只启动一次整页字符读取。
     */
    fun findTextRects(
        start: PagePosition,
        end: PagePosition,
        expandSingleCharacterToWord: Boolean = false,
        callback: (List<PageRect>, String) -> Unit
    ) {
        if (start.pageIndex != end.pageIndex || start.pageIndex !in 0 until pageCount) {
            callback(emptyList(), "")
            return
        }
        val pageIndex = start.pageIndex
        textLayouts[pageIndex]?.let { layout ->
            val resolved = resolveTextSelection(layout, start, end, expandSingleCharacterToWord)
            callback(resolved.first, resolved.second)
            return
        }
        val request = TextSelectionRequest(start, end, expandSingleCharacterToWord, callback)
        val requests = pendingTextRequests.getOrPut(pageIndex) { mutableListOf() }
        requests += request
        scheduleTextLayout(pageIndex)
    }

    /** 提前读取当前页的字符框，避免用户第一次长按时等待 PDFium。 */
    fun preloadTextLayout(pageIndex: Int) {
        if (pageIndex !in 0 until pageCount || textLayouts.containsKey(pageIndex)) return
        scheduleTextLayout(pageIndex)
    }

    /** 对同一页去重后，在专用文字线程构建布局，并统一回放等待中的选择请求。 */
    private fun scheduleTextLayout(pageIndex: Int) {
        if (!pendingTextLoads.add(pageIndex)) return

        val displayRect = pageRect(pageIndex)
        val displayWidth = displayRect.width().roundToInt().coerceAtLeast(1)
        val displayHeight = displayRect.height().roundToInt().coerceAtLeast(1)
        val generation = loadGeneration
        val worker = textExecutor ?: run {
            pendingTextLoads.remove(pageIndex)
            pendingTextRequests.remove(pageIndex).orEmpty().forEach { it.callback(emptyList(), "") }
            return
        }
        try {
            worker.execute {
                val layout = synchronized(documentLock) {
                    if (generation != loadGeneration) return@synchronized null
                    buildTextLayout(pageIndex, displayWidth, displayHeight)
                }
                mainHandler.post {
                    if (generation != loadGeneration) return@post
                    pendingTextLoads.remove(pageIndex)
                    val waiting = pendingTextRequests.remove(pageIndex).orEmpty()
                    if (layout == null) {
                        waiting.forEach { it.callback(emptyList(), "") }
                        return@post
                    }
                    textLayouts[pageIndex] = layout
                    waiting.forEach { pending ->
                        val resolved = resolveTextSelection(
                            layout,
                            pending.start,
                            pending.end,
                            pending.expandWord
                        )
                        pending.callback(resolved.first, resolved.second)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            pendingTextLoads.remove(pageIndex)
            pendingTextRequests.remove(pageIndex).orEmpty().forEach { it.callback(emptyList(), "") }
        }
    }

    /**
     * 通过 PDFium 读取整页字符、字符框和行框。
     *
     * PDFium 返回 PDF 坐标，先 mapRectToDevice 再归一化，得到与页面旋转一致的显示坐标。
     * 行框仅用于给字符编号，最终选择矩形仍由精确字符框 union 得到。
     */
    private fun buildTextLayout(pageIndex: Int, width: Int, height: Int): TextLayout? {
        return document?.openPage(pageIndex)?.use { page ->
            page.openTextPage().use { textPage ->
                val count = textPage.textPageCountChars()
                val lineCount = textPage.textPageCountRects(0, count)
                val lines = (0 until lineCount).mapNotNull { index ->
                    val source = textPage.textPageGetRect(index) ?: return@mapNotNull null
                    page.mapRectToDevice(0, 0, width, height, 0, source).toNormalizedRect(width, height)
                }
                val characters = (0 until count).map { index ->
                    val source = textPage.textPageGetCharBox(index) ?: return@map null
                    val bounds = page.mapRectToDevice(
                        0,
                        0,
                        width,
                        height,
                        0,
                        source
                    ).toNormalizedRect(width, height)
                    if (bounds.width() <= 0f || bounds.height() <= 0f) return@map null
                    val lineIndex = lines.indexOfFirst { line ->
                        bounds.centerX() >= line.left - LINE_MATCH_TOLERANCE &&
                            bounds.centerX() <= line.right + LINE_MATCH_TOLERANCE &&
                            bounds.centerY() >= line.top - LINE_MATCH_TOLERANCE &&
                            bounds.centerY() <= line.bottom + LINE_MATCH_TOLERANCE
                    }
                    TextCharacter(textPage.textPageGetUnicode(index), bounds, lineIndex)
                }
                TextLayout(pageIndex, characters, height.toFloat() / width)
            }
        }
    }

    /** 在纯内存字符布局中解析选区，并按文字行合并为一个或多个矩形。 */
    private fun resolveTextSelection(
        layout: TextLayout,
        start: PagePosition,
        end: PagePosition,
        expandWord: Boolean
    ): Pair<List<PageRect>, String> {
        var first = layout.nearestCharacter(start.xFraction, start.yFraction)
        var last = layout.nearestCharacter(end.xFraction, end.yFraction)
        if (first < 0 || last < 0) return emptyList<PageRect>() to ""
        if (first > last) {
            val temporary = first
            first = last
            last = temporary
        }
        fun isWordCharacter(index: Int): Boolean {
            val character = layout.characters.getOrNull(index)?.unicode ?: return false
            return !character.isCjkCharacter() &&
                (character.isLetterOrDigit() || character == '_' || character == '-')
        }
        fun isCjk(index: Int): Boolean = layout.characters.getOrNull(index)?.unicode?.isCjkCharacter() == true
        fun isSelectableCjk(index: Int): Boolean {
            val c = layout.characters.getOrNull(index)?.unicode ?: return false
            return c.isCjkCharacter() && c != '\r' && c != '\n' && !c.isWhitespace() && !c.isCjkPunctuation()
        }
        // 首次长按英文/数字时扩展为整词；中日韩文字保持单字符，避免整段被错误选中。
        if (expandWord && first == last && isWordCharacter(first)) {
            while (isWordCharacter(first - 1)) first--
            while (isWordCharacter(last + 1)) last++
            // 长按首次选中至少覆盖 5 个字母/数字，同时保持至少包含一个完整单词。
            // 短词会向相邻字符扩展，避免拖拽手柄过小、难以观察和调整。
            fun letterCount(from: Int, to: Int): Int = (from..to).count { index ->
                val c = layout.characters.getOrNull(index)?.unicode ?: return@count false
                c.isLetterOrDigit()
            }
            while (letterCount(first, last) < MIN_INITIAL_SELECTION_LETTERS) {
                val left = first - 1
                val right = last + 1
                when {
                    left >= 0 && layout.characters.getOrNull(left)?.unicode !in listOf('\r', '\n') -> first = left
                    right < layout.characters.size && layout.characters.getOrNull(right)?.unicode !in listOf('\r', '\n') -> last = right
                    else -> break
                }
            }
        } else if (expandWord && first == last && isCjk(first)) {
            // 中日韩没有可靠空格分词：以长按字符为中心，扩展到至少 5 个连续字符。
            val line = layout.characters.getOrNull(first)?.lineIndex ?: -1
            while (last - first + 1 < MIN_INITIAL_SELECTION_CJK) {
                val left = first - 1
                val right = last + 1
                val canLeft = left >= 0 && isSelectableCjk(left) && layout.characters[left]?.lineIndex == line
                val canRight = right < layout.characters.size && isSelectableCjk(right) && layout.characters[right]?.lineIndex == line
                when {
                    canLeft && (!canRight || first - (layout.characters.getOrNull(first)?.let { it.bounds.left } ?: 0f) >
                        ((layout.characters.getOrNull(last)?.let { it.bounds.right } ?: 0f) - (layout.characters.getOrNull(first)?.let { it.bounds.left } ?: 0f))) -> first--
                    canRight -> last++
                    canLeft -> first--
                    else -> break
                }
            }
        }

        val lineBounds = linkedMapOf<Int, RectF>()
        for (index in first..last) {
            val character = layout.characters.getOrNull(index) ?: continue
            if (character.unicode == '\r' || character.unicode == '\n') continue
            val lineKey = if (character.lineIndex >= 0) character.lineIndex else index
            val bounds = lineBounds[lineKey]
            if (bounds == null) lineBounds[lineKey] = RectF(character.bounds)
            else bounds.union(character.bounds)
        }
        val rects = lineBounds.values.map { bounds ->
            PageRect(
                layout.pageIndex,
                bounds.left.coerceIn(0f, 1f),
                bounds.top.coerceIn(0f, 1f),
                bounds.right.coerceIn(0f, 1f),
                bounds.bottom.coerceIn(0f, 1f)
            )
        }
        val text = (first..last)
            .mapNotNull { layout.characters.getOrNull(it)?.unicode }
            .joinToString("")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        return rects to text
    }

    /**
     * 查找触点最近字符。y 距离按页面高宽比换算到与 x 相近的尺度，避免长页面上
     * 垂直距离在归一化后权重过小；超过阈值则视为点在空白区。
     */
    private fun TextLayout.nearestCharacter(x: Float, y: Float): Int {
        var nearest = -1
        var nearestDistance = Float.MAX_VALUE
        characters.forEachIndexed { index, character ->
            val bounds = character?.bounds ?: return@forEachIndexed
            val dx = when {
                x < bounds.left -> bounds.left - x
                x > bounds.right -> x - bounds.right
                else -> 0f
            }
            val dy = when {
                y < bounds.top -> bounds.top - y
                y > bounds.bottom -> y - bounds.bottom
                else -> 0f
            }
            val scaledY = dy * heightToWidthRatio
            val distance = dx * dx + scaledY * scaledY
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = index
            }
        }
        return if (nearestDistance <= MAX_TEXT_HIT_FRACTION * MAX_TEXT_HIT_FRACTION) nearest else -1
    }

    private fun Char.isCjkCharacter(): Boolean {
        return code in 0x3400..0x9FFF ||
            code in 0x3040..0x30FF ||
            code in 0xAC00..0xD7AF
    }

    private fun Char.isCjkPunctuation(): Boolean = this in "，。！？；：、（）【】「」『』《》〈〉…—·～,.!?;:()[]"

    private fun Rect.toNormalizedRect(width: Int, height: Int): RectF {
        return RectF(
            minOf(left, right).toFloat() / width,
            minOf(top, bottom).toFloat() / height,
            maxOf(left, right).toFloat() / width,
            maxOf(top, bottom).toFloat() / height
        )
    }

    /**
     * 关闭文档、两个后台线程与所有位图/文字缓存；之后仍可再次调用 [load]。
     * generation 先递增，确保已经发出的旧任务即使无法立即中断，也不能覆盖新文档状态。
     */
    fun recycle() {
        loadGeneration++
        executor?.shutdownNow()
        executor = null
        textExecutor?.shutdownNow()
        textExecutor = null
        textLayouts.clear()
        pendingTextRequests.clear()
        pendingTextLoads.clear()
        pendingPageMetadata.clear()
        resolvedPageMetadata.clear()
        metadataExecutor?.shutdownNow()
        metadataExecutor = null
        pendingRenders.clear()
        renderGeneration++
        synchronized(documentLock) {
            document?.close()
            document = null
        }
        bitmapCache.evictAll()
        rawPageSizes = mutableListOf()
        fittedPageSizes = emptyList()
        pageTops = FloatArray(0)
        pageCount = 0
        currentPage = 0
        firstRenderDelivered = false
        metadataReady = false
        scrollTo(0, 0)
        invalidate()
    }

    /** 只遍历并渲染可见页；精确尺寸位图未就绪时允许先显示同页较低分辨率缓存。 */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pageCount == 0) return
        val visibleTop = scrollY.toFloat()
        val visibleBottom = visibleTop + height
        val firstPage = pageTops.lowerBound(visibleTop / zoom).let { index ->
            (index - 1).coerceIn(0, pageCount - 1)
        }
        val lastPage = pageTops.lowerBound(visibleBottom / zoom).coerceIn(firstPage, pageCount - 1)
        for (pageIndex in firstPage..lastPage) {
            val pageRect = pageRect(pageIndex)
            if (pageRect.bottom < visibleTop || pageRect.top > visibleBottom) continue
            canvas.drawRect(pageRect, placeholderPaint)
            val key = renderKey(pageIndex, pageRect)
            val exact = bitmapCache.get(key)
            val bitmap = exact ?: bestCachedBitmap(pageIndex)
            if (bitmap != null && !bitmap.isRecycled) {
                canvas.drawBitmap(bitmap, null, pageRect, pagePaint)
            }
            if (exact == null && (!isScaling || bitmap == null)) scheduleRender(key)
            schedulePageMetadata(pageIndex)
        }
    }

    private fun schedulePageMetadata(pageIndex: Int) {
        if (pageIndex !in 0 until pageCount || metadataExecutor == null) return
        if (pageIndex == currentPage || resolvedPageMetadata.contains(pageIndex) || !pendingPageMetadata.add(pageIndex)) return
        val generation = loadGeneration
        try {
            metadataExecutor?.execute {
                val resolved = synchronized(documentLock) {
                    if (generation != loadGeneration) null else document?.openPage(pageIndex)?.use { page ->
                        val size = page.getPageSize(PDF_POINT_DPI)
                        RawPageSize(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
                    }
                } ?: return@execute
                mainHandler.post {
                    pendingPageMetadata.remove(pageIndex)
                    if (generation != loadGeneration || rawPageSizes[pageIndex] == resolved) return@post
                    val anchorPage = currentPage.coerceIn(0, pageCount - 1)
                    val anchorOffset = scrollY - pageTops.getOrNull(anchorPage).orZero() * zoom
                    rawPageSizes[pageIndex] = resolved
                    resolvedPageMetadata.add(pageIndex)
                    rebuildLayout()
                    scrollTo(scrollX, (pageTops[anchorPage] * zoom + anchorOffset).roundToInt()
                        .coerceIn(0, maxScrollY()))
                    invalidate()
                }
            }
        } catch (_: RejectedExecutionException) {
            pendingPageMetadata.remove(pageIndex)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            updateCurrentPage()
            postInvalidateOnAnimation()
        }
    }

    override fun onScrollChanged(left: Int, top: Int, oldLeft: Int, oldTop: Int) {
        super.onScrollChanged(left, top, oldLeft, oldTop)
        onViewportChanged?.invoke()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (rawPageSizes.isNotEmpty() && width != oldWidth) {
            val page = currentPage
            rebuildLayout()
            scrollToPage(page)
        }
    }

    /** 按最宽页面适配 View 宽度，计算所有页面的 zoom=1 尺寸和累计 top。 */
    private fun rebuildLayout() {
        if (rawPageSizes.isEmpty() || width <= 0) return
        val widest = rawPageSizes.maxOf { it.width }.toFloat()
        val fitScale = width / widest
        fittedPageSizes = rawPageSizes.map { raw ->
            PageSize(raw.width * fitScale, raw.height * fitScale)
        }
        pageTops = FloatArray(fittedPageSizes.size)
        var top = 0f
        fittedPageSizes.forEachIndexed { index, size ->
            pageTops[index] = top
            top += size.height
        }
        contentWidth = fittedPageSizes.maxOfOrNull { it.width } ?: 0f
        contentHeight = top
        // 页面尺寸更新时保留已有整页 Bitmap，避免当前页先变白再等待重渲染。
        // 新尺寸对应的 RenderKey 会在下一帧按需补齐，旧图由 LruCache 自然淘汰。
    }

    /** 计算页面在当前 zoom 和连续文档坐标中的实际矩形，窄页沿水平方向居中。 */
    private fun pageRect(pageIndex: Int): RectF {
        val size = fittedPageSizes[pageIndex]
        val scaledWidth = size.width * zoom
        val scaledHeight = size.height * zoom
        val fullWidth = maxOf(width.toFloat(), contentWidth * zoom)
        val left = (fullWidth - scaledWidth) / 2f
        val top = pageTops[pageIndex] * zoom
        return RectF(left, top, left + scaledWidth, top + scaledHeight)
    }

    /**
     * 根据屏幕目标尺寸生成渲染键，同时限制最大边和总像素数，防止高倍缩放 OOM。
     */
    private fun renderKey(pageIndex: Int, pageRect: RectF): RenderKey {
        var renderWidth = pageRect.width().roundToInt().coerceAtLeast(1)
        var renderHeight = pageRect.height().roundToInt().coerceAtLeast(1)
        val dimensionScale = min(1f, MAX_BITMAP_DIMENSION / maxOf(renderWidth, renderHeight).toFloat())
        val areaScale = min(1f, sqrt(MAX_BITMAP_PIXELS / (renderWidth.toDouble() * renderHeight)).toFloat())
        val scale = min(dimensionScale, areaScale)
        renderWidth = (renderWidth * scale).roundToInt().coerceAtLeast(1)
        renderHeight = (renderHeight * scale).roundToInt().coerceAtLeast(1)
        return RenderKey(pageIndex, renderWidth, renderHeight)
    }

    private fun bestCachedBitmap(pageIndex: Int): Bitmap? = bitmapCache.snapshot()
        .asSequence()
        .filter { (key, bitmap) -> key.pageIndex == pageIndex && !bitmap.isRecycled }
        .maxByOrNull { (key, _) -> key.width }
        ?.value

    /** 后台渲染单页；只有 generation 仍匹配时才把位图交回缓存。 */
    private fun scheduleRender(key: RenderKey) {
        if (!pendingRenders.add(key)) return
        val generation = loadGeneration
        val renderGenerationAtSubmit = renderGeneration
        val worker = executor
        if (worker == null) {
            pendingRenders.remove(key)
            return
        }
        try {
            worker.execute {
                var bitmap: Bitmap? = null
                try {
                    val renderedBitmap = Bitmap.createBitmap(key.width, key.height, Bitmap.Config.RGB_565)
                    bitmap = renderedBitmap
                    var rendered = false
                    synchronized(documentLock) {
                        if (generation != loadGeneration || renderGenerationAtSubmit != renderGeneration) return@synchronized
                        document?.openPage(key.pageIndex)?.use { page ->
                            page.renderPageBitmap(
                                renderedBitmap,
                                0,
                                0,
                                key.width,
                                key.height,
                                renderAnnot = true,
                                canvasColor = Color.WHITE,
                                pageBackgroundColor = Color.WHITE
                            )
                            rendered = true
                        }
                    }
                    if (!rendered) {
                        renderedBitmap.recycle()
                        mainHandler.post {
                            if (renderGenerationAtSubmit == renderGeneration) pendingRenders.remove(key)
                        }
                        return@execute
                    }
                    mainHandler.post {
                        if (renderGenerationAtSubmit == renderGeneration) pendingRenders.remove(key)
                        if (generation != loadGeneration || renderGenerationAtSubmit != renderGeneration) {
                            renderedBitmap.recycle()
                            return@post
                        }
                        bitmapCache.put(key, renderedBitmap)
                        if (!firstRenderDelivered) {
                            firstRenderDelivered = true
                            onFirstRender?.invoke()
                        }
                        invalidate()
                    }
                } catch (_: Throwable) {
                    bitmap?.recycle()
                    mainHandler.post {
                        if (renderGenerationAtSubmit == renderGeneration) pendingRenders.remove(key)
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            pendingRenders.remove(key)
        }
    }

    /** 改变 zoom 后修正 scroll，使双指焦点对应的文档位置尽量保持在原屏幕位置。 */
    private fun setZoomAround(scaleFactor: Float, focusX: Float, focusY: Float) {
        val oldZoom = zoom
        val newZoom = (oldZoom * scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (abs(newZoom - oldZoom) < 0.001f) return
        val ratio = newZoom / oldZoom
        zoom = newZoom
        onViewportChanged?.invoke()
        val targetX = ((scrollX + focusX) * ratio - focusX).roundToInt()
        val targetY = ((scrollY + focusY) * ratio - focusY).roundToInt()
        scrollTo(targetX.coerceIn(0, maxScrollX()), targetY.coerceIn(0, maxScrollY()))
        updateCurrentPage()
        invalidate()
    }

    private fun scrollByClamped(dx: Int, dy: Int) {
        scrollTo((scrollX + dx).coerceIn(0, maxScrollX()), (scrollY + dy).coerceIn(0, maxScrollY()))
        updateCurrentPage()
        invalidate()
    }

    private fun scrollToPage(pageIndex: Int) {
        if (pageIndex !in pageTops.indices) return
        scrollTo(0, (pageTops[pageIndex] * zoom).roundToInt().coerceIn(0, maxScrollY()))
        updateCurrentPage()
    }

    /** 以视口中心最近的页面作为当前页，避免页缝位置频繁跳变。 */
    private fun updateCurrentPage() {
        if (pageCount == 0) return
        val viewportCenter = scrollY + height / 2f
        val resolved = (0 until pageCount).minByOrNull { index ->
            val rect = pageRect(index)
            abs(rect.centerY() - viewportCenter)
        } ?: 0
        if (resolved != currentPage) {
            currentPage = resolved
            preloadTextLayout(resolved)
            onPageChange?.invoke(resolved, pageCount)
        }
    }

    /** 异步命中当前页链接；页内跳转优先，URI 链接交给系统浏览器。 */
    private fun openLinkAt(viewX: Float, viewY: Float) {
        if (pageCount == 0) return
        val documentX = scrollX + viewX
        val documentY = scrollY + viewY
        val pageIndex = (0 until pageCount).firstOrNull { pageRect(it).contains(documentX, documentY) }
            ?: return
        val rect = pageRect(pageIndex)
        val localX = (documentX - rect.left).roundToInt()
        val localY = (documentY - rect.top).roundToInt()
        val generation = loadGeneration
        val worker = executor ?: return
        try {
            worker.execute {
                val link = synchronized(documentLock) {
                    if (generation != loadGeneration) return@synchronized null
                    document?.openPage(pageIndex)?.use { page ->
                        page.getPageLinks().firstOrNull { candidate ->
                            val bounds: Rect = page.mapRectToDevice(
                                0,
                                0,
                                rect.width().roundToInt(),
                                rect.height().roundToInt(),
                                0,
                                candidate.bounds
                            )
                            bounds.contains(localX, localY)
                        }
                    }
                }
                mainHandler.post {
                    if (generation != loadGeneration || link == null) return@post
                    link.destPageIdx?.let { jumpTo(it, true) }
                    link.uri?.let { uri ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                        }
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            // The view was recycled between the tap and task submission.
        }
    }

    private fun maxScrollX(): Int = (contentWidth * zoom - width).roundToInt().coerceAtLeast(0)

    private fun maxScrollY(): Int = (contentHeight * zoom - height).roundToInt().coerceAtLeast(0)

    private fun Float?.orZero(): Float = this ?: 0f

    private fun FloatArray.lowerBound(value: Float): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (this[middle] < value) low = middle + 1 else high = middle
        }
        return low
    }

    /** 使用 Java heap 的 1/8 作为缓存，并设置上下限适配不同内存级别设备。 */
    private fun cacheSizeKb(): Int {
        val runtimeLimitKb = (Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt()
        return runtimeLimitKb.coerceIn(MIN_CACHE_KB, MAX_CACHE_KB)
    }

    companion object {
        private val PAGE_SIZE_CACHE = object : LruCache<String, List<RawPageSize>>(6) {
            override fun sizeOf(key: String, value: List<RawPageSize>): Int = value.size
        }
        private const val MIN_INITIAL_SELECTION_LETTERS = 5
        private const val MIN_INITIAL_SELECTION_CJK = 5
        private const val PDF_POINT_DPI = 72
        private const val MIN_ZOOM = 1f
        private const val MAX_ZOOM = 4f
        private const val DOUBLE_TAP_ZOOM = 2f
        private const val PAGE_JUMP_DURATION_MS = 250
        private const val MAX_TEXT_HIT_FRACTION = 0.055f
        private const val LINE_MATCH_TOLERANCE = 0.004f
        private const val MAX_BITMAP_DIMENSION = 2048f
        private const val MAX_BITMAP_PIXELS = 4_000_000.0
        private const val MIN_CACHE_KB = 16 * 1024
        private const val MAX_CACHE_KB = 48 * 1024
    }
}
