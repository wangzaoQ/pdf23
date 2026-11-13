package com.pdf.editor.ui.tools.document.file

import android.content.Context
import com.pdf.editor.common.tools.PermissionManager

/** PDF14-MIGRATION: 源工具权限接口委派当前项目，保留现有授权回调与埋点。 */
object ToolPermissionManager {
    fun hasPermission(context: Context): Boolean = PermissionManager.hasAllFilePermission(context)

    fun request(context: Context, onGranted: () -> Unit, onDenied: () -> Unit) {
        PermissionManager.requestManageAllFilesPermission(context, { onGranted() }, onDenied)
    }
}
