// PDF14-MIGRATION: 从 pdf14 原样迁移；本文件仅变更包名，未修改业务逻辑。
package com.pdf.editor.ui.detail.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * 图片和签名在写入 PDF 前使用的可视化定位层。
 *
 * [box] 始终使用当前 View 的像素坐标；最终保存时由 [PdfEditUtil.captureImagePlacement]
 * 转为页面归一化坐标。这样拖动、缩放逻辑与 PDF 页面尺寸和旋转解耦。右下角圆点负责
 * 等比例缩放，图片主体负责平移，所有操作都会限制在编辑区域内部。
 */
class PdfImageOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** 当前手指手势的含义；一次手势只允许拖动或缩放其中一种。 */
    private enum class Mode { NONE, DRAG, SCALE }

    private val density = resources.displayMetrics.density
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#25A83A")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#25A83A")
        style = Paint.Style.FILL
    }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val handleRadius = 9f * density
    private val minimumSize = 56f * density

    /** bitmap 保留原图，box 只描述屏幕预览位置，不在拖动过程中反复缩放像素。 */
    private var bitmap: Bitmap? = null
    private var box = RectF()
    private var mode = Mode.NONE
    private var lastX = 0f
    private var lastY = 0f
    private var aspectRatio = 1f

    /** 设置待编辑图片，并以点击位置为中心创建一个适合手机屏幕的初始尺寸。 */
    fun setImage(source: Bitmap, centerX: Float, centerY: Float) {
        bitmap = source
        aspectRatio = source.width.toFloat() / source.height.coerceAtLeast(1)
        val targetWidth = (width * 0.45f)
            .coerceIn(minimumSize, 220f * density)
        val targetHeight = targetWidth / aspectRatio
        box.set(
            centerX - targetWidth / 2f,
            centerY - targetHeight / 2f,
            centerX + targetWidth / 2f,
            centerY + targetHeight / 2f
        )
        constrainBox()
        visibility = VISIBLE
        invalidate()
    }

    /** 移除待提交图片；位图生命周期仍由调用方管理，本 View 不主动 recycle。 */
    fun clearImage() {
        bitmap = null
        box.setEmpty()
        mode = Mode.NONE
        visibility = GONE
        invalidate()
    }

    fun hasImage(): Boolean = bitmap != null && !box.isEmpty

    /** 返回防御性副本，防止外部修改内部定位框。 */
    fun getImageRect(): RectF = RectF(box)

    fun getSourceBitmap(): Bitmap? = bitmap

    /**
     * 生成与屏幕定位框尺寸一致的位图副本。
     * 当前 PDF 写入流程直接使用原图和目标矩形，此方法保留给需要预缩放导出的调用方。
     */
    fun getProcessedBitmap(): Bitmap? {
        val source = bitmap ?: return null
        val outputWidth = box.width().toInt().coerceAtLeast(1)
        val outputHeight = box.height().toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, outputWidth, outputHeight, true)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val source = bitmap ?: return
        canvas.drawBitmap(source, null, box, imagePaint)
        canvas.drawRect(box, borderPaint)
        canvas.drawCircle(box.right, box.bottom, handleRadius, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // 缩放手柄优先于图片主体，避免圆点区域被误判为普通拖动。
                mode = when {
                    isScaleHandle(x, y) -> Mode.SCALE
                    box.contains(x, y) -> Mode.DRAG
                    else -> Mode.NONE
                }
                lastX = x
                lastY = y
                return mode != Mode.NONE
            }

            MotionEvent.ACTION_MOVE -> {
                when (mode) {
                    Mode.DRAG -> box.offset(x - lastX, y - lastY)
                    Mode.SCALE -> {
                        // 只由水平方向决定新宽度，再用原图宽高比推导高度，避免图片变形。
                        val newWidth = (x - box.left).coerceAtLeast(minimumSize)
                        val newHeight = newWidth / aspectRatio
                        box.right = box.left + newWidth
                        box.bottom = box.top + newHeight
                    }
                    Mode.NONE -> return false
                }
                constrainBox()
                lastX = x
                lastY = y
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val handled = mode != Mode.NONE
                mode = Mode.NONE
                return handled
            }
        }
        return false
    }

    /** 使用比可见圆点更大的命中区域，降低小屏幕上拖动手柄的难度。 */
    private fun isScaleHandle(x: Float, y: Float): Boolean {
        return abs(x - box.right) <= handleRadius * 2f &&
            abs(y - box.bottom) <= handleRadius * 2f
    }

    /** 按“先限制尺寸、再修正位置”的顺序确保图片完整留在编辑区域内。 */
    private fun constrainBox() {
        if (width <= 0 || height <= 0 || box.isEmpty) return
        if (box.width() > width) {
            box.right = box.left + width
            box.bottom = box.top + width / aspectRatio
        }
        if (box.height() > height) {
            box.bottom = box.top + height
            box.right = box.left + height * aspectRatio
        }
        if (box.left < 0f) box.offset(-box.left, 0f)
        if (box.top < 0f) box.offset(0f, -box.top)
        if (box.right > width) box.offset(width - box.right, 0f)
        if (box.bottom > height) box.offset(0f, height - box.bottom)
    }
}
