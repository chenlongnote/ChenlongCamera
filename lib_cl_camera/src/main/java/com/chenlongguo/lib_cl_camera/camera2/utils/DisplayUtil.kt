package com.chenlongguo.lib_cl_camera.camera2.utils

import android.content.res.Resources
import android.util.TypedValue

class DisplayUtil private constructor() {
    companion object {
        fun dpToPx(dp: Int): Int {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), Resources.getSystem().displayMetrics).toInt()
        }

        fun pxToDp(px: Float): Int {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, px, Resources.getSystem().displayMetrics).toInt()
        }
    }

    init {
        throw UnsupportedOperationException("u can't instantiate me...")
    }
}