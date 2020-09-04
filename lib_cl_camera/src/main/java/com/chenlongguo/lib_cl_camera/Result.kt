package com.chenlongguo.lib_cl_camera

import android.app.Activity

class Result {
    var resultCode = Activity.RESULT_CANCELED
    var type: Int = CaptureUtil.RESULT_TYPE_UNKNOWN
    var originPath: String? = null
    override fun toString(): String {
        return "CaptureResult(resultCode=$resultCode, type=$type, originPath=$originPath)"
    }
}