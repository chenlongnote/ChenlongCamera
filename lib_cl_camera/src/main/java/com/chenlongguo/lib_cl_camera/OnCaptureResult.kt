package com.chenlongguo.lib_cl_camera

import java.io.Serializable

interface OnCaptureResult : Serializable {
    fun onResult(result: Result)
}