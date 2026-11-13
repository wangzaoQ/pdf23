package com.pdf.editor.common.core

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.LifecycleObserver

import androidx.lifecycle.viewModelScope
import com.pdf.editor.common.tools.AppLogs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

open class BaseDataModel : ViewModel(), LifecycleObserver {

    val TAG by lazy { javaClass.name }

    var failLiveData = MutableLiveData<String>()

    fun loadData(loadBack: suspend CoroutineScope.() -> Unit, failBack: (Throwable) -> Unit?, type:Int?=0): Job? {
        runCatching {
            return viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
                AppLogs.eLog(TAG,"error1:${throwable.stackTraceToString()}")
                failLiveData.postValue("BaseDataModelError")
                failBack.invoke(throwable)
            }) {
                if (type == 0){
                    loadBack()
                }else {
                    withContext(Dispatchers.IO){
                        loadBack()
                    }
                }
            }
        }.onFailure {
            failLiveData.postValue("BaseDataModelError")
            failBack.invoke(it)
            AppLogs.eLog(TAG,"error2:${it.stackTraceToString()}")
        }
        return null
    }
}
