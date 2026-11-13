package com.pdf.editor.common

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log

/**
 * 接收文档生命周期命令；用户实际启动 App 后才启用该组件。
 */
class ReadService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE_DOCUMENT -> {
                // Entry point for work required before opening a document.
            }

            ACTION_REFRESH_DOCUMENT_METADATA -> {
                // Entry point for refreshing document metadata or the file index.
            }

            ACTION_RELEASE_DOCUMENT_RESOURCES -> {
                // Entry point for releasing resources after document operations finish.
            }

            else -> Log.w(TAG, "Ignoring unsupported service action: ${intent?.action}")
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_PREPARE_DOCUMENT = "com.pdf.editor.action.PREPARE_DOCUMENT"
        const val ACTION_REFRESH_DOCUMENT_METADATA =
            "com.pdf.editor.action.REFRESH_DOCUMENT_METADATA"
        const val ACTION_RELEASE_DOCUMENT_RESOURCES =
            "com.pdf.editor.action.RELEASE_DOCUMENT_RESOURCES"

        private const val TAG = "PdfDocumentCommand"

        /** Manifest 默认禁用，用户启动 App 后允许外部命令唤起。 */
        fun enableAfterAppLaunch(context: Context) {
            val componentName = ComponentName(context, ReadService::class.java)
            val packageManager = context.packageManager
            if (
                packageManager.getComponentEnabledSetting(componentName) ==
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            ) {
                return
            }
            runCatching {
                packageManager.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }.onFailure {
                Log.e(TAG, "enable service after app launch failed", it)
            }
        }
    }
}
