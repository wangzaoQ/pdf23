package com.pdf.editor.common.tools

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.pdf.editor.MyAPP
import com.pdf.editor.R
import com.pdf.editor.cache.LocalDataManager
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.config.PointKey
import com.pdf.editor.common.nf.NFControl
import com.pdf.editor.common.nf.NFEnum
import com.pdf.editor.common.nf.NFViewManager
import com.pdf.editor.common.point.EventManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

object OtherTools {

    var lastMediaPush = 0L

    val bitmap:Bitmap by lazy {
        // ✅ 清空任何媒体信息
        BitmapFactory.decodeResource(
            MyAPP!!.instance.resources,
            R.drawable.ic_nf_large
        )
    }

    @SuppressLint("MissingPermission")
    fun showNF2() {
        if (!LocalDataManager.isBUser)return
        MyAPP.instance.applicationScope.launch(Dispatchers.Main) {
            delay(1000)
            if (MyAPP.instance.lifecycleApp.count > 0) {
                AppLogs.dLog(NFControl.TAG, "showMedia skipped: app is foreground")
                return@launch
            }
            AppLogs.dLog(NFControl.TAG, "showMedia ")
            NFControl.newChannel(NFEnum.NF_MEDIA)
            var random = Random.nextInt(NFControl.timePushTexts.size)
            var nfId = NFControl.getRandomCode()
            val contentText = NFControl.getTimePushText(random)
            updateMediaSession(contentText)
            val contentIntent = NFViewManager.getMediaPushJumpIntent(nfId)
            var build = NotificationCompat.Builder(MyAPP.instance, NFEnum.NF_MEDIA.channelId)
                .setSmallIcon(R.mipmap.ic_small)          // 必须
                .setContentTitle(MyAPP.instance.getString(R.string.app_name))
                .setContentText(contentText)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setGroupSummary(false)
                .setGroup(NFEnum.NF_MEDIA.menuName)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setPriority(NFEnum.NF_MEDIA.nfPriority)
                .setLargeIcon(bitmap)
                .setContentIntent(contentIntent)
                .setWhen(System.currentTimeMillis())
                .addAction(R.mipmap.ic_small, MyAPP.instance.getString(R.string.app_name), contentIntent)
                // 关键：MediaStyle，但不 addAction
                .setStyle(buildMediaStyle(mediaSession))

                .build()
            EventManager.posePoint(PointConfig.push, Bundle().apply {
                putString(PointKey.source, NFEnum.NF_MEDIA.pointTag)
            })
            runCatching {
                NFControl.manager.notify(nfId, build)
                lastMediaPush = System.currentTimeMillis()
                AppLogs.dLog(NFControl.TAG, "showMedia success")
            }.onFailure {
                AppLogs.dLog(NFControl.TAG, "showMedia fail:${it.message}")
            }
        }

    }

    private fun updateMediaSession(contentText: String) {
        invokeMethod(
            mediaSession,
            // setMetadata
            "setMetadata",
            arrayOf(mediaMetadataClass),
            buildUpdatedMediaMetadata(contentText)
        )
        invokeMethod(
            mediaSession,
            // setPlaybackState
            "setPlaybackState",
            arrayOf(playbackStateClass),
            buildUpdatedPlaybackState()
        )
        invokeMethod(
            mediaSession,
            // setActive
            "setActive",
            arrayOf(Boolean::class.javaPrimitiveType!!),
            true
        )
    }

    private val mediaSessionClass by lazy {
        // android.support.v4.media.session.MediaSessionCompat
        Class.forName("android.support.v4.media.session.MediaSessionCompat")
    }

    private val mediaSessionTokenClass by lazy {
        // android.support.v4.media.session.MediaSessionCompat$Token
        Class.forName("android.support.v4.media.session.MediaSessionCompat\$Token")
    }

    private val mediaMetadataClass by lazy {
        // android.support.v4.media.MediaMetadataCompat
        Class.forName("android.support.v4.media.MediaMetadataCompat")
    }

    private val mediaMetadataBuilderClass by lazy {
        // android.support.v4.media.MediaMetadataCompat$Builder
        Class.forName("android.support.v4.media.MediaMetadataCompat\$Builder")
    }

    private val playbackStateClass by lazy {
        // android.support.v4.media.session.PlaybackStateCompat
        Class.forName("android.support.v4.media.session.PlaybackStateCompat")
    }

    private val playbackStateBuilderClass by lazy {
        // android.support.v4.media.session.PlaybackStateCompat$Builder
        Class.forName("android.support.v4.media.session.PlaybackStateCompat\$Builder")
    }

    private val mediaStyleClass by lazy {
        // androidx.media.app.NotificationCompat$MediaStyle
        Class.forName("androidx.media.app.NotificationCompat\$MediaStyle")
    }

    private val mediaSession by lazy {
        // media_session
        createMediaSession(MyAPP.instance, "media_session")
    }

    private fun createMediaSession(context: Context, tag: String): Any {
        val constructor = mediaSessionClass.getConstructor(Context::class.java, String::class.java)
        val mediaSession = constructor.newInstance(context, tag)
        // setFlags
        invokeMethod(
            mediaSession,
            "setFlags",
            arrayOf(Int::class.javaPrimitiveType!!),
            3
        )
        // setPlaybackState
        invokeMethod(
            mediaSession,
            "setPlaybackState",
            arrayOf(playbackStateClass),
            buildInitialPlaybackState()
        )
        // setMetadata
        invokeMethod(
            mediaSession,
            "setMetadata",
            arrayOf(mediaMetadataClass),
            buildInitialMediaMetadata()
        )
        // setActive
        invokeMethod(
            mediaSession,
            "setActive",
            arrayOf(Boolean::class.javaPrimitiveType!!),
            true
        )
        return mediaSession
    }

    private fun buildMediaStyle(mediaSession: Any): NotificationCompat.Style {
        val mediaStyle = mediaStyleClass.getConstructor().newInstance()
        val token = requireNotNull(invokeMethod(
            mediaSession,
            // getSessionToken
            "getSessionToken",
            emptyArray()
        ))
        // setMediaSession
        val setMediaSessionMethod = mediaStyleClass.getMethod("setMediaSession", mediaSessionTokenClass)
        setMediaSessionMethod.invoke(mediaStyle, token)
        // setShowActionsInCompactView
        val showActionsMethod = mediaStyleClass.getMethod("setShowActionsInCompactView", IntArray::class.java)
        showActionsMethod.invoke(mediaStyle, intArrayOf(0))
        return mediaStyle as NotificationCompat.Style
    }

    private fun buildInitialMediaMetadata(): Any {
        val builder = mediaMetadataBuilderClass.getConstructor().newInstance()
        invokeMethod(
            builder,
            // putBitmap
            "putBitmap",
            arrayOf(String::class.java, Bitmap::class.java),
            // METADATA_KEY_ALBUM_ART
            getStaticField(mediaMetadataClass, "METADATA_KEY_ALBUM_ART"),
            bitmap
        )
        invokeMethod(
            builder,
            // putBitmap
            "putBitmap",
            arrayOf(String::class.java, Bitmap::class.java),
            // METADATA_KEY_ART
            getStaticField(mediaMetadataClass, "METADATA_KEY_ART"),
            bitmap
        )
        return requireNotNull(invokeMethod(
            builder,
            // build
            "build",
            emptyArray()
        ))
    }

    private fun buildUpdatedMediaMetadata(contentText: String): Any {
        val builder = mediaMetadataBuilderClass.getConstructor().newInstance()
        invokeMethod(
            builder,
            // putString
            "putString",
            arrayOf(String::class.java, String::class.java),
            // METADATA_KEY_TITLE
            getStaticField(mediaMetadataClass, "METADATA_KEY_TITLE"),
            MyAPP.instance.getString(R.string.app_name)
        )
        invokeMethod(
            builder,
            // putString
            "putString",
            arrayOf(String::class.java, String::class.java),
            // METADATA_KEY_ARTIST
            getStaticField(mediaMetadataClass, "METADATA_KEY_ARTIST"),
            contentText
        )
        invokeMethod(
            builder,
            // putBitmap
            "putBitmap",
            arrayOf(String::class.java, Bitmap::class.java),
            // METADATA_KEY_ALBUM_ART
            getStaticField(mediaMetadataClass, "METADATA_KEY_ALBUM_ART"),
            bitmap
        )
        invokeMethod(
            builder,
            // putBitmap
            "putBitmap",
            arrayOf(String::class.java, Bitmap::class.java),
            // METADATA_KEY_ART
            getStaticField(mediaMetadataClass, "METADATA_KEY_ART"),
            bitmap
        )
        return requireNotNull(invokeMethod(
            builder,
            // build
            "build",
            emptyArray()
        ))
    }

    private fun buildInitialPlaybackState(): Any {
        val builder = playbackStateBuilderClass.getConstructor().newInstance()
        invokeMethod(
            builder,
            // setActions
            "setActions",
            arrayOf(Long::class.javaPrimitiveType!!),
            0L
        )
        invokeMethod(
            builder,
            // setState
            "setState",
            arrayOf(
                Int::class.javaPrimitiveType!!,
                Long::class.javaPrimitiveType!!,
                Float::class.javaPrimitiveType!!
            ),
            // STATE_NONE
            getStaticField(playbackStateClass, "STATE_NONE"),
            // PLAYBACK_POSITION_UNKNOWN
            getStaticField(playbackStateClass, "PLAYBACK_POSITION_UNKNOWN"),
            0f
        )
        return requireNotNull(invokeMethod(
            builder,
            // build
            "build",
            emptyArray()
        ))
    }

    private fun buildUpdatedPlaybackState(): Any {
        val builder = playbackStateBuilderClass.getConstructor().newInstance()
        invokeMethod(
            builder,
            // setActions
            "setActions",
            arrayOf(Long::class.javaPrimitiveType!!),
            // ACTION_PLAY_PAUSE
            getStaticField(playbackStateClass, "ACTION_PLAY_PAUSE")
        )
        invokeMethod(
            builder,
            // setState
            "setState",
            arrayOf(
                Int::class.javaPrimitiveType!!,
                Long::class.javaPrimitiveType!!,
                Float::class.javaPrimitiveType!!
            ),
            // STATE_PLAYING
            getStaticField(playbackStateClass, "STATE_PLAYING"),
            // PLAYBACK_POSITION_UNKNOWN
            getStaticField(playbackStateClass, "PLAYBACK_POSITION_UNKNOWN"),
            1f
        )
        return requireNotNull(invokeMethod(
            builder,
            // build
            "build",
            emptyArray()
        ))
    }

    private fun invokeMethod(
        target: Any,
        methodName: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any?
    ): Any? {
        val method = target.javaClass.getMethod(methodName, *parameterTypes)
        return method.invoke(target, *args)
    }

    private fun getStaticField(clazz: Class<*>, fieldName: String): Any {
        return clazz.getField(fieldName).get(null)
    }
}
