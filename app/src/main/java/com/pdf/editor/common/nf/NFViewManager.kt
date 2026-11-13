package com.pdf.editor.common.nf

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.pdf.editor.MyAPP
import com.pdf.editor.R
import com.pdf.editor.common.nf.NFControl.createLocalizedContext
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.data.NFData
import com.pdf.editor.ui.start.PdfStartActivity
import kotlin.random.Random

object NFViewManager {

    fun getNFView(isLargeView: Boolean, content: String):RemoteViews{
        var layoutId = if (isLargeView){
            R.layout.nf_time_large
        }else{
            R.layout.nf_time_small
        }

        var remoteViews = RemoteViews(MyAPP.instance.packageName, layoutId)
        val ctx = createLocalizedContext(MyAPP.instance)
        remoteViews.apply {
            setTextViewText(R.id.tvPushTitle,content)
            setTextViewText(R.id.tvPushContent,ctx.getString(R.string.app_nf_content))
            setTextViewText(R.id.tvBtn,ctx.getString(R.string.app_open))
            if (isLargeView){
//                remoteViews.setImageViewResource(R.id.ivBtn, R.drawable.btn_animal)
            }
        }
        return remoteViews
    }

    fun getFCMNFView(isLargeView: Boolean, data: NFData):RemoteViews{
        var layoutId = if (isLargeView){
            R.layout.nf_time_large
        }else{
            R.layout.nf_time_small
        }
        var remoteViews = RemoteViews(MyAPP.instance.packageName, layoutId)
        remoteViews.apply {
            setTextViewText(R.id.tvPushTitle,data.title)
            setTextViewText(R.id.tvPushContent,data.body)
        }
        return remoteViews
    }

    fun getTimePushJumpIntent(enum: NFEnum,nfId: Int): PendingIntent {
        val intent = Intent(MyAPP.instance, PdfStartActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        //具体点的是当前通知哪一个区域 状态各有不同
        intent.putExtra(PdfStartActivity.EXTRA_LAUNCH_SCENE, PdfStartActivity.LAUNCH_SCENE_NOTIFICATION)
        intent.putExtra(NFControl.intentParamsFrom, enum.menuName)
        intent.putExtra(NFControl.intentParamsNFID, nfId)
        return PendingIntent.getActivity(MyAPP.instance, getCode(), intent, getFlags())
    }

    fun getFCMPushJumpIntent(enum: NFEnum,nfId: Int): PendingIntent {
        val intent = Intent(MyAPP.instance, PdfStartActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        //具体点的是当前通知哪一个区域 状态各有不同
        intent.putExtra(PdfStartActivity.EXTRA_LAUNCH_SCENE, PdfStartActivity.LAUNCH_SCENE_NOTIFICATION)
        intent.putExtra(NFControl.intentParamsFrom, enum.menuName)
        intent.putExtra(NFControl.intentParamsNFID, nfId)
        return PendingIntent.getActivity(MyAPP.instance, getCode(), intent, getFlags())
    }

    fun getMediaPushJumpIntent(nfId: Int): PendingIntent {
        val intent = Intent(MyAPP.instance, PdfStartActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        //具体点的是当前通知哪一个区域 状态各有不同
        intent.putExtra(PdfStartActivity.EXTRA_LAUNCH_SCENE, PdfStartActivity.LAUNCH_SCENE_MEDIA_NOTIFICATION)
        intent.putExtra(NFControl.intentParamsFrom, NFEnum.NF_MEDIA.menuName)
        intent.putExtra(NFControl.intentParamsNFID, nfId)
        return PendingIntent.getActivity(MyAPP.instance, getCode(), intent, getFlags())
    }

    fun getForegroundRemoteView(enum: NFEnum): RemoteViews? {
        val ctx = createLocalizedContext(MyAPP.instance)
        runCatching {
            var layoutId =  R.layout.nf_foreground
            var remoteViews = RemoteViews(MyAPP.instance.packageName, layoutId)
            remoteViews.apply {
                setTextViewText(R.id.tvHome,ctx.getString(R.string.app_home))
                setTextViewText(R.id.tvSearch,ctx.getString(R.string.app_search))
                setTextViewText(R.id.tvScan,ctx.getString(R.string.app_scan))
                setTextViewText(R.id.tvConvert,ctx.getString(R.string.app_convert))

                remoteViews.setOnClickPendingIntent(
                    R.id.llHome,
                    getForegroundPushJumpIntent(0,)
                )
                remoteViews.setOnClickPendingIntent(
                    R.id.llSearch,
                    getForegroundPushJumpIntent(1)
                )
                remoteViews.setOnClickPendingIntent(
                    R.id.llScan,
                    getForegroundPushJumpIntent(2)
                )
                remoteViews.setOnClickPendingIntent(
                    R.id.llConvert,
                    getForegroundPushJumpIntent(3)
                )
            }
            return remoteViews

        }.onFailure {

        }
        return null
    }

    fun getForegroundPushJumpIntent(index:Int): PendingIntent {
        val intent = Intent(MyAPP.instance, PdfStartActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        //具体点的是当前通知哪一个区域 状态各有不同
        intent.putExtra(PdfStartActivity.EXTRA_LAUNCH_SCENE, PdfStartActivity.LAUNCH_SCENE_NOTIFICATION)
        intent.putExtra(NFControl.intentParamsFrom, NFEnum.NF_FOREGROUND.menuName)
        intent.putExtra(NFControl.intentParamsTimeIndex, index)
        // 前台通知 4 个入口必须有稳定且唯一的 requestCode，否则会被最后一个入口（通常是 scan）覆盖
        return PendingIntent.getActivity(MyAPP.instance, 30000 + index, intent,
            getFlags()
        )
    }


    fun getCode() = (1001..99998).random(Random(System.currentTimeMillis()))

    fun getFlags() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_UPDATE_CURRENT

}
