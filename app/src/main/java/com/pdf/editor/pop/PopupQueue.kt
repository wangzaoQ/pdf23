package com.pdf.editor.pop

import android.os.Handler
import android.os.Looper
import razerdp.basepopup.BasePopupWindow

// PopupQueue.kt
object PopupQueue {
    private val queue: ArrayDeque<BasePopupWindow> = ArrayDeque()
    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isShowing = false

    // 保存当前正在显示的弹窗引用，便于立刻 dismiss
    @Volatile
    private var currentPopup: BasePopupWindow? = null

    private const val GAP_MS: Long = 500L

    fun enqueue(popup: BasePopupWindow) {
        synchronized(lock) {
            queue.addLast(popup)
        }
        tryShowNext()
    }

    fun remove(popup: BasePopupWindow) {
        synchronized(lock) {
            queue.remove(popup)
        }
    }

    /** 立即关闭当前弹窗（如果存在） */
    fun dismissCurrent() {
        mainHandler.post {
            currentPopup?.let {
                try {
                    it.setOnDismissListener(null) // 清理避免回调被触发两次
                    it.dismiss()
                } catch (e: Exception) {
                    // ignore
                } finally {
                    currentPopup = null
                    synchronized(lock) { isShowing = false }
                    // 立即尝试下一个（不再等待 GAP，因为已经主动 dismiss）
                    tryShowNext()
                }
            }
        }
    }

    /** 清空队列并尝试关闭当前 */
    fun clearQueueAndDismissCurrent() {
        synchronized(lock) {
            queue.clear()
        }
        dismissCurrent()
    }

    private fun tryShowNext() {
        mainHandler.post {
            synchronized(lock) {
                if (isShowing) return@post
                val next = queue.removeFirstOrNull() ?: return@post
                isShowing = true
                currentPopup = next

                // 注册基于你给出的 OnDismissListener 回调
                next.setOnDismissListener(object : BasePopupWindow.OnDismissListener() {
                    override fun onDismiss() {
                        // 清理 listener 引用，避免内存泄露和重复触发
                        next.setOnDismissListener(null)

                        // 延迟 GAP_MS 后显示下一个
                        mainHandler.postDelayed({
                            synchronized(lock) {
                                isShowing = false
                            }
                            currentPopup = null
                            tryShowNext()
                        }, GAP_MS)
                    }
                })

                // 展示弹窗（若 show 抛异常则防御性处理并继续下一个）
                try {
                    next.showPopupWindow()
                } catch (e: Exception) {
                    // 展示失败 -> 清理并尝试下一个
                    next.setOnDismissListener(null)
                    currentPopup = null
                    isShowing = false
                    tryShowNext()
                }
            }
        }
    }
}