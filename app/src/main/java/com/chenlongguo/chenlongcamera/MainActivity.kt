package com.chenlongguo.chenlongcamera

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.chenlongguo.lib_cl_camera.Result
import com.chenlongguo.lib_cl_camera.CaptureUtil
import com.chenlongguo.lib_cl_camera.OnCaptureResult

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.text).setOnClickListener{
            CaptureUtil.create(this).type(CaptureUtil.TYPE_ALL).duration(3*1000).setOnCaptureResult(object : OnCaptureResult {
                override fun onResult(result: Result) {
                    Log.d("MainActivity", result.toString())
                }
            })
        }
    }
}