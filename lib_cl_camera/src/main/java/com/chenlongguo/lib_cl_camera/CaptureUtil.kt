package com.chenlongguo.lib_cl_camera

import android.app.Activity
import androidx.fragment.app.Fragment

object CaptureUtil {
    const val REQUEST = "request"

    const val RESULT_TYPE_UNKNOWN = 0
    const val RESULT_TYPE_PICTURE = 1
    const val RESULT_TYPE_VIDEO = 2

    // 只拍照
    const val TYPE_IMAGE = "type_image"
    // 点击拍照，长按录像
    const val TYPE_ALL = "type_all"

    fun create(activity: Activity): Request {
        return Request(activity, null)
    }

    fun create(fragment: Fragment): Request {
        return Request(null, fragment)
    }
}