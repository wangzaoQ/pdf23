package com.pdf.editor.common.point

import com.pdf.editor.MyAPP
import com.pdf.editor.common.config.PointConfig
import com.pdf.editor.common.tools.jobCancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

object SessionManager {

    var backJob: Job ?= null

    fun registerBackListener(){
        backJob?.jobCancel()
        backJob = MyAPP.instance.applicationScope.launch(Dispatchers.Main){
            while (true) {
                EventManager.posePoint(PointConfig.session_bac)
                delay((60*60*1000L).milliseconds)
            }
        }
    }

    fun reset(){
        backJob?.jobCancel()
    }
}