// PDF14-MIGRATION: 从 pdf14 复制；仅包名及已标记宿主接口/资源适配，未优化转换算法。
package com.pdf.editor.ui.tools.document.common

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.pdf.editor.MyAPP
import com.pdf.editor.common.core.BaseDataModel // PDF14-MIGRATION: 使用当前项目等价 ViewModel 基类。
import com.pdf.editor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 持有一次页面任务及其状态，让任务不再依赖 Activity 的协程作用域。
 * 配置变更会复用该实例，不重复生成文件；不提供后台持久任务或进程重启续跑能力。
 */
class DocumentBuildViewModel : BaseDataModel() {
    // CoreViewModel 使用无参构造；执行器只持有应用实例，不持有 Activity，避免页面重建造成泄漏。
    private val executor = DocumentTaskExecutor(MyAPP.instance)
    // 内部通过 MutableLiveData 更新状态，对页面只暴露 LiveData，避免页面直接修改任务状态。
    private val mutableState = MutableLiveData<DocumentTaskState>(DocumentTaskState.Idle)
    val state: LiveData<DocumentTaskState> = mutableState
    private var request: DocumentTaskRequest? = null
    private var resultOpened = false

    /** 只接受首次请求；Activity 重建后再次调用时复用正在执行或已经完成的任务。 */
    fun start(request: DocumentTaskRequest) {
        if (this.request != null) return
        this.request = request
        execute(request)
    }

    /** 成功结果只能消费一次，防止重新订阅时重复打开成功页；在主线程调用。 */
    fun takeResult(): DocumentTaskResult? {
        // as? 安全转换类型，不是 Success 时得到 null；?: return null 在这种情况下直接结束方法。
        val success = state.value as? DocumentTaskState.Success ?: return null
        if (resultOpened) return null
        resultOpened = true
        return success.result
    }

    /**
     * 沿用 CoreViewModel 的 launchDataLoad(loadBlock, onFailure) 组织任务。
     * 在主线程调用，使用默认 executionMode = 0，使状态更新保持在主线程；
     * Executor 自行切换到 IO 执行转换，返回后可以直接更新 LiveData.value。
     * 普通异常由基类记录并回调 onFailure，协程正常取消不会触发该异常回调。
     */
    private fun execute(request: DocumentTaskRequest) {
        // when 配合 is 判断任务类型；-> 后是对应初始数量，整个表达式的结果赋给 total。
        val total = when (request) {
            // 拆分执行器稍后上报实际保留页数，初始为不确定进度，不启动图片模拟动画。
            is DocumentTaskRequest.SplitPdf -> 0
            // 合并由执行器报告文件级进度，初始化时先显示等待，不启动图片的模拟进度。
            is DocumentTaskRequest.MergePdfs -> 0
            is DocumentTaskRequest.ImagesToPdf -> request.imageUris.size
            // Word 渲染无法提供准确页进度，以 0 表示不确定进度，页面不显示虚假的图片数量。
            is DocumentTaskRequest.WordToPdf -> 0
            is DocumentTaskRequest.PdfToWord -> 0
            is DocumentTaskRequest.PdfToImages -> 0
        }
        mutableState.value = DocumentTaskState.Running(total)
        // PDF14-MIGRATION: 当前 BaseDataModel.loadData 与源 launchDataLoad 实现等价，仅参数命名不同。
        loadData(
            loadBack = {
                val result = executor.execute(request) { completed, count ->
                    // 转换器在 IO 线程报告真实进度，切回主线程更新 LiveData，避免 postValue 与成功状态乱序。
                    withContext(Dispatchers.Main) { mutableState.value = DocumentTaskState.Running(count, completed) }
                }
                mutableState.value = DocumentTaskState.Success(result)
            },
            failBack = { error ->
                // 基类负责统一错误记录，这里只将任务失败状态交给页面展示。
                // is 判断请求和异常的具体类型；if/when 都可作为表达式返回对应失败状态或文案资源。
                mutableState.value = if (request is DocumentTaskRequest.PdfToWord) {
                    DocumentTaskState.Failed(when (error) {
                        is PdfToWordConverter.Protected -> R.string.app_pdf_word_protected
                        is PdfToWordConverter.Unavailable -> R.string.app_pdf_file_unavailable
                        else -> R.string.app_pdf_word_failed
                    })
                } else if (request is DocumentTaskRequest.PdfToImages) {
                    DocumentTaskState.Failed(when (error) {
                        is PdfToImagesConverter.Protected -> R.string.app_pdf_images_protected
                        is PdfToImagesConverter.StoragePermission -> R.string.app_pdf_images_permission
                        else -> R.string.app_pdf_images_failed
                    })
                } else if (request is DocumentTaskRequest.MergePdfs) {
                    // 区分加密、失效和一般合并错误，便于用户回到选择页调整文件。
                    DocumentTaskState.Failed(when (error) {
                        is PdfMergeConverter.Protected -> R.string.app_pdf_merge_protected
                        is PdfMergeConverter.Unavailable -> R.string.app_pdf_file_unavailable
                        else -> R.string.app_pdf_merge_failed
                    })
                } else if (request is DocumentTaskRequest.SplitPdf) {
                    // is 判断具体失败类型，when 返回对应提示资源；不改变其他转换任务的失败逻辑。
                    DocumentTaskState.Failed(when (error) {
                        is PdfSplitConverter.Protected -> R.string.app_pdf_split_protected
                        is PdfSplitConverter.Unavailable -> R.string.app_pdf_file_unavailable
                        else -> R.string.app_pdf_split_failed
                    })
                } else if (request is DocumentTaskRequest.WordToPdf) {
                    DocumentTaskState.Failed(R.string.app_word_pdf_failed)
                } else DocumentTaskState.Failed()
            }
        )
    }
}
