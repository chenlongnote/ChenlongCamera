package com.chenlongguo.lib_cl_camera

import java.io.Serializable

class RequestConfig : Serializable {
    var type: String = CaptureUtil.TYPE_ALL
    val duration = 10 * 1000
}