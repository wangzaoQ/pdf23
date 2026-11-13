package com.pdf.editor.ui.tools

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.pdf.editor.R
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.core.BaseFragment
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.jumpToActivity
import com.pdf.editor.databinding.ToolFragmentToolBinding
import com.pdf.editor.ui.detail.reader.setSequentialClickListener
import com.pdf.editor.ui.tools.document.file.FileListActivity
import com.pdf.editor.ui.tools.document.file.FileSelectAction
import com.pdf.editor.ui.PdfMainActivity
import com.pdf.editor.feature.browser.InAppBrowserActivity
import com.pdf.editor.feature.preferences.language.LanguagePickerActivity
import com.pdf.editor.feature.widget.QuickToolsWidgetController
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// PDF14-MIGRATION: 迁入源七个工具入口和布局；沿用当前 Fragment 基类及广告、设置接口。
class ToolsFragment: BaseFragment<ToolFragmentToolBinding>() {
    override fun startLoadData() {
    }


    override fun setListener() {
        fBinding.apply {
            rlLanguage.setSequentialClickListener {
                rootActivity.jumpToActivity<LanguagePickerActivity>()
            }
            flAddWidget.setSequentialClickListener {
                EventManager.posePoint(PointConfig.widget_tool_click)
                QuickToolsWidgetController.requestPin(rootActivity)
            }
            btnAddWidget.setSequentialClickListener {
                EventManager.posePoint(PointConfig.widget_tool_click)
                QuickToolsWidgetController.requestPin(rootActivity)
            }
            imageToPdfButton.setSequentialClickListener {
                EventManager.posePoint(PointConfig.tool_image_to_pdf)
                runCatching {
                    (rootActivity as PdfMainActivity).toPhotos()
                }
            }
            scanToPdfButton.setSequentialClickListener {
                runCatching {
                    (rootActivity as PdfMainActivity).toScan()
                }
            }
            // PDF14-MIGRATION: 原工具选择协议直接进入迁入的选择/预览/确认/执行链路。
            word2PDF.setSequentialClickListener {
                EventManager.posePoint(PointConfig.tool_word_to_pdf)
                FileListActivity.open(rootActivity, FileSelectAction.WORD_TO_PDF)
            }
            pdf2Img.setSequentialClickListener { FileListActivity.open(rootActivity, FileSelectAction.PDF_TO_IMAGE) }
            mergePDF.setSequentialClickListener {
                EventManager.posePoint(PointConfig.tool_merge_pdf)
                FileListActivity.open(rootActivity, FileSelectAction.MERGE_PDF)
            }
            splitPDF.setSequentialClickListener {
                EventManager.posePoint(PointConfig.tool_split_pdf)
                FileListActivity.open(rootActivity, FileSelectAction.SPLIT_PDF)
            }
            pdf2word.setSequentialClickListener {
                EventManager.posePoint(PointConfig.tool_pdf_to_word)
                FileListActivity.open(rootActivity, FileSelectAction.PDF_TO_WORD)
            }
            rlPrivate.setSequentialClickListener {
                rootActivity.jumpToActivity<InAppBrowserActivity>(Bundle().apply {
                    putString("title",getString(R.string.app_pp))
                    putString("url","https://sites.google.com/view/asdawdawdqdqwdasd/home")
                })
            }
            rlTerms.setSequentialClickListener {
                rootActivity.jumpToActivity<InAppBrowserActivity>(Bundle().apply {
                    putString("title",getString(R.string.app_terms))
                    putString("url","https://sites.google.com/view/ai-pdf-editer/home")
                })
            }
        }
    }

    override fun setShowView() {
        fBinding.apply {
        }
    }

    override fun onResume() {
        super.onResume()
        // 保存的名称可能是在切换语言前生成的，因此根据语言 code 显示对应的本地名称。
        fBinding.tvLanguage.text = languageNativeNames[LocalDataManager.cacheLanguage] ?: run {
            val systemLocale = LocaleListCompat.getAdjustedDefault()[0]
                ?: resources.configuration.locales[0]
            systemLocale.getDisplayName(systemLocale)
        }
    }

    override fun getBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): ToolFragmentToolBinding {
        return ToolFragmentToolBinding.inflate(inflater, container, false)
    }

    companion object {
        private val languageNativeNames = mapOf(
            "en" to "English",
            "ja" to "日本語",
            "fr" to "Français",
            "ko" to "한국어",
            "de" to "Deutsch",
            "es" to "Español",
            "it" to "Italiano",
            "pt" to "Português",
            "ru" to "Русский",
            "zh_tw" to "繁體中文",
            "zh" to "简体中文",
            "ms" to "Bahasa Melayu",
            "ar" to "العربية",
        )
    }
}
