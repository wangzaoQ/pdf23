// PDF14-MIGRATION: 从 pdf14 原样迁移；本文件仅变更包名，未修改业务逻辑。
package com.pdf.editor.ui.detail.reader

import android.content.res.AssetManager
import android.graphics.Bitmap
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * PDFBox Android 写入层。
 *
 * UI 和 PDFium 阅读器统一使用“页面左上角为原点、0..1 归一化”的坐标；PDFBox 使用
 * “页面左下角为原点、PDF point”为单位的坐标。本对象负责两套坐标之间的转换，并把
 * 文字、图片、签名、文字标记、墨迹和擦除真正写进 PDF。
 *
 * 所有修改方法都会同步打开并保存文件，必须由调用方放在后台线程执行。Activity 在写入前
 * 会备份原文件，异常时再恢复，因而这里可以专注于单次原子编辑。
 */
object PdfEditUtil {

    /** 文字编辑框在目标 PDF 页中的位置和排版参数，全部使用页面比例保存。 */
    data class TextPlacement(
        val pageIndex: Int,
        val xFraction: Float,
        val baselineFromTopFraction: Float,
        val fontSizeFraction: Float,
        val lineHeightFraction: Float
    )

    /** 图片/签名目标矩形；top/bottom 是从显示页面顶部开始计算的比例。 */
    data class ImagePlacement(
        val pageIndex: Int,
        val leftFraction: Float,
        val topFraction: Float,
        val rightFraction: Float,
        val bottomFraction: Float
    )

    /** 当前产品支持的两种文字选区批注。 */
    enum class TextMarkupType { HIGHLIGHT, UNDERLINE }

    /** 一条待写入的文字批注，可包含同一页上的多行矩形。 */
    data class TextMarkup(
        val rects: List<PdfiumDocumentView.PageRect>,
        val type: TextMarkupType,
        val color: Int
    )

    /** View 坐标转换后的内部归一化点，避免编辑预览依赖具体 PDF point 尺寸。 */
    private data class PagePoint(
        val xFraction: Float,
        val yFraction: Float
    )

    /**
     * 从屏幕上的 EditText 提取可跨缩放保存的文字位置。
     *
     * 除基线外还分别映射“基线 + 字号”和“基线 + 行高”，用坐标差得到字号及行高比例。
     * 因此用户放大 PDF 后添加文字，最终写入的字号仍与当时页面视觉大小一致。
     */
    fun captureTextPlacement(
        pdfView: PdfiumDocumentView,
        pageIndex: Int,
        textX: Float,
        baselineY: Float,
        textSizePx: Float,
        lineHeightPx: Float
    ): TextPlacement? {
        val baseline = mapViewToPage(pdfView, pageIndex, textX, baselineY) ?: return null
        val fontBottom = mapViewToPage(pdfView, pageIndex, textX, baselineY + textSizePx)
            ?: return null
        val lineBottom = mapViewToPage(pdfView, pageIndex, textX, baselineY + lineHeightPx)
            ?: return null
        return TextPlacement(
            pageIndex = pageIndex,
            xFraction = baseline.xFraction,
            baselineFromTopFraction = baseline.yFraction,
            fontSizeFraction = abs(fontBottom.yFraction - baseline.yFraction),
            lineHeightFraction = abs(lineBottom.yFraction - baseline.yFraction)
        )
    }

    /** 将图片预览框的两个角转换为同一页内的归一化目标矩形。 */
    fun captureImagePlacement(
        pdfView: PdfiumDocumentView,
        pageIndex: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): ImagePlacement? {
        val topLeft = mapViewToPage(pdfView, pageIndex, left, top) ?: return null
        val bottomRight = mapViewToPage(pdfView, pageIndex, right, bottom) ?: return null
        return ImagePlacement(
            pageIndex = pageIndex,
            leftFraction = minOf(topLeft.xFraction, bottomRight.xFraction),
            topFraction = minOf(topLeft.yFraction, bottomRight.yFraction),
            rightFraction = maxOf(topLeft.xFraction, bottomRight.xFraction),
            bottomFraction = maxOf(topLeft.yFraction, bottomRight.yFraction)
        )
    }

