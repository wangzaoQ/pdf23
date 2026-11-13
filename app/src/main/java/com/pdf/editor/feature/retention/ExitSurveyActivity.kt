package com.pdf.editor.feature.retention

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.chad.library.adapter4.BaseQuickAdapter
import com.pdf.editor.R
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.ad.ADEnum
import com.pdf.editor.common.ad.ADShowManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.core.BaseActivity
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.common.tools.jumpToActivity
import com.pdf.editor.feature.retention.model.ExitReasonOption
import com.pdf.editor.databinding.ActivityUninstallInfoBinding
import com.pdf.editor.ui.PdfMainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** 原类：`com.pdf.read.ui.uninstall.PdfUninstallInfoActivity`。 */
class ExitSurveyActivity : BaseActivity<ActivityUninstallInfoBinding>() {
    override fun getBinding(inflater: LayoutInflater): ActivityUninstallInfoBinding {
        return ActivityUninstallInfoBinding.inflate(layoutInflater)
    }

    private val infoAdapter = ExitReasonAdapter()
    private val dataList = mutableListOf<ExitReasonOption>()

    override fun setListener() {
        aBinding.apply {
            btnKeepApp.setOneClick {
                EventManager.posePoint(PointConfig.uninstall_feedback_select, Bundle().apply {
                    putInt(PointKey.type, 1)
                })
                addFeedback()
                startActivity(
                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                )
            }
            tvPersistUninstall.setOneClick { toMain() }
            ivBack.setOneClick {
                addFeedback()
                toMain()
            }
        }
    }

    private fun addFeedback() {
        val feedback = dataList
            .filter { it.selected }
            .joinToString(",") { it.id.toString() }
        EventManager.posePoint(PointConfig.uninstall_feedback, Bundle().apply {
            putString(PointKey.type, feedback)
        })
    }

    override fun setShowView() {
        ADControl.preloadAD(ADEnum.BANNER_AD_1)
        EventManager.posePoint(PointConfig.uninstall_feedback_page)
        aBinding.apply {
            rv.layoutManager = LinearLayoutManager(
                this@ExitSurveyActivity,
                LinearLayoutManager.VERTICAL,
                false
            )
            rv.adapter = infoAdapter

            val reasons = listOf(
                getString(R.string.uninstall_reason_difficult),
                getString(R.string.uninstall_reason_ads),
                getString(R.string.uninstall_reason_reading),
                getString(R.string.uninstall_reason_notifications),
                getString(R.string.uninstall_reason_editing),
                getString(R.string.uninstall_reason_builtin),
                getString(R.string.uninstall_reason_other)
            )
            reasons.forEachIndexed { index, item ->
                dataList.add(ExitReasonOption().apply {
                    content = item
                    id = index + 1
                })
            }

            infoAdapter.submitList(dataList)
            infoAdapter.setOnItemClickListener(
                object : BaseQuickAdapter.OnItemClickListener<ExitReasonOption> {
                    override fun onClick(
                        adapter: BaseQuickAdapter<ExitReasonOption, *>,
                        view: View,
                        position: Int
                    ) {
                        dataList[position].selected = !dataList[position].selected
                        infoAdapter.notifyDataSetChanged()
                        etSearch.visibility = if (dataList.last().selected) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }
                        btnKeepApp.isEnabled = dataList.any { it.selected }
                    }
                }
            )
        }
    }

    private fun toMain() {
        EventManager.posePoint(PointConfig.uninstall_feedback_select, Bundle().apply {
            putInt(PointKey.type, 0)
        })
        jumpToActivity<PdfMainActivity>()
        finish()
    }
}
