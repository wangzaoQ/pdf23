package com.pdf.editor.ui.detail.add

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.scwang.smart.refresh.layout.util.SmartUtil
import kotlin.math.max
import kotlin.math.min

class SignatureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint().apply {
        color = Color.parseColor("#FE5154")  // 签名颜色
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 8f                      // 笔画粗细
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val drawPath = Path()
    private var bitmap: Bitmap? = null
    private var canvasBuffer: Canvas? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 生成透明 Bitmap 作为缓冲
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        canvasBuffer = Canvas(bitmap!!)
        canvasBuffer?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.drawPath(drawPath, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> drawPath.moveTo(x, y)
            MotionEvent.ACTION_MOVE -> drawPath.lineTo(x, y)
            MotionEvent.ACTION_UP -> {
                canvasBuffer?.drawPath(drawPath, paint)
                drawPath.reset()
            }
        }
        invalidate()
        return true
    }

    /** 清空签名 */
    fun clear() {
        canvasBuffer?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        invalidate()
    }

    /**
     * 导出签名 Bitmap
     * 自动裁剪透明区域，并在周围留 paddingDp 空白
     */
    fun getCroppedBitmapWithPadding(): Bitmap? {
        val src = bitmap ?: return null

        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0

        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = pixels[y * width + x] ushr 24
                if (alpha > 0) {
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
        }

        // 全透明返回 null
        if (minX > maxX || minY > maxY) return null

        // 转 dp 为 px
        val padding = SmartUtil.dp2px(50f)

        minX = (minX - padding).coerceAtLeast(0)
        minY = (minY - padding).coerceAtLeast(0)
        maxX = (maxX + padding).coerceAtMost(width - 1)
        maxY = (maxY + padding).coerceAtMost(height - 1)

        val croppedWidth = maxX - minX + 1
        val croppedHeight = maxY - minY + 1

        val result = Bitmap.createBitmap(croppedWidth, croppedHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val srcRect = Rect(minX, minY, maxX + 1, maxY + 1)
        val dstRect = Rect(0, 0, croppedWidth, croppedHeight)
        canvas.drawBitmap(src, srcRect, dstRect, Paint(Paint.ANTI_ALIAS_FLAG))

        return result
    }
}
