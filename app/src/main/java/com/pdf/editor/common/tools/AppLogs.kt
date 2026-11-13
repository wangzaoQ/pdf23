package com.pdf.editor.common.tools

import android.util.Log
import com.pdf.editor.MyAPP

object AppLogs {
    private val showLog: Boolean = MyAPP.isDebug

    fun dLog(tag: String,msg:String) {
        if (showLog) {
            logContent(tag, msg)
        }
    }

    fun eLog(tag: String,msg:String) {
        if (showLog) {
            Log.d(tag, msg)
        }
    }

    fun logContent(tag: String,content:String){
        if (content.length > 4000) {
            var i = 0
            while (i < content.length) {
                if (i + 4000 < content.length) Log.d(
                    tag,
                    content.substring(i, i + 4000)
                ) else {
                    Log.d(tag, content.substring(i, content.length))
                }
                i += 4000
            }
        } else {
            Log.d(tag, content)
        }
    }


    fun writeDiary(tag:String,msg:String) {
//    if (BuildConfig.DEBUG){
//        val filePath = NewsAPP.singleApp.getExternalFilesDir(null)?.absolutePath
//        val tmpDate = SimpleDateFormat("yyyyMMdd hh:mm:ss.SSSS", Locale.getDefault()).format(Date(System.currentTimeMillis())).toString()
//
//        val text = "$tmpDate $msg\n"
//        try {
//            // 创建文件夹
//            val dir = File("$filePath/app")
//            if (!dir.exists()) {
//                dir.mkdirs()
//            }
//
//            //1,创建文件
//            val file = File(dir, "diary.txt")
//            //2,如果当前的文件不存在
//            if (!file.exists()) {
//                //3,创建文件
//                file.createNewFile()
//            }
//
//            //4,根据file对象获得OutputStream对象
//            val out: OutputStream = FileOutputStream(file, true)
//            //5,写数据
//            out.write(text.toByteArray())
//            //6,关闭流
//            out.close()
//        } catch (e: FileNotFoundException) {
//            e.printStackTrace()
//        } catch (e: IOException) {
//            e.printStackTrace()
//        }
//    }
    }
}
