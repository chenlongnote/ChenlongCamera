package com.chenlongguo.lib_cl_camera

import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.chenlongguo.lib_cl_camera.camera2.Camera2BasicFragment
import com.chenlongguo.lib_cl_camera.databinding.ActivityCaptureBinding

class CaptureActivity : AppCompatActivity() {
    lateinit var binding:ActivityCaptureBinding
    lateinit var request:RequestConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE) //这行代码一定要在setContentView之前，不然会闪退
        binding = DataBindingUtil.setContentView(this, R.layout.activity_capture)
        request = intent.getSerializableExtra(CaptureUtil.REQUEST) as RequestConfig
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        supportActionBar?.hide()
        hideNavigationBar()
       val fragment2 =  Camera2BasicFragment.newInstance(request)
        supportFragmentManager.beginTransaction().add(
            R.id.container,
            fragment2
        ).commit()
    }

    private fun hideNavigationBar() {
        if (window != null) {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE)
        }
    }

    fun close(result: Result) {
        request.result?.onResult(result)
        finish()
        overridePendingTransition(0, R.anim.exit_amin);
    }
}