package com.pdf.editor.feature.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.pdf.editor.MyAPP
import com.pdf.editor.R
import com.pdf.editor.common.nf.NFControl
import com.pdf.editor.common.nf.NFViewManager.getFlags
import com.pdf.editor.ui.start.PdfStartActivity
import java.util.Locale

/** 原类：`com.pdf.read.ui.widget.PdfWidgetManager`。 */
object QuickToolsWidgetController {

    private const val PIN_CALLBACK_REQUEST_CODE = 41_001
    private const val ENTRY_REQUEST_CODE = 42_000
    private const val ENTRY_HOME = 0
    private const val ENTRY_SEARCH = 1
    private const val ENTRY_SCAN = 2
    private const val ENTRY_CONVERT = 3

    fun isAdded(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        return manager.getAppWidgetIds(provider(context)).isNotEmpty()
    }
    fun requestPin(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = AppWidgetManager.getInstance(activity)
            if (manager.isRequestPinAppWidgetSupported) {
                val callback = PendingIntent.getBroadcast(
                    activity,
                    PIN_CALLBACK_REQUEST_CODE,
                    Intent(activity, QuickToolsWidgetProvider::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (manager.requestPinAppWidget(provider(activity), null, callback)) {
                    return
                }
            }
        }
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(provider(context))
        ids.forEach { manager.updateAppWidget(it, createRemoteViews(context)) }
    }

    fun createRemoteViews(context: Context): RemoteViews {
        val localizedContext = createLocalizedContext(context)
        return RemoteViews(context.packageName, R.layout.widget_pdf_tools).apply {
            setTextViewText(R.id.widgetTitle, localizedContext.getString(R.string.app_name))
            setTextViewText(R.id.widgetSearchHint, localizedContext.getString(R.string.app_home_search_hint))
            setTextViewText(R.id.widgetHomeLabel, localizedContext.getString(R.string.app_home))
            setTextViewText(R.id.widgetSearchLabel, localizedContext.getString(R.string.app_search))
            setTextViewText(R.id.widgetScanLabel, localizedContext.getString(R.string.app_scan))
            setTextViewText(R.id.widgetConvertLabel, localizedContext.getString(R.string.app_convert))
            setOnClickPendingIntent(R.id.widgetRoot, getJumpIntent(ENTRY_HOME))
            setOnClickPendingIntent(R.id.widgetSearchBar, getJumpIntent(ENTRY_SEARCH))
            setOnClickPendingIntent(R.id.widgetHome, getJumpIntent(ENTRY_HOME))
            setOnClickPendingIntent(R.id.widgetSearch, getJumpIntent(ENTRY_SEARCH))
            setOnClickPendingIntent(R.id.widgetScan, getJumpIntent(ENTRY_SCAN))
            setOnClickPendingIntent(R.id.widgetConvert, getJumpIntent(ENTRY_CONVERT))
        }
    }

    private fun getJumpIntent(index: Int): PendingIntent {
        val intent = Intent(MyAPP.instance, PdfStartActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        intent.putExtra(PdfStartActivity.EXTRA_LAUNCH_SCENE, PdfStartActivity.LAUNCH_SCENE_WIDGET)
        intent.putExtra(NFControl.intentParamsTimeIndex, index)
        return PendingIntent.getActivity(
            MyAPP.instance,
            ENTRY_REQUEST_CODE + index,
            intent,
            getFlags()
        )
    }

    private fun createLocalizedContext(context: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        val configuration = Configuration(context.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            configuration.setLocales(locales.toPlatformLocales())
        } else {
            configuration.setLocale(locales.get(0) ?: Locale.getDefault())
        }
        return context.createConfigurationContext(configuration)
    }

    private fun LocaleListCompat.toPlatformLocales(): LocaleList {
        val values = (0 until size()).mapNotNull { get(it) }.ifEmpty { listOf(Locale.getDefault()) }
        return LocaleList(*values.toTypedArray())
    }

    private fun provider(context: Context) =
        ComponentName(context, QuickToolsWidgetProvider::class.java)
}
