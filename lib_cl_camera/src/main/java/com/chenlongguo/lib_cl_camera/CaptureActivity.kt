package com.chenlongguo.lib_cl_camera

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.chenlongguo.lib_cl_camera.camera2.Camera2BasicFragment
import com.chenlongguo.lib_cl_camera.databinding.ActivityCaptureBinding


class CaptureActivity : AppCompatActivity() {
    lateinit var binding: ActivityCaptureBinding
    lateinit var requestConfig: RequestConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE) //这行代码一定要在setContentView之前，不然会闪退
        binding = DataBindingUtil.setContentView(this, R.layout.activity_capture)
        requestConfig = intent.getSerializableExtra(CaptureUtil.REQUEST) as RequestConfig
        if (Build.VERSION.SDK_INT >= 21) {
            val decorView = window.decorView
            val option = (View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            decorView.systemUiVisibility = option
            window.navigationBarColor = Color.TRANSPARENT
            window.statusBarColor = Color.TRANSPARENT
            full(true)
        }
        supportActionBar?.hide()
        val fragment2 = Camera2BasicFragment.newInstance(requestConfig)
        supportFragmentManager.beginTransaction().add(
            R.id.container,
            fragment2
        ).commit()
    }


    private fun full(enable: Boolean) {
        if (enable) {
            val lp = window.attributes
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
            window.attributes = lp
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        } else {
            val attr = window.attributes
            attr.flags = attr.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN.inv()
            window.attributes = attr
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
    }
    fun close(result: Result) {
        Request.result?.onResult(result)
        Request.result = null
        Request.imageLoader = null
        finish()
        overridePendingTransition(0, R.anim.exit_amin);
    }
}