package com.pdf.editor.ui.home

import android.appwidget.AppWidgetManager
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import com.blankj.utilcode.util.KeyboardUtils
import com.pdf.editor.R
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.core.BaseFragment
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.ScanConfig
import kotlin.math.roundToInt
import com.pdf.editor.common.tools.setOneClick
import com.pdf.editor.databinding.PdfFragmentHomeBinding
import com.pdf.editor.feature.files.actions.FileSortPopup
import com.pdf.editor.ui.detail.FileListFragment
import com.pdf.editor.feature.widget.QuickToolsWidgetController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class HomeFragment : BaseFragment<PdfFragmentHomeBinding>() {

    private val searchHeaderTransition by lazy(LazyThreadSafetyMode.NONE) {
        AutoTransition().apply {
            duration = 220
            interpolator = FastOutSlowInInterpolator()
        }
    }

    private val tabTitles by lazy(LazyThreadSafetyMode.NONE) {
        listOf(
            getString(R.string.app_all_files),
            getString(R.string.app_pdf),
            getString(R.string.app_word),
            getString(R.string.app_excel),
            getString(R.string.app_ppt),
            getString(R.string.app_txt)
        )
    }

    var oldPosition = -1
    var startTime = 0L

    private val fragments = mutableListOf<FileListFragment>()
    private var mediator: TabLayoutMediator? = null
    private var isPageChangeCallbackRegistered = false
    private var pendingSearchFocus = false
    private val pageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            EventManager.posePoint(PointConfig.file_filter_click)
            if (oldPosition != position){
                if (fBinding.etSearch.text.isNotEmpty()) {
                    fBinding.etSearch.setText("")
                }
                fBinding.etSearch.clearFocus()
            }
            oldPosition = position
            updateSortImg(position)
            EventManager.posePoint(PointConfig.home_top_tab)
            if (System.currentTimeMillis()-startTime>1000){
                ADShowManager(rootActivity, "HomeFragment").showScreenAD(PointConfig.AD_POINT_INT2, ADEnum.INT_AD_USER){ type, hasValue ->

                }
            }
        }
    }

    override fun getBinding(inflater: LayoutInflater, container: ViewGroup?): PdfFragmentHomeBinding {
        return PdfFragmentHomeBinding.inflate(layoutInflater)
    }

    override fun setShowView() {
        startTime = System.currentTimeMillis()
        fragments.clear()
        fragments.add(FileListFragment.newInstance(ScanConfig.FILE_TYPE_ALL, ScanConfig.FROM_TAG_FILE))
        fragments.add(FileListFragment.newInstance(ScanConfig.FILE_TYPE_PDF, ScanConfig.FROM_TAG_FILE))
        fragments.add(FileListFragment.newInstance(ScanConfig.FILE_TYPE_DOC, ScanConfig.FROM_TAG_FILE))
        fragments.add(FileListFragment.newInstance(ScanConfig.FILE_TYPE_EXCEL, ScanConfig.FROM_TAG_FILE))
        fragments.add(FileListFragment.newInstance(ScanConfig.FILE_TYPE_PPT, ScanConfig.FROM_TAG_FILE))
        fragments.add(FileListFragment.newInstance(ScanConfig.FILE_TYPE_TEXT, ScanConfig.FROM_TAG_FILE))
        fBinding.vp2.apply {
            offscreenPageLimit = fragments.size
            isUserInputEnabled = true
            adapter = HomePagerAdapter(this@HomeFragment, fragments)
        }
        mediator?.detach()
        mediator = TabLayoutMediator(fBinding.tabLayout, fBinding.vp2) { tab, position ->
            tab.customView = createTabView(
                title = tabTitles[position],
                selected = position == 0,
                isLast = position == tabTitles.lastIndex
            )
        }.also { it.attach() }
        if (!isPageChangeCallbackRegistered) {
            fBinding.vp2.registerOnPageChangeCallback(pageChangeCallback)
            isPageChangeCallbackRegistered = true
        }
        syncTabViews()
        updateSortImg(fBinding.vp2.currentItem)
    }

    override fun setListener() {
        fBinding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateTabView(tab, true)
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                updateTabView(tab, false)
            }

            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
        fBinding.llSort.setOneClick {
            EventManager.posePoint(PointConfig.file_sort_change)
            showSortPop()
        }
        fBinding.tvCommit.setOneClick {
            EventManager.posePoint(PointConfig.widget_banner_click)
            QuickToolsWidgetController.requestPin(rootActivity)
        }
        fBinding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {
                val searchContent = p0?.toString()?.trim().orEmpty()
                getFragmentAt(fBinding.vp2.currentItem)?.search(searchContent)
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }
        })
        fBinding.etSearch.onFocusChangeListener = object : View.OnFocusChangeListener {
            override fun onFocusChange(p0: View?, hasFocus: Boolean) {
                updateSearchHeaderState(hasFocus, true)
                if (hasFocus){
                    EventManager.posePoint(PointConfig.search_click)
                }
            }
        }
        fBinding.etSearch.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                KeyboardUtils.hideSoftInput(fBinding.etSearch)
                fBinding.etSearch.clearFocus()
                true
            } else {
                false
            }
        }
        updateSearchHeaderState(fBinding.etSearch.hasFocus(), false)
    }

    fun showSortPop() {
        val currentSortType = getCurrentFragment()?.getCurrentSortType() ?: ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST
        FileSortPopup(rootActivity).createPop(currentSortType) { sortType ->
            if (currentSortType == sortType) {
                return@createPop
            }
            getCurrentFragment()?.updateBySort(sortType)
            updateSortImg(fBinding.vp2.currentItem)
        }
    }

    override fun startLoadData() {
        rootActivity.addLaunch(success = {
            while (true){
                if (rootActivity.getActivityStatus() && ADControl.getCacheAD(ADEnum.BANNER_AD_1)!=null && LocalDataManager.isBUser && ADControl.allowAds){
                    withContext(Dispatchers.Main){
                        showBannerAD()
                    }
                    break
                }
                delay(1000)
            }
        }, failBack = {})
    }

    private fun showBannerAD() {
        var adShowManager = ADShowManager(rootActivity,"HomeFragment")
        adShowManager.showADBannerOrNative(fBinding.flADRoot,ADEnum.BANNER_AD_1, PointConfig.AD_POINT_BANNER1,this){ type, hasValue ->

        }
    }

    override fun onDestroyView() {
        if (isPageChangeCallbackRegistered) {
            fBinding.vp2.unregisterOnPageChangeCallback(pageChangeCallback)
            isPageChangeCallbackRegistered = false
        }
        mediator?.detach()
        mediator = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        updateWidgetUI()
        applyPendingSearchFocus()
    }

    private fun updateWidgetUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            if (QuickToolsWidgetController.isAdded(rootActivity)) {
                fBinding.rlWidget.visibility =  View.GONE
            } else {
                val manager = AppWidgetManager.getInstance(rootActivity)
                if (manager.isRequestPinAppWidgetSupported){
                    fBinding.rlWidget.visibility = View.VISIBLE
                }else{
                    fBinding.rlWidget.visibility =  View.GONE
                }
            }
        }else{
            fBinding.rlWidget.visibility =  View.GONE
        }
    }

    fun focusSearch() {
        pendingSearchFocus = true
        applyPendingSearchFocus()
    }

    private fun applyPendingSearchFocus() {
        if (!pendingSearchFocus || !isResumed || view == null || !::fBinding.isInitialized) {
            return
        }
        fBinding.etSearch.post {
            if (!pendingSearchFocus || !isResumed || view == null) return@post
            if (fBinding.etSearch.requestFocus()) {
                pendingSearchFocus = false
                fBinding.etSearch.post {
                    KeyboardUtils.showSoftInput(fBinding.etSearch)
                }
            }
        }
    }

    private fun createTabView(title: String, selected: Boolean, isLast: Boolean): TextView {
        return (LayoutInflater.from(rootActivity).inflate(
            R.layout.item_home_tab,
            fBinding.tabLayout,
            false
        ) as TextView).apply {
            text = title
            isSelected = selected
            val layoutParams = ViewGroup.MarginLayoutParams(
                70.dpToPx(),
                40.dpToPx()
            )
            layoutParams.marginEnd = if (isLast) 0 else 8.dpToPx()
            this.layoutParams = layoutParams
            background = rootActivity.getDrawable(
                if (selected) R.drawable.bg_home_tab_selected else R.drawable.bg_home_tab_unselected
            )
        }
    }

    private fun Int.dpToPx(): Int =
        (this * resources.displayMetrics.density).roundToInt()

    private fun updateTabView(tab: TabLayout.Tab, selected: Boolean) {
        val view = tab.customView as? TextView ?: return
        view.isSelected = selected
        view.background = rootActivity.getDrawable(
            if (selected) R.drawable.bg_home_tab_selected else R.drawable.bg_home_tab_unselected
        )
    }

    private fun syncTabViews() {
        for (index in 0 until fBinding.tabLayout.tabCount) {
            val tab = fBinding.tabLayout.getTabAt(index) ?: continue
            updateTabView(tab, index == fBinding.tabLayout.selectedTabPosition)
        }
    }

    private fun getCurrentFragment(): FileListFragment? {
        return getFragmentAt(fBinding.vp2.currentItem)
    }

    private fun getFragmentAt(position: Int): FileListFragment? {
        return fragments.getOrNull(position)
    }

    private fun updateSortImg(position: Int) {
        val sortType = getFragmentAt(position)?.getCurrentSortType() ?: ScanConfig.SORT_TYPE_OPEN_TIME_NEWEST
    }


    private fun updateSearchHeaderState(hasFocus: Boolean, animate: Boolean) {
        val targetVisibility = if (hasFocus) View.GONE else View.VISIBLE
        if (fBinding.ivFileLogo.visibility == targetVisibility) {
            return
        }
        val container = fBinding.ivFileLogo.parent as? ViewGroup
        if (animate && container != null) {
            TransitionManager.beginDelayedTransition(container, searchHeaderTransition)
        }
        fBinding.ivFileLogo.visibility = targetVisibility
    }
}
