package com.pdf.editor.common.point

import android.os.Build
import com.pdf.editor.MyAPP
import com.pdf.editor.common.tools.AppLogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import java.io.IOException
import java.net.Proxy

object EventNet {

    var NET_TAG = "PointNet"

    val pointUrl by lazy {
        StringBuffer(
            if (MyAPP.isDebug) "https://test-larynges.nogappdfreaderscanner.com/broom/flinty"
            else "https://larynges.nogappdfreaderscanner.com/claimant/courtesy/elate"
        )
            .toString()
    }

    fun getPonitNet() : OkHttpClient {

// 创建日志拦截器
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY  // 输出请求和响应的完整信息
        }
        var okHttpClientBuilder = OkHttpClient.Builder()
            .apply {
                if (!MyAPP.isDebug) { // 非测试环境
                    proxy(Proxy.NO_PROXY) // 禁止抓包
                }
            }
            .retryOnConnectionFailure(false)
//            //其他配置
//            .addInterceptor(object : Interceptor {
//                override fun intercept(chain: Interceptor.Chain): Response {
//                    var newRequest = chain.request().newBuilder()
//                        .build()
//                    return chain.proceed(newRequest)
//                }
//            })
        if (MyAPP.isDebug) {
            okHttpClientBuilder.addInterceptor(loggingInterceptor)
        }
        return okHttpClientBuilder.build()
    }


    interface PointCallback {

        fun onSuccess(response: Response)

        fun onFailed() {}

    }

    fun sendValue(jsonObject: JSONObject?, tag:String?=null,defaultTryCount:Int, callback: PointCallback?=null){
        if (jsonObject == null)return
        val request = Request.Builder()
        request.url(pointUrl)
        request.post(jsonObject.toString().toRequestBody("application/json".toMediaType()))
        getPonitNet().newCall(request.build())?.enqueue(object :
            Callback {
            override fun onFailure(call: Call, e: IOException) {
                AppLogs.eLog(NET_TAG,e.stackTraceToString())
                tbaFailedTry(jsonObject,tag,defaultTryCount,callback)
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.code == 200){
                    callback?.onSuccess(response)
                    AppLogs.dLog(NET_TAG,"tbaSuccess key:${tag} jsonObject:${jsonObject}")
                }  else tbaFailedTry(jsonObject,tag,defaultTryCount,callback,"onResponse")
            }
        })
    }


    private fun tbaFailedTry(jsonObject: JSONObject?, tag:String?=null, defaultTryCount:Int,callback: PointCallback?=null,from:String="onFailure") {
        AppLogs.dLog(NET_TAG,"tbaFailedTry tag:${tag} defaultTryCount:${defaultTryCount} from:${from}")
        var tempCount = defaultTryCount-1
        if (tempCount > 0) {
            MyAPP.instance.applicationScope.launch {
                delay(3000L)
                withContext(Dispatchers.Main){
                    sendValue(jsonObject,tag,tempCount,callback)
                }
            }
        }else{
            callback?.onFailed()
        }
    }

    fun postEvent(jsonObject: JSONObject, tag:String?="", callback: PointCallback?=null) {
        sendValue(jsonObject,tag,3,callback)
    }
}