    /**
     * 把文字作为页面内容流写入，而不是建立可编辑表单字段。
     *
     * 使用内置 NotoSansSC 字体保证中英文均可编码。PDF 文本坐标以基线定位；多行文字
     * 每行重新计算绝对基线，避免 content stream 的相对坐标误差逐行累积。
     */
    fun addText(
        file: File,
        assets: AssetManager,
        text: String,
        placement: TextPlacement
    ) {
        PDDocument.load(file).use { document ->
            require(placement.pageIndex in 0 until document.numberOfPages)
            val page = document.getPage(placement.pageIndex)
            val box = page.mediaBox
            // 屏幕 y 从上向下递增，PDF y 从下向上递增，所以基线需要用 1 - fraction 翻转。
            val pdfX = box.lowerLeftX + placement.xFraction * box.width
            val firstBaseline = box.lowerLeftY + (1f - placement.baselineFromTopFraction) * box.height
            val fontSize = (placement.fontSizeFraction * box.height).coerceAtLeast(1f)
            val lineHeight = (placement.lineHeightFraction * box.height).coerceAtLeast(fontSize)
            val font = assets.open(FONT_ASSET).use { PDType0Font.load(document, it) }

            // APPEND 保留原页面内容；resetContext=true 避免继承原内容流中的变换矩阵。
            PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
            ).use { content ->
                content.beginText()
                content.setFont(font, fontSize)
                content.setNonStrokingColor(0, 0, 0)
                text.lines().forEachIndexed { index, line ->
                    if (line.isNotEmpty()) {
                        content.newLineAtOffset(pdfX, firstBaseline - index * lineHeight)
                        content.showText(line)
                        content.newLineAtOffset(-pdfX, -(firstBaseline - index * lineHeight))
                    }
                }
                content.endText()
            }
            document.save(file)
        }
    }

    /** 将图片或签名作为无损 XObject 追加到页面内容流。 */
    fun addImage(
        file: File,
        bitmap: Bitmap,
        placement: ImagePlacement
    ) {
        PDDocument.load(file).use { document ->
            require(placement.pageIndex in 0 until document.numberOfPages)
            val page = document.getPage(placement.pageIndex)
            val box = page.mediaBox
            val left = box.lowerLeftX + placement.leftFraction * box.width
            // PDFBox drawImage 接收左下角，因此使用屏幕矩形的 bottom 计算 PDF 的 bottom。
            val bottom = box.lowerLeftY + (1f - placement.bottomFraction) * box.height
            val width = (placement.rightFraction - placement.leftFraction) * box.width
            val height = (placement.bottomFraction - placement.topFraction) * box.height
            require(width > 0f && height > 0f)
            val image = LosslessFactory.createFromImage(document, bitmap)

            PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
            ).use { content ->
                content.drawImage(image, left, bottom, width, height)
            }
            document.save(file)
        }
    }

    /** 写入单条文字标记的便捷入口。 */
    fun addTextMarkup(
        file: File,
        rects: List<PdfiumDocumentView.PageRect>,
        type: TextMarkupType,
        color: Int
    ) {
        addTextMarkups(file, listOf(TextMarkup(rects, type, color)))
    }

    /**
     * 一次打开和保存文档，提交当前编辑会话里积累的全部文字标记。
     *
     * PDF 文字标记使用 QuadPoints 描述每一行的四个角；rectangle 只是批注总包围框。
     * 两者都必须设置，否则不同 PDF 阅读器可能无法正确显示或命中该批注。
     */
    fun addTextMarkups(file: File, markups: List<TextMarkup>) {
        require(markups.isNotEmpty())
        PDDocument.load(file).use { document ->
            markups.forEachIndexed { index, markup ->
                require(markup.rects.isNotEmpty())
                val pageIndex = markup.rects.first().pageIndex
                require(markup.rects.all { it.pageIndex == pageIndex })
                require(pageIndex in 0 until document.numberOfPages)
                val page = document.getPage(pageIndex)
                val subtype = when (markup.type) {
                    TextMarkupType.HIGHLIGHT -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
                    TextMarkupType.UNDERLINE -> PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
                }
                val annotation = PDAnnotationTextMarkup(subtype)
                // PDF 规范要求每个文字四边形按 左上、右上、左下、右下 的顺序写入。
                val quadPoints = markup.rects.flatMap { rect ->
                    val topLeft = pagePoint(page.cropBox, page.rotation, rect.leftFraction, rect.topFraction)
                    val topRight = pagePoint(page.cropBox, page.rotation, rect.rightFraction, rect.topFraction)
                    val bottomLeft = pagePoint(page.cropBox, page.rotation, rect.leftFraction, rect.bottomFraction)
                    val bottomRight = pagePoint(page.cropBox, page.rotation, rect.rightFraction, rect.bottomFraction)
                    listOf(
                        topLeft.first, topLeft.second,
                        topRight.first, topRight.second,
                        bottomLeft.first, bottomLeft.second,
                        bottomRight.first, bottomRight.second
                    )
                }.toFloatArray()
                annotation.quadPoints = quadPoints
                annotation.rectangle = boundsOf(quadPoints, 2f)
                annotation.color = pdfColor(markup.color)
                annotation.constantOpacity = if (markup.type == TextMarkupType.HIGHLIGHT) 0.35f else 1f
                // 名称前缀用于橡皮擦区分“本应用创建的批注”和文档原有批注。
                annotation.annotationName = "$ANNOTATION_PREFIX${System.nanoTime()}-$index"
                page.annotations.add(annotation)
                annotation.constructAppearances(document)
            }
            document.save(file)
        }
    }

    /**
     * 将一次画笔手势写成标准 Ink 批注。
     *
     * strokeWidthFraction 相对于显示页宽，保存时换算成 PDF point；旋转 90/270 度时显示页宽
     * 对应 cropBox.height，因此需要根据旋转交换基准边。
     */
    fun addInk(
        file: File,
        points: List<PdfiumDocumentView.PagePosition>,
        color: Int,
        strokeWidthFraction: Float
    ) {
        require(points.isNotEmpty())
        val pageIndex = points.first().pageIndex
        require(points.all { it.pageIndex == pageIndex })
        PDDocument.load(file).use { document ->
            require(pageIndex in 0 until document.numberOfPages)
            val page = document.getPage(pageIndex)
            val box = page.cropBox
            // InkList 的数组格式为 [x0, y0, x1, y1, ...]。
            val path = points.flatMap { point ->
                val mapped = pagePoint(box, page.rotation, point.xFraction, point.yFraction)
                listOf(mapped.first, mapped.second)
            }.toFloatArray()
            val displayWidth = if (normalizedRotation(page.rotation) % 180 == 0) box.width else box.height
            val strokeWidth = (strokeWidthFraction * displayWidth).coerceAtLeast(0.75f)
            val annotation = PDAnnotationMarkup().apply {
                cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)
                setInkList(arrayOf(path))
                setRectangle(boundsOf(path, strokeWidth * 2f))
                setColor(pdfColor(color))
                setBorderStyle(PDBorderStyleDictionary().apply {
                    width = strokeWidth
                    style = PDBorderStyleDictionary.STYLE_SOLID
                })
                setConstantOpacity(1f)
                setAnnotationName("$ANNOTATION_PREFIX${System.nanoTime()}")
            }
            page.annotations.add(annotation)
            annotation.constructAppearances(document)
            document.save(file)
        }
    }

    /** 删除单点命中的墨迹或文字标记；主要保留给测试及单击擦除场景。 */
    fun eraseAnnotation(
        file: File,
        position: PdfiumDocumentView.PagePosition,
        radiusFraction: Float
    ): Boolean = eraseAnnotations(file, listOf(position), radiusFraction)

    /**
     * 沿完整手势轨迹擦除应用创建的批注。
     *
     * 只处理支持的 subtype 且名称带 [ANNOTATION_PREFIX] 的批注，链接、表单、他人已有批注
     * 均不会被删除。倒序遍历避免删除元素后下标移动；一次轨迹可以命中多条批注。
     */
    fun eraseAnnotations(
        file: File,
        positions: List<PdfiumDocumentView.PagePosition>,
        radiusFraction: Float
    ): Boolean {
        require(positions.isNotEmpty())
        val pageIndex = positions.first().pageIndex
        require(positions.all { it.pageIndex == pageIndex })
        var removed = false
        PDDocument.load(file).use { document ->
            require(pageIndex in 0 until document.numberOfPages)
            val page = document.getPage(pageIndex)
            val box = page.cropBox
            val points = positions.map { position ->
                pagePoint(box, page.rotation, position.xFraction, position.yFraction)
            }
            val displayWidth = if (normalizedRotation(page.rotation) % 180 == 0) box.width else box.height
            val radius = radiusFraction * displayWidth
            // PDFBox 返回的注释列表不适合边迭代边修改，先复制，最后整体回写页面。
            val annotations = page.annotations.toMutableList()
            for (index in annotations.indices.reversed()) {
                val annotation = annotations[index]
                if (annotation.subtype !in EDITABLE_SUBTYPES) continue
                if (annotation.annotationName?.startsWith(ANNOTATION_PREFIX) != true) continue
                if (points.any { point ->
                        annotationHit(annotation, point.first, point.second, radius)
                    }
                ) {
                    annotations.removeAt(index)
                    removed = true
                }
            }
            if (removed) {
                page.annotations = annotations
                document.save(file)
            }
        }
        return removed
    }

    /** 根据批注实际几何数据判断橡皮擦圆是否命中，而不是只判断粗略 rectangle。 */
    private fun annotationHit(
        annotation: com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation,
        x: Float,
        y: Float,
        radius: Float
    ): Boolean {
        if (annotation is PDAnnotationTextMarkup) {
            // 每 8 个数是一行文字的四个顶点，使用外接矩形并扩展橡皮擦半径命中。
            val quads = annotation.quadPoints ?: return false
            for (offset in quads.indices step 8) {
                if (offset + 7 >= quads.size) break
                val xs = listOf(quads[offset], quads[offset + 2], quads[offset + 4], quads[offset + 6])
                val ys = listOf(quads[offset + 1], quads[offset + 3], quads[offset + 5], quads[offset + 7])
                if (x >= xs.minOrNull()!! - radius && x <= xs.maxOrNull()!! + radius &&
                    y >= ys.minOrNull()!! - radius && y <= ys.maxOrNull()!! + radius
                ) return true
            }
            return false
        }
        if (annotation is PDAnnotationMarkup && annotation.subtype == PDAnnotationMarkup.SUB_TYPE_INK) {
            // 对墨迹的每一条线段计算点到线段距离，避免只在采样点附近才能擦除。
            annotation.inkList?.forEach { path ->
                if (path.size == 2 && distance(path[0], path[1], x, y) <= radius) return true
                for (offset in 0 until path.size - 3 step 2) {
                    if (segmentDistance(
                            x, y,
                            path[offset], path[offset + 1],
                            path[offset + 2], path[offset + 3]
                        ) <= radius
                    ) return true
                }
            }
            return false
        }
        return false
    }

    /** 点到有限线段的最短距离；投影比例被限制在 0..1。 */
    private fun segmentDistance(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) return distance(px, py, x1, y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0f, 1f)
        return distance(px, py, x1 + t * dx, y1 + t * dy)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * 将显示坐标转换到考虑 CropBox 和页面旋转后的 PDF 坐标。
     * 输入坐标原点在左上；输出坐标原点在 CropBox 左下。
     */
    private fun pagePoint(
        box: PDRectangle,
        rotation: Int,
        xFraction: Float,
        yFraction: Float
    ): Pair<Float, Float> = when (normalizedRotation(rotation)) {
        90 -> Pair(
            box.lowerLeftX + yFraction * box.width,
            box.lowerLeftY + xFraction * box.height
        )
        180 -> Pair(
            box.lowerLeftX + (1f - xFraction) * box.width,
            box.lowerLeftY + yFraction * box.height
        )
        270 -> Pair(
            box.lowerLeftX + (1f - yFraction) * box.width,
            box.lowerLeftY + (1f - xFraction) * box.height
        )
        else -> Pair(
            box.lowerLeftX + xFraction * box.width,
            box.lowerLeftY + (1f - yFraction) * box.height
        )
    }

    /** 从交错的 x/y 坐标数组生成带留白的 PDF 包围框。 */
    private fun boundsOf(values: FloatArray, padding: Float): PDRectangle {
        val xs = values.indices.filter { it % 2 == 0 }.map { values[it] }
        val ys = values.indices.filter { it % 2 == 1 }.map { values[it] }
        val left = xs.minOrNull()!! - padding
        val bottom = ys.minOrNull()!! - padding
        return PDRectangle(left, bottom, xs.maxOrNull()!! - left + padding, ys.maxOrNull()!! - bottom + padding)
    }

    /** Android 0..255 RGB 转换为 PDFBox 0..1 DeviceRGB。Alpha 由批注 opacity 单独表达。 */
    private fun pdfColor(color: Int) = PDColor(
        floatArrayOf(
            android.graphics.Color.red(color) / 255f,
            android.graphics.Color.green(color) / 255f,
            android.graphics.Color.blue(color) / 255f
        ),
        PDDeviceRGB.INSTANCE
    )

    /** 将负角度和大于 360 度的值归一化到 0..359。 */
    private fun normalizedRotation(rotation: Int): Int = ((rotation % 360) + 360) % 360

    /**
     * 把编辑层 View 坐标还原到目标页面的归一化坐标。
     *
     * 页面可能因“最宽页适配”而水平居中，还要同时考虑 zoom 与 scroll offset。该方法与
     * [PdfiumDocumentView] 的连续页面布局保持一致，供文字和图片落点捕获使用。
     */
    private fun mapViewToPage(
        pdfView: PdfiumDocumentView,
        pageIndex: Int,
        viewX: Float,
        viewY: Float
    ): PagePoint? {
        val count = pdfView.pageCount
        val zoom = pdfView.zoom
        if (count <= 0 || zoom <= 0f || pageIndex !in 0 until count) return null

        val pageSize = pdfView.getPageSize(pageIndex)
        if (pageSize.width <= 0f || pageSize.height <= 0f) return null

        // primaryOffset 是目标页之前所有页面在滚动方向上的累计长度。
        var primaryOffset = 0f
        for (index in 0 until pageIndex) {
            val size = pdfView.getPageSize(index)
            primaryOffset += if (pdfView.isSwipeVertical) size.height else size.width
        }

        // 次轴按最宽/最高页面居中，尺寸较小的页面会产生 secondaryOffset。
        var maxSecondarySize = 0f
        for (index in 0 until count) {
            val size = pdfView.getPageSize(index)
            maxSecondarySize = maxOf(
                maxSecondarySize,
                if (pdfView.isSwipeVertical) size.width else size.height
            )
        }
        val currentSecondarySize = if (pdfView.isSwipeVertical) pageSize.width else pageSize.height
        val secondaryOffset = (maxSecondarySize - currentSecondarySize) / 2f
        val pageLeft = if (pdfView.isSwipeVertical) secondaryOffset * zoom else primaryOffset * zoom
        val pageTop = if (pdfView.isSwipeVertical) primaryOffset * zoom else secondaryOffset * zoom
        val documentX = -pdfView.currentXOffset + viewX
        val documentY = -pdfView.currentYOffset + viewY
        val localX = (documentX - pageLeft) / zoom
        val localY = (documentY - pageTop) / zoom

        return PagePoint(
            xFraction = (localX / pageSize.width).coerceIn(0f, 1f),
            yFraction = (localY / pageSize.height).coerceIn(0f, 1f)
        )
    }

    /** 嵌入 PDF 的字体、应用批注标识和橡皮擦白名单。 */
    private const val FONT_ASSET = "fonts/NotoSansSC_Bold.ttf"
    private const val ANNOTATION_PREFIX = "ReadPro-"
    private val EDITABLE_SUBTYPES = setOf(
        PDAnnotationMarkup.SUB_TYPE_INK,
        PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT,
        PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
    )
}
