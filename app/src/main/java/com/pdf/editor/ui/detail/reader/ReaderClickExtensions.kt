package com.pdf.editor.ui.detail.reader

// PDF14-MIGRATION: 仅迁移包路径；序列点击实现保持 pdf14 原样。

import android.view.View
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.channels.actor


fun View.setSequentialClickListener(action: suspend (View) -> Unit) {
    // 启动一个 actor
    val eventActor = MainScope().actor<View>(Dispatchers.Main) { // 执行时不接收新来的消息
//        val eventActor = GlobalScope.actor<View>(Dispatchers.Main, capacity = Channel.CONFLATED) { // 总是执行最新的消息,会停掉之前的操作
        for (event in channel) {
            action(event) // 将事件传递给 action
        }
    }
    // 设置一个监听器来启用 actor
    setOnClickListener {
        eventActor.trySend(it).isSuccess
    }
}