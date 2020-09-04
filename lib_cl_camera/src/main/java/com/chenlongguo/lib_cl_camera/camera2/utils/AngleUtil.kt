package com.chenlongguo.lib_cl_camera.camera2.utils

import kotlin.math.abs

class AngleUtil private constructor() {
    companion object {
        /**
         * 当 x=y=0 时，手机处于水平放置状态。
         * 当 x=0 并且 y>0 时，手机顶部的水平位置要大于底部，也就是一般接听电话时手机所处的状态。
         * 当 x=0 并且 y<0 时，手机顶部的水平位置要小于底部。手机一般很少处于这种状态。
         * 当 y=0 并且 x>0 时，手机右侧的水平位置要大于左侧，也就是右侧被抬起。
         * 当 y=0 并且 x<0 时，手机左侧的水平位置要小于左侧，也就是左侧被抬起。
         * 当 z=0 时，手机平面与水平面垂直。
         * 当 z>0 时，手机屏幕朝上。
         * 当 z<0 时，手机屏幕朝下。
         *
         * @param x
         * @param y
         * @param z
         * @return
         */
        fun getSensorAngle(x: Float, y: Float, z: Float): Int {
            return if (abs(x) > abs(y)) {
                /**
                 * 横屏倾斜角度比较大
                 */
                when {
                    x > 4 -> {
                        90
                    }
                    x < -4 -> {
                        270
                    }
                    else -> {
                        0
                    }
                }
            } else {
                //竖屏倾斜角度比较大
                when {
                    y > 7 -> {
                        0
                    }
                    y < -7 -> {
                        180
                    }
                    else -> {
                        0
                    }
                }
            }
        }
    }

    init {
        throw UnsupportedOperationException("u can't instantiate me...")
    }
}