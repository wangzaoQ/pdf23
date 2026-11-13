// PDF14-MIGRATION: 原样迁入 pdf14 工具页面；仅包名、资源名及当前项目基类/宿主接口适配。
package com.pdf.editor.ui.tools.document.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CornerBorderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val path = Path()
    private val arcRect = RectF()
    private val cornerRadius = 10f
    private val armLength = 40f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#24AE27")
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val offset = paint.strokeWidth / 2f
        val left = offset
        val top = offset
        val right = width - offset
        val bottom = height - offset
        path.reset()

        path.moveTo(left, armLength)
        path.lineTo(left, top + cornerRadius)
        arcRect.set(left, top, left + cornerRadius * 2, top + cornerRadius * 2)
        path.arcTo(arcRect, 180f, 90f)
        path.lineTo(armLength, top)

        path.moveTo(right - armLength, top)
        path.lineTo(right - cornerRadius, top)
        arcRect.set(right - cornerRadius * 2, top, right, top + cornerRadius * 2)
        path.arcTo(arcRect, 270f, 90f)
        path.lineTo(right, armLength)

        path.moveTo(right, height - armLength)
        path.lineTo(right, bottom - cornerRadius)
        arcRect.set(right - cornerRadius * 2, bottom - cornerRadius * 2, right, bottom)
        path.arcTo(arcRect, 0f, 90f)
        path.lineTo(right - armLength, bottom)

        path.moveTo(armLength, bottom)
        path.lineTo(left + cornerRadius, bottom)
        arcRect.set(left, bottom - cornerRadius * 2, left + cornerRadius * 2, bottom)
        path.arcTo(arcRect, 90f, 90f)
        path.lineTo(left, height - armLength)
        canvas.drawPath(path, paint)
    }
}
