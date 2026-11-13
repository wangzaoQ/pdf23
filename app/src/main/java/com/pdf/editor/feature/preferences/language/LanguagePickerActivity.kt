package com.pdf.editor.feature.preferences.language

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.chad.library.adapter4.util.setOnDebouncedItemClick
import com.pdf.editor.R
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.AppLogs
import com.pdf.editor.common.tools.jumpToActivity
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADControl.preloadAD
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.feature.preferences.language.model.LanguageOption
import com.pdf.editor.databinding.PdfActivityLanguageSelectedBinding
import com.pdf.editor.ui.PdfMainActivity
import com.pdf.editor.feature.onboarding.OnboardingActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/** 原类：`com.pdf.read.ui.language.PdfLanguageSelectedActivity`。 */
class LanguagePickerActivity : BaseActivity<PdfActivityLanguageSelectedBinding>() {

    companion object {
        const val EXTRA_SELECT_MODE = "select_mode"
        const val SELECT_MODE_APP_LANGUAGE = 0
        const val SELECT_MODE_TRANSLATE_LANGUAGE = 1
        const val EXTRA_SELECTED_LANGUAGE_TAG = "selected_language_tag"
    }

    private var selectMode = SELECT_MODE_APP_LANGUAGE
    private var language = ""
    private var fromType = 0
    private var launchContent = ""

    private val languageAdapter by lazy { LanguageOptionAdapter() }

    override fun getBinding(inflater: LayoutInflater): PdfActivityLanguageSelectedBinding {
        return PdfActivityLanguageSelectedBinding.inflate(layoutInflater)
    }

    override fun setListener() {
        aBinding.flConfirm.setOneClick {
            EventManager.posePoint(PointConfig.language_page_confirm)
            if (fromType == 0){
                LocalDataManager.cacheLanguage = language
                LocalDataManager.cacheLanguageName = getSupportedLanguageName(language)
                AppLogs.dLog(aTAG, "currentLanguage:$language")
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
                finish()
            }else {
                ADShowManager(this, "PdfLanguageSelectedActivity").showScreenAD(
                    PointConfig.AD_POINT_LAN_INT,
                    ADEnum.INT_AD_NEW
                ){type, hasValue ->
                    LocalDataManager.cacheLanguageFirst = false
                    LocalDataManager.cacheLanguage = language
                    LocalDataManager.cacheLanguageName = getSupportedLanguageName(language)
                    AppLogs.dLog(aTAG, "currentLanguage:$language")
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
                    if (LocalDataManager.cacheGuideFirst){
                        jumpToActivity<OnboardingActivity>(Bundle().apply {
                            putString("nfContent", launchContent)
                        })
                        AppLogs.dLog(aTAG, "start OnboardingActivity")
                    }else{
                        jumpToActivity<PdfMainActivity>(Bundle().apply {
                            putString("nfContent", launchContent)
                        })
                    }
                    finish()
                }
            }
        }
        aBinding.ivBack.setOneClick {
            EventManager.posePoint(PointConfig.language_page_back)
            finish()
        }
    }

    override fun setShowView() {
        selectMode = intent.getIntExtra(EXTRA_SELECT_MODE, SELECT_MODE_APP_LANGUAGE)
        fromType = intent.getIntExtra("fromType", 0)
        launchContent = intent.getStringExtra("nfContent") ?: ""
        language = LocalDataManager.cacheLanguage

        if (selectMode == SELECT_MODE_TRANSLATE_LANGUAGE) {
            aBinding.flConfirm.visibility = View.GONE
        }
        if (fromType == 0) {
            aBinding.ivBack.visibility = View.VISIBLE
        }
        if (LocalDataManager.cacheLanguageFirst) {
            EventManager.posePoint(PointConfig.language_page)
        }

        aBinding.rv.apply {
            layoutManager = LinearLayoutManager(this@LanguagePickerActivity)
            adapter = languageAdapter
            languageAdapter.setOnDebouncedItemClick { _, _, position ->
                if (position > languageAdapter.items.size - 1) return@setOnDebouncedItemClick
                language = languageAdapter.items[position].tag
                languageAdapter.items.forEach { it.isSelected = false }
                languageAdapter.items[position].isSelected = true
                languageAdapter.notifyDataSetChanged()
                if (selectMode == SELECT_MODE_TRANSLATE_LANGUAGE) {
                    aBinding.root.postDelayed({
                        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_SELECTED_LANGUAGE_TAG, language))
                        finish()
                    }, 300)
                }
            }
        }
        loadData()
        aBinding.root.post {
            preloadAD(ADEnum.BANNER_AD_2)
            if (LocalDataManager.cacheLanguageFirst){
                preloadAD(ADEnum.BANNER_AD_1)
                preloadAD(ADEnum.INT_AD_USER)
            }
            addLaunch(success = {
                while (true) {
                    if (ADControl.getCacheAD(ADEnum.BANNER_AD_2) != null && getActivityStatus() && ADControl.allowAds) {
                        withContext(Dispatchers.Main) {
                            var adShowManager = ADShowManager(
                                this@LanguagePickerActivity,
                                "PdfLanguageSelectedActivity"
                            )
                            adShowManager.showADBannerOrNative(
                                aBinding.flADRoot, ADEnum.BANNER_AD_2,PointConfig.AD_POINT_NAT,
                            ) { type, hasValue ->

                            }
                        }
                        break
                    }
                    delay(1000)
                }
            }, failBack = {})
        }
    }

    override fun onBackPressed() {
        if (fromType == 0) {
            aBinding.ivBack.performClick()
        } else {
            super.onBackPressed()
        }
    }
    val supportedLanguages = listOf(
        "en" to R.string.app_english,
        "ja" to R.string.app_japanese,
        "fr" to R.string.app_french,
        "ko" to R.string.app_korean,
        "de" to R.string.app_german,
        "es" to R.string.app_spanish,
        "it" to R.string.app_italian,
        "pt" to R.string.app_portuguese,
        "ru" to R.string.app_russian,
        "zh_tw" to R.string.app_chinese_tw,
        "zh" to R.string.app_chinese,
        "ms" to R.string.app_malay,
        "ar" to R.string.app_arabic
    )
    val supportedDrawableId = hashMapOf(
        "en" to R.drawable.ic_usa,
        "ja" to R.drawable.ic_ja,
        "fr" to R.drawable.ic_fr,
        "ko" to R.drawable.ic_ko,
        "de" to R.drawable.ic_de,
        "es" to R.drawable.ic_es,
        "it" to R.drawable.ic_it,
        "pt" to R.drawable.ic_pt,
        "ru" to R.drawable.ic_ru,
        "zh_tw" to R.drawable.ic_zh,
        "zh" to R.drawable.ic_zh,
        "ar" to R.drawable.ic_ar
    )

    private fun getSupportedLanguageName(languageCode: String): String {
        val resId = supportedLanguages.firstOrNull { it.first == languageCode }?.second
            ?: R.string.app_english
        return getString(resId)
    }

    private fun loadData() {
        val systemLanguage = Locale.getDefault().language
        val defaultLanguage = when {
            language in supportedLanguages.map { it.first } -> language
            systemLanguage in supportedLanguages.map { it.first } -> systemLanguage
            else -> "en"
        }

        val dataList = supportedLanguages.map { (code, resId) ->
            LanguageOption(
                getString(resId),
                code,
                if (selectMode == SELECT_MODE_TRANSLATE_LANGUAGE) false else code == defaultLanguage,
                drawableId = supportedDrawableId[code] ?: R.drawable.ic_usa
            )
        }.sortedByDescending { it.isSelected }

        languageAdapter.submitList(dataList)
        aBinding.tvLanguage.text = getString(R.string.app_choose_language)
    }
}
