package com.chenlongguo.lib_cl_camera

import android.content.Context
import android.widget.ImageView
import java.io.File

interface IImageLoader {
    fun load(context:Context, file : File, imageView: ImageView)
}