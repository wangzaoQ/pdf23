package com.pdf.editor.common.tools

import android.os.Environment
import android.os.StatFs
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.util.Base64
import java.util.LinkedList
import java.util.Vector

object DataUtils {

    val gson: Gson by lazy {
        GsonBuilder()
            .create()
    }

    /** 将Json数据解析成相应的映射对象列表  */
    fun <T> getListByGson(json: String?, cls: Class<T>?): MutableList<T>? {
        runCatching {
            val mList = ArrayList<T>()
            val array = JsonParser().parse(json).asJsonArray
            if (array != null && array.size() > 0) {
                for (elem in array) {
                    mList.add(gson.fromJson(elem, cls))
                }
            }
            return mList
        }.onFailure {
            AppLogs.eLog("gson", it.stackTraceToString())
        }
        return null
    }

    fun <T> getVectorByGson(json: String?, cls: Class<T>?): Vector<T>? {
        runCatching {
            val mList = Vector<T>()
            val array = JsonParser().parse(json).asJsonArray
            if (array != null && array.size() > 0) {
                for (elem in array) {
                    mList.add(gson.fromJson(elem, cls))
                }
            }
            return mList
        }.onFailure {
            AppLogs.eLog("gson",it.stackTraceToString())
        }
        return null
    }

    /** 将Json数据解析成相应的映射对象列表  */
    fun <T> getLinkedListByGson(json: String?, cls: Class<T>?): LinkedList<T>? {
        runCatching {
            val mList = LinkedList<T>()
            val array = JsonParser().parse(json).asJsonArray
            if (array != null && array.size() > 0) {
                for (elem in array) {
                    mList.add(gson.fromJson(elem, cls))
                }
            }
            return mList
        }.onFailure {
            AppLogs.eLog("gson", it.stackTraceToString())
        }
        return null
    }

    fun <T> parseBeanByGson(jsonData: String?, type: Class<T>?): T? {
        runCatching {
            return gson.fromJson<T>(jsonData, type)!!
        }.onFailure {
            AppLogs.eLog("gson", it.stackTraceToString())
        }
        return null
    }

    fun stringToMap(content:String):Map<String,String>{
        val gson = Gson()
        val mapType = object : TypeToken<Map<String?, String?>?>() {}.getType()
        return gson.fromJson(content, mapType)
    }

    fun getMapByGson(jsonData: String):HashMap<String,Any>?{
        runCatching {
            val mapType = object : TypeToken<HashMap<String?, Any?>?>() {}.getType()
            return  gson.fromJson(jsonData, mapType)
        }.onFailure {
            AppLogs.eLog("gson", it.stackTraceToString())
        }
        return null
    }

    fun toJson(data: Any?): String {
        runCatching {
            return gson.toJson(data)
        }.onFailure {
            AppLogs.eLog("gson", it.stackTraceToString())
        }
        return ""
    }

    fun getAvailableStorage(): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val availableBlocks = stat.availableBlocksLong
        return blockSize * availableBlocks
    }
    fun formatSize(bytes: Long): String {
        val mb = bytes / 1024.0 / 1024
        val gb = mb / 1024

        return when {
            gb >= 1 -> String.format("%.1f GB", gb)
            mb >= 1 -> String.format("%.1f MB", mb)
            else -> "0 MB"
        }
    }


    // 解密
    fun decrypt(data: String, code: Int): String {
        val decode = Base64.getDecoder().decode(data)
        val xorList = ByteArray(decode.size)

        for (i in decode.indices) {
            xorList[i] = (decode[i].toInt() xor code).toByte()
        }

        return xorList.toString(Charsets.UTF_8)
    }


}