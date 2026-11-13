// PDF14-MIGRATION: 从 pdf14 原样迁移；本文件仅变更包名，未修改业务逻辑。
package com.pdf.editor.ui.detail.reader

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * PDF 编辑区的软键盘避让容器。
 *
 * 页面顶部标题栏不在本容器内，因此这里不使用 Activity 整体 resize。键盘出现时只计算
 * 当前焦点控件（通常是 PDF 上的文字输入框）底边与键盘顶边的重叠量，再通过
 * [translationY] 临时上移编辑区。键盘关闭后恢复到 0，PDF 的滚动坐标不会因此改变。
 */
class ImePanRelativeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    init {
        // WindowInsets 同时兼容手势导航和传统三键导航，不能用可见屏幕高度差替代。
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            updateTranslationForIme(insets)
            insets
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    /**
     * 文字输入点击 Done 后立即恢复阅读区位置。
     *
     * Activity 使用 adjustNothing，键盘关闭动画结束前不一定马上派发新的 Insets；由输入流程
     * 主动归零可以避免 PDF 暂时或持续停留在上移位置，随后再请求一次 Insets 校准最终状态。
     */
    fun restoreAfterIme() {
        translationY = 0f
        ViewCompat.requestApplyInsets(this)
    }

    private fun updateTranslationForIme(insets: WindowInsetsCompat) {
        // 未显示键盘时必须清除旧位移，否则返回阅读模式后整个 PDF 会保持上移。
        if (!insets.isVisible(WindowInsetsCompat.Type.ime())) {
            translationY = 0f
            return
        }
        // 容器自身获得焦点不需要避让，只有真正的子编辑控件才参与计算。
        val focusedView = findFocus()?.takeIf { it !== this } ?: return
        val rootLocation = IntArray(2)
        val focusedLocation = IntArray(2)
        rootView.getLocationInWindow(rootLocation)
        focusedView.getLocationInWindow(focusedLocation)

        val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        val keyboardTop = rootLocation[1] + rootView.height - imeBottom
        // focusedLocation 已经包含当前 translationY，先抵消它，避免重复 Insets 回调时累计位移。
        val focusedBottomWithoutTranslation =
            focusedLocation[1] + focusedView.height - translationY
        val spacing = 12 * resources.displayMetrics.density
        translationY =
            (keyboardTop - spacing - focusedBottomWithoutTranslation).coerceAtMost(0f)
    }
}
