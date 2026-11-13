package com.pdf.editor.common.tools

import android.app.Activity
import android.content.Context
import android.os.Bundle
import com.pdf.editor.MyAPP
import com.pdf.editor.common.ad.ADControl
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.point.EventManager
import com.pdf.editor.cache.LocalDataManager
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.hjq.permissions.permission.PermissionLists
import com.hjq.permissions.permission.base.IPermission

object PermissionManager {
    var TAG = "PermissionManager"

    fun requestManageAllFilesPermission(context: Context, onGranted: (type: Int) -> Unit, onFail: () -> Unit) {
        if (hasAllFilePermission(context)){
            if (LocalDataManager.lastFilePermissionType == 0){
                LocalDataManager.lastFilePermissionType = 1
                EventManager.posePoint(PointConfig.ad_permission_file)
            }
            onGranted(0)
            return
        }
        if (LocalDataManager.isBUser && LocalDataManager.isFirstShowFilePermission){
            ADControl.goBackType = "file"
            LocalDataManager.isFirstShowFilePermission = false
        }
        XXPermissions.with(context)
            .permission(PermissionLists.getManageExternalStoragePermission())
            .request(object : OnPermissionCallback {

                override fun onResult(
                    grantedList: List<IPermission?>,
                    deniedList: List<IPermission?>
                ) {
                    if (deniedList.isEmpty()) {
                        // 所有权限都授予
                        AppLogs.dLog(TAG,"ManageAllFilesPermission success")
                        LocalDataManager.lastFilePermissionType = 2
                        EventManager.posePoint(PointConfig.ad_permission_file)
                        onGranted(0)
                    } else {
                        AppLogs.dLog(TAG,"ManageAllFilesPermission onFail")
                        onFail()
                    }
                }
            })
    }

    fun hasAllFilePermission(context: Context): Boolean {
        return XXPermissions.isGrantedPermission(
            context,
            PermissionLists.getManageExternalStoragePermission()
        )
    }

    fun allowNF(context: Context):Boolean{
        return XXPermissions.isGrantedPermission(
            context,
            PermissionLists.getPostNotificationsPermission()
        )
    }

    fun requestNFPermission(context: Context, onGranted: () -> Unit,onFail: () -> Unit) {
        if (allowNF(context)){
            if (LocalDataManager.lastNFPermissionType == 0){
                LocalDataManager.lastNFPermissionType = 1
                EventManager.posePoint(PointConfig.ad_permission_notice)
            }
            onGranted()
            return
        }
        EventManager.posePoint(PointConfig.push_guide_view, Bundle().apply {
            putString("show_type","system")
        })
        XXPermissions.with(context)
            .permission(PermissionLists.getPostNotificationsPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(
                    grantedList: List<IPermission?>,
                    deniedList: List<IPermission?>
                ) {
                    if (deniedList.isEmpty()) {
                        LocalDataManager.lastNFPermissionType = 2
                        EventManager.posePoint(PointConfig.ad_permission_notice)
                        onGranted()
                    } else {
                        onFail()
                    }
                }
            })
    }

    fun allowSystemAlert(activity: Context):Boolean{
        return XXPermissions.isGrantedPermission(
            activity,
            PermissionLists.getSystemAlertWindowPermission()
        )
    }

    fun ensureSystemAlertPermission(
        activity: Activity,
        onGranted: () -> Unit,onFail: () -> Unit
    ): Boolean {
        ADControl.goBackType = "system"
        XXPermissions.with(activity)
            .permission(PermissionLists.getSystemAlertWindowPermission())
            .request(object : OnPermissionCallback {
                override fun onResult(
                    grantedList: List<IPermission?>,
                    deniedList: List<IPermission?>
                ) {
                    if (deniedList.isEmpty()) {
                        onGranted()
                    } else {
                        onFail()
                    }
                }
            })
        return false
    }
}
