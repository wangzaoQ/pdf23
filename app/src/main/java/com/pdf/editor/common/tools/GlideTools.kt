package com.pdf.editor.common.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.signature.ObjectKey
import java.io.File

object GlideTools {


    fun loadImg(fragment: Fragment?=null, iv: ImageView, url: Any?, width: Int=0, height: Int=0) {
        runCatching {
            if (url == null)return
            val builder =if (fragment !=null) Glide.with(fragment).load(url) else Glide.with(iv).load(url)
            if (url is String) {
                val localFile = File(url)
                if (localFile.exists()) {
                    builder.signature(
                        ObjectKey("${localFile.absolutePath}_${localFile.lastModified()}_${localFile.length()}")
                    )
                }
            }
            if (width!=0 && height!=0){
                builder.override(width,height)
            }
//            builder.transition(DrawableTransitionOptions().crossFade()).into(iv)
            builder.into(iv)
        }.onFailure {
            AppLogs.eLog("GlideTools","loadImg :${it.stackTraceToString()}")
        }
    }

    fun loadNFBitmap(mContext: Context, url: Any?, width:Int, height: Int, bitmapCall:(Bitmap?)->Unit, callFail:(()->Unit)?=null){
        if (url == null || mContext == null){
            callFail?.invoke()
            return
        }
        Glide.with(mContext)
            .load(url)
            .override(width,height)
            .centerCrop()
            .into(object : CustomTarget<Drawable?>() {
                override fun onResourceReady(
                    resource: Drawable,
                    transition: com.bumptech.glide.request.transition.Transition<in Drawable?>?
                ) {
                    runCatching {
                        bitmapCall(resource.toBitmap(width, height, Bitmap.Config.RGB_565))
                    }.onFailure {
                        AppLogs.eLog("GlideTools", "通知图片获取失败")
                        callFail?.invoke()
                    }
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    AppLogs.eLog("GlideTools", "通知图片获取失败")
                    callFail?.invoke()
                }

                override fun onLoadCleared(placeholder: Drawable?) {

                }
            })
    }
}