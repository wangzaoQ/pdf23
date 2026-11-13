// PDF14-MIGRATION: 从 pdf14 原样迁移；本文件仅变更包名，未修改业务逻辑。
package com.pdf.editor.ui.detail.reader

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.widget.Magnifier
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

/**
 * 覆盖在 [PdfiumDocumentView] 上方的原生编辑手势层。
 *
 * 本 View 只负责交互和“尚未保存”的可视化，不直接读写 PDF：
 * - SELECT：区分长按选字、手柄拖动、普通点击、页面滚动和双指缩放；
 * - DRAW：采集归一化页面点，并绘制即时笔迹；
 * - ERASER：采集完整擦除轨迹并显示圆形擦头；
 * - 已失焦的文字标记和已完成的画笔保留为预览，直到用户保存或撤销。
 *
 * 所有持久坐标都使用页面 0..1 归一化值。屏幕坐标仅用于当前帧绘制，滚动或缩放后
 * 会通过 mapper 重新计算，因此预览不会漂移。
 */
class PdfAnnotationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** NONE 完全透传；其余模式由本层接收事件。阅读默认使用 SELECT。 */
    enum class Mode { NONE, SELECT, DRAW, ERASER }

    private enum class SelectionDrag { NONE, START, END }

    /** 切换模式时同时清除“正在进行”的手势，但不会删除已经暂存的编辑预览。 */
    var mode: Mode = Mode.NONE
        set(value) {
            field = value
            visibility = if (value == Mode.NONE) GONE else VISIBLE
            updatePaint()
            clearGesturePreview()
        }

    var color: Int = Color.RED
        set(value) {
            field = value
            updatePaint()
        }

    /*
     * 坐标桥接由 Activity 注入，避免本层持有 PDF View：
     * positionResolver：屏幕点 -> 页面点；两个 mapper：页面几何 -> 当前屏幕几何。
     */
    var positionResolver: ((Float, Float, Int?, Boolean) -> PdfiumDocumentView.PagePosition?)? = null
    var pageRectMapper: ((PdfiumDocumentView.PageRect) -> RectF?)? = null
    var pagePositionMapper: ((PdfiumDocumentView.PagePosition) -> Pair<Float, Float>?)? = null
    /** 请求 PDFium 根据起止锚点重新计算真实字符框。 */
    var onSelectionRequested: ((PdfiumDocumentView.PagePosition, PdfiumDocumentView.PagePosition, Boolean, Boolean) -> Unit)? = null
    /** 当前文字选区因点击其他位置失焦；Activity 可将其加入待保存操作。 */
    var onSelectionFocusLost: (() -> Unit)? = null
    /** 使用 PDF 阅读器的位图缓存绘制真实内容放大镜。 */
    var magnifierRenderer: ((Canvas, Float, Float, Float, Float, Float) -> Unit)? = null
    private var systemMagnifier: Magnifier? = null

    /** 使用包含 PDF 与选区层的根容器作为放大源，保证内容和选中状态完全同步。 */
    fun attachMagnifier(source: View) {
        systemMagnifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val size = (140f * resources.displayMetrics.density).toInt()
            val radius = size / 2f
            Magnifier.Builder(source)
                .setSize(size, size)
                .setCornerRadius(radius)
                .setElevation(8f * resources.displayMetrics.density)
                .build()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Android 9 supports Magnifier, but Builder requires Android 10.
            @Suppress("DEPRECATION")
            Magnifier(source)
        } else null
    }

    private fun showMagnifier(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val lift = 86f * resources.displayMetrics.density
            systemMagnifier?.show(x, y, x, y - lift)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            systemMagnifier?.show(x, y)
        }
    }

    private fun hideMagnifier() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) systemMagnifier?.dismiss()
    }
    /** SELECT 模式判定为滚动/缩放/点击后，将完整 MotionEvent 序列交回 PDF View。 */
    var navigationEventHandler: ((MotionEvent) -> Unit)? = null
    /** DRAW/ERASER 在一整次手势结束时各回调一次，不代表已经写入文件。 */
    var onInkStroke: ((List<PdfiumDocumentView.PagePosition>, Float) -> Unit)? = null
    var onEraseStroke: ((List<PdfiumDocumentView.PagePosition>, Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val handler = Handler(Looper.getMainLooper())
    private val actionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(64, 147, 241)
        alpha = 85
        style = Paint.Style.FILL
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(64, 147, 241)
        style = Paint.Style.FILL
    }
    // ---------- 当前手势和当前文字选区 ----------
    private val previewPath = Path()
    private val inkPoints = mutableListOf<PdfiumDocumentView.PagePosition>()
    private val selectedPageRects = mutableListOf<PdfiumDocumentView.PageRect>()
    private val selectedRects = mutableListOf<RectF>()
    // ---------- 已加入编辑会话、等待用户点击保存的预览 ----------
    private val markupPreviews = mutableListOf<TextMarkupPreview>()
    private val inkPreviews = mutableListOf<InkPreview>()
    private var selectionStart: PdfiumDocumentView.PagePosition? = null
    private var selectionEnd: PdfiumDocumentView.PagePosition? = null
    private var gestureStart: PdfiumDocumentView.PagePosition? = null
    private var selectionDrag = SelectionDrag.NONE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var forwardingNavigation = false
    private var longPressTriggered = false
    private var selectionMoved = false
    private var dragUsesHandleOffset = false
    private var downTime = 0L
    private var pendingLongPressPosition: PdfiumDocumentView.PagePosition? = null

    private data class TextMarkupPreview(
        val pageRects: List<PdfiumDocumentView.PageRect>,
        val color: Int,
        val underline: Boolean
    )

    private data class InkPreview(
        val id: Long,
        val points: List<PdfiumDocumentView.PagePosition>,
        val color: Int,
        val strokeWidthFraction: Float
    )

    /**
     * 长按计时任务使用 ACTION_DOWN 时捕获的页面位置，而不是超时后的屏幕位置。
     * 页面在等待期间即使发生很小的抖动，也不会导致首次选字锚点漂移。
     */
    private val beginLongPress = Runnable {
        if (mode != Mode.SELECT || forwardingNavigation) return@Runnable
        val position = pendingLongPressPosition ?: return@Runnable
        longPressTriggered = true
        selectionDrag = SelectionDrag.END
        selectionStart = position
        selectionEnd = position
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        onSelectionRequested?.invoke(position, position, true, true)
    }

    /** 是否存在仍带蓝色手柄、尚未失焦的活动文字选区。 */
    fun hasTextSelection(): Boolean = selectedPageRects.isNotEmpty()

    /** 返回副本供 Activity 暂存，避免异步保存时内部列表继续变化。 */
    fun getSelectedPageRects(): List<PdfiumDocumentView.PageRect> = selectedPageRects.toList()

    /** 接收 PDFium 解析完成的选区，同时记录归一化矩形和当前帧屏幕矩形。 */
    fun updateTextSelection(
        start: PdfiumDocumentView.PagePosition,
        end: PdfiumDocumentView.PagePosition,
        pageRects: List<PdfiumDocumentView.PageRect>,
        viewRects: List<RectF>
    ) {
        selectionStart = start
        selectionEnd = end
        selectedPageRects.clear()
        selectedPageRects += pageRects
        selectedRects.clear()
        selectedRects += viewRects
        invalidate()
    }

    /** 只清除活动选区，不影响已经加入待保存列表的高亮、下划线或画笔。 */
    fun clearTextSelection() {
        selectionStart = null
        selectionEnd = null
        selectedPageRects.clear()
        selectedRects.clear()
        selectionDrag = SelectionDrag.NONE
        invalidate()
    }

    /** 添加失焦后的文字批注预览；实际 PDF 直到用户点击保存才会修改。 */
    fun addTextMarkupPreview(
        pageRects: List<PdfiumDocumentView.PageRect>,
        color: Int,
        underline: Boolean
    ) {
        if (pageRects.isEmpty()) return
        markupPreviews += TextMarkupPreview(pageRects.toList(), color, underline)
        invalidate()
    }

    fun removeLastTextMarkupPreview() {
        if (markupPreviews.isNotEmpty()) markupPreviews.removeAt(markupPreviews.lastIndex)
        invalidate()
    }

    fun clearTextMarkupPreviews() {
        markupPreviews.clear()
        invalidate()
    }

    /** 添加已完成画笔的持久预览，线宽仍使用页面宽度比例。 */
    fun addInkPreview(
        id: Long,
        points: List<PdfiumDocumentView.PagePosition>,
        color: Int,
        strokeWidthFraction: Float
    ) {
        if (points.isEmpty()) return
        inkPreviews += InkPreview(id, points.toList(), color, strokeWidthFraction)
        invalidate()
    }

    /**
     * 在内存预览上应用一条待保存擦除轨迹。
     *
     * 画笔按采样点距离判断，文字标记按矩形扩展半径判断。这里移除的只是预览；Activity
     * 仍保留完整、有序的编辑操作，撤销时可以从操作列表重建所有预览。
     */
    fun applyEraserPreview(
        points: List<PdfiumDocumentView.PagePosition>,
        radiusFraction: Float
    ) {
        if (points.isEmpty()) return
        val radiusSquared = radiusFraction * radiusFraction
        inkPreviews.removeAll { ink ->
            ink.points.firstOrNull()?.pageIndex == points.first().pageIndex &&
                points.any { eraser ->
                    ink.points.any { point ->
                        val dx = eraser.xFraction - point.xFraction
                        val dy = eraser.yFraction - point.yFraction
                        dx * dx + dy * dy <= radiusSquared
                    }
                }
        }
        markupPreviews.removeAll { markup ->
            markup.pageRects.any { rect ->
                points.any { point ->
                    point.pageIndex == rect.pageIndex &&
                        point.xFraction >= rect.leftFraction - radiusFraction &&
                        point.xFraction <= rect.rightFraction + radiusFraction &&
                        point.yFraction >= rect.topFraction - radiusFraction &&
                        point.yFraction <= rect.bottomFraction + radiusFraction
                }
            }
        }
        invalidate()
    }

    fun clearInkPreviews() {
        inkPreviews.clear()
        invalidate()
    }

    /** 绘制顺序：待保存画笔 -> 待保存文字标记 -> 活动蓝色选区 -> 当前手势。 */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        inkPreviews.forEach { preview ->
            val mapper = pagePositionMapper ?: return@forEach
            val first = preview.points.firstOrNull()?.let(mapper) ?: return@forEach
            val path = Path().apply { moveTo(first.first, first.second) }
            preview.points.drop(1).forEach { point ->
                mapper(point)?.let { mapped -> path.lineTo(mapped.first, mapped.second) }
            }
            // 用整页矩形取得当前缩放后的页宽，再把归一化线宽还原为屏幕像素。
            val pageRect = preview.points.firstOrNull()?.let { point ->
                pageRectMapper?.invoke(
                    PdfiumDocumentView.PageRect(point.pageIndex, 0f, 0f, 1f, 1f)
                )
            }
            val paint = Paint(actionPaint).apply {
                color = preview.color
                style = Paint.Style.STROKE
                strokeWidth = preview.strokeWidthFraction * (pageRect?.width() ?: width.toFloat())
                alpha = 220
            }
            canvas.drawPath(path, paint)
        }
        markupPreviews.forEach { preview ->
            val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = preview.color
                alpha = if (preview.underline) 255 else 90
                style = if (preview.underline) Paint.Style.STROKE else Paint.Style.FILL
                strokeWidth = 2f * density
                strokeCap = Paint.Cap.ROUND
            }
            preview.pageRects.forEach { pageRect ->
                val rect = pageRectMapper?.invoke(pageRect) ?: return@forEach
                if (preview.underline) canvas.drawLine(rect.left, rect.bottom, rect.right, rect.bottom, previewPaint)
                else canvas.drawRect(rect, previewPaint)
            }
        }
        val currentSelectionRects = currentSelectedViewRects()
        currentSelectionRects.forEach { canvas.drawRect(it, selectionPaint) }
        if (currentSelectionRects.isNotEmpty()) {
            val first = currentSelectionRects.first()
            val last = currentSelectionRects.last()
            drawSelectionHandle(canvas, first.left, first.bottom, start = true)
            drawSelectionHandle(canvas, last.right, last.bottom, start = false)
        }
        when (mode) {
            Mode.DRAW -> canvas.drawPath(previewPath, actionPaint)
            Mode.ERASER -> canvas.drawCircle(lastX, lastY, ERASER_RADIUS_DP * density, actionPaint)
            else -> Unit
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = when (mode) {
        Mode.SELECT -> handleSelectionTouch(event)
        Mode.DRAW, Mode.ERASER -> handleActionTouch(event)
        Mode.NONE -> false
    }

    /**
     * SELECT 模式的事件仲裁器。
     *
     * ACTION_DOWN 不立刻交给 PDF View，否则 PDF View 会先开始滚动并取消长按。只有移动超过
     * 较宽松阈值时才补发 DOWN 并转交导航；普通点击则在 UP 时重放 DOWN + UP。
     */
    private fun handleSelectionTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                downTime = event.downTime
                longPressTriggered = false
                selectionMoved = false
                forwardingNavigation = false
                selectionDrag = resolveHandle(event.x, event.y)
                dragUsesHandleOffset = selectionDrag != SelectionDrag.NONE
                if (selectionDrag != SelectionDrag.NONE) {
                    // 手柄拖动期间不允许父布局截获，保证 MOVE 序列连续。
                    parent?.requestDisallowInterceptTouchEvent(true)
                    showMagnifier(event.x, event.y)
                } else {
                    // 新交互开始前先让 Activity 将旧选区暂存为批注，再清理蓝色手柄。
                    if (hasTextSelection()) onSelectionFocusLost?.invoke()
                    clearTextSelection()
                    pendingLongPressPosition = positionResolver?.invoke(
                        event.x,
                        event.y,
                        null,
                        false
                    )
                    handler.postDelayed(beginLongPress, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                lastX = event.x
                lastY = event.y
                if (selectionDrag != SelectionDrag.NONE) {
                    val dragSlop = HANDLE_DRAG_SLOP_DP * density
                    if (abs(event.x - downX) > dragSlop || abs(event.y - downY) > dragSlop) {
                        selectionMoved = true
                        requestHandleSelection(event.x, event.y, false)
                        showMagnifier(event.x, event.y)
                    }
                    return true
                }
                if (!longPressTriggered && !forwardingNavigation && movedPastNavigationSlop(event)) {
                    startNavigation(event)
                } else if (forwardingNavigation) {
                    forwardNavigation(event)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (!longPressTriggered && !forwardingNavigation) startNavigation(event)
                else if (forwardingNavigation) forwardNavigation(event)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (forwardingNavigation) forwardNavigation(event)
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(beginLongPress)
                if (selectionDrag != SelectionDrag.NONE && selectionMoved) {
                    requestHandleSelection(event.x, event.y, true)
                }
                if (forwardingNavigation) {
                    forwardNavigation(event)
                } else if (!longPressTriggered && selectionDrag == SelectionDrag.NONE) {
                    replayTap(event)
                }
                forwardingNavigation = false
                pendingLongPressPosition = null
                selectionDrag = SelectionDrag.NONE
                dragUsesHandleOffset = false
                hideMagnifier()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(beginLongPress)
                if (forwardingNavigation) forwardNavigation(event)
                forwardingNavigation = false
                pendingLongPressPosition = null
                selectionDrag = SelectionDrag.NONE
                dragUsesHandleOffset = false
                hideMagnifier()
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return false
    }

    /** DRAW 与 ERASER 共用轨迹采集；回调只在 ACTION_UP 发出一次。 */
    private fun handleActionTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val start = positionResolver?.invoke(event.x, event.y, null, false) ?: return false
                clearTextSelection()
                parent?.requestDisallowInterceptTouchEvent(true)
                gestureStart = start
                lastX = event.x
                lastY = event.y
                inkPoints.clear()
                inkPoints += start
                previewPath.reset()
                previewPath.moveTo(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val start = gestureStart ?: return false
                val point = positionResolver?.invoke(event.x, event.y, start.pageIndex, true) ?: return true
                lastX = event.x
                lastY = event.y
                inkPoints += point
                if (mode == Mode.DRAW) {
                    previewPath.lineTo(event.x, event.y)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val start = gestureStart ?: return false
                val end = positionResolver?.invoke(event.x, event.y, start.pageIndex, true) ?: start
                if (inkPoints.lastOrNull() != end) inkPoints += end
                if (mode == Mode.DRAW) {
                    onInkStroke?.invoke(inkPoints.toList(), DRAW_WIDTH_DP * density)
                } else {
                    onEraseStroke?.invoke(inkPoints.toList(), ERASER_RADIUS_DP * density)
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                clearGesturePreview()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                clearGesturePreview()
                return true
            }
        }
        return false
    }

    /** 将手柄触点转换为新锚点，并要求 PDFium 同步刷新选择范围。 */
    private fun requestHandleSelection(x: Float, y: Float, final: Boolean) {
        val start = selectionStart ?: return
        val end = selectionEnd ?: return
        // 手柄圆心位于文字底边下方。拖动实体手柄时把触点还原到文字基线附近，
        // 否则 PDFium 会命中下一行或落到无文字区域，表现为拖不动、跳行。
        val resolvedX = if (!dragUsesHandleOffset) x else when (selectionDrag) {
            SelectionDrag.START -> x + HANDLE_TEXT_INSET_DP * density
            SelectionDrag.END -> x - HANDLE_TEXT_INSET_DP * density
            SelectionDrag.NONE -> x
        }
        val resolvedY = if (dragUsesHandleOffset) y - HANDLE_RADIUS_DP * density else y
        val position = positionResolver?.invoke(resolvedX, resolvedY, start.pageIndex, true) ?: return
        when (selectionDrag) {
            SelectionDrag.START -> onSelectionRequested?.invoke(position, end, false, final)
            SelectionDrag.END -> onSelectionRequested?.invoke(start, position, false, final)
            SelectionDrag.NONE -> Unit
        }
    }

    /** 使用 24dp 隐形热区判断按中了起始还是结束手柄。 */
    private fun resolveHandle(x: Float, y: Float): SelectionDrag {
        val currentSelectionRects = currentSelectedViewRects()
        if (currentSelectionRects.isEmpty()) return SelectionDrag.NONE
        val radius = HANDLE_TOUCH_RADIUS_DP * density
        val first = currentSelectionRects.first()
        val last = currentSelectionRects.last()
        if (distanceSquared(x, y, first.left, first.bottom + HANDLE_RADIUS_DP * density) <= radius * radius) {
            return SelectionDrag.START
        }
        if (distanceSquared(x, y, last.right, last.bottom + HANDLE_RADIUS_DP * density) <= radius * radius) {
            return SelectionDrag.END
        }
        return SelectionDrag.NONE
    }

    /** 优先用归一化矩形实时映射；mapper 暂不可用时才退回上次屏幕矩形。 */
    private fun currentSelectedViewRects(): List<RectF> {
        val mapper = pageRectMapper ?: return selectedRects
        val mapped = selectedPageRects.mapNotNull(mapper)
        return if (mapped.size == selectedPageRects.size) mapped else selectedRects
    }

    private fun drawSelectionHandle(canvas: Canvas, x: Float, y: Float, start: Boolean) {
        val radius = HANDLE_RADIUS_DP * density
        canvas.drawRect(if (start) x - 2f else x, y - radius, if (start) x else x + 2f, y + radius, handlePaint)
        canvas.drawCircle(x, y + radius, radius, handlePaint)
    }


    /** MotionEvent 的所有权仍属于系统，转交前复制，回调完成后立即 recycle。 */
    private fun forwardNavigation(event: MotionEvent) {
        val copy = MotionEvent.obtain(event)
        navigationEventHandler?.invoke(copy)
        copy.recycle()
    }

    /** 延迟决定滚动后，先合成最初的 DOWN，再转发当前 MOVE，保持 GestureDetector 状态完整。 */
    private fun startNavigation(current: MotionEvent) {
        handler.removeCallbacks(beginLongPress)
        pendingLongPressPosition = null
        dragUsesHandleOffset = false
        forwardingNavigation = true
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, downX, downY, 0)
        navigationEventHandler?.invoke(down)
        down.recycle()
        forwardNavigation(current)
    }

    /** 没有达到长按或滚动条件时，向 PDF View 重放一次完整点击。 */
    private fun replayTap(up: MotionEvent) {
        val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, downX, downY, 0)
        navigationEventHandler?.invoke(down)
        down.recycle()
        forwardNavigation(up)
    }

    /** 选择模式使用比系统 touchSlop 更宽松的阈值，容忍长按时的自然手抖。 */
    private fun movedPastNavigationSlop(event: MotionEvent): Boolean {
        val threshold = maxOf(touchSlop * 2f, 12f * density)
        return abs(event.x - downX) > threshold || abs(event.y - downY) > threshold
    }

    private fun updatePaint() {
        actionPaint.color = color
        actionPaint.style = Paint.Style.STROKE
        actionPaint.strokeWidth = if (mode == Mode.ERASER) 2f * density else DRAW_WIDTH_DP * density
        actionPaint.alpha = 220
        invalidate()
    }

    /** 清理临时 Path、采样点和定时任务；待保存预览由单独列表维护，不受影响。 */
    private fun clearGesturePreview() {
        handler.removeCallbacks(beginLongPress)
        pendingLongPressPosition = null
        gestureStart = null
        inkPoints.clear()
        previewPath.reset()
        invalidate()
    }

    private fun distanceSquared(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    companion object {
        private const val DRAW_WIDTH_DP = 3f
        private const val ERASER_RADIUS_DP = 18f
        private const val HANDLE_RADIUS_DP = 6f
        private const val HANDLE_TOUCH_RADIUS_DP = 24f
        private const val HANDLE_DRAG_SLOP_DP = 1.5f
        private const val HANDLE_TEXT_INSET_DP = 2f
    }
}
