package com.chenlongguo.chenlongcamera

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.chenlongguo.lib_cl_camera.Result
import com.chenlongguo.lib_cl_camera.CaptureUtil
import com.chenlongguo.lib_cl_camera.IImageLoader
import com.chenlongguo.lib_cl_camera.OnCaptureResult
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.text).setOnClickListener{
            CaptureUtil.create(this).type(CaptureUtil.TYPE_ALL).duration(3*1000).setOnCaptureResult(object :
                IImageLoader {
                override fun load(context: Context, file: File, imageView: ImageView) {
                    Glide.with(context)
                        .load(file)
                        .skipMemoryCache(true) // 不使用内存缓存
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .into(imageView)
                }

            },object : OnCaptureResult {
                override fun onResult(result: Result) {
                    Log.d("MainActivity", result.toString())
                }
            })
        }
    }
}