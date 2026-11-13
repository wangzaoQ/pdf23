package com.pdf.editor.common.core

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class BaseActivityLife : DefaultLifecycleObserver {

    var destoryList:MutableList<(type:Int) -> Unit> = mutableListOf()
    override fun onDestroy(owner: LifecycleOwner) {
        destoryList.forEach {
            it.invoke(0)
        }
        super.onDestroy(owner)
    }

//    override fun onPause(owner: LifecycleOwner) {
//        destoryList.forEach {
//            it.invoke(1)
//        }
//        super.onPause(owner)
//    }

//
//
//    override fun onResume(owner: LifecycleOwner) {
//        destoryList.forEach {
//            it.invoke(2)
//        }
//        super.onResume(owner)
//    }

}