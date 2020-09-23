package com.chenlongguo.lib_cl_camera

import android.app.Activity
import android.content.Intent
import androidx.fragment.app.Fragment
import com.chenlongguo.lib_cl_camera.camera2.utils.Logger
import java.lang.ref.WeakReference

class Request(activity: Activity?, fragment: Fragment?) {
    private var mActivity: WeakReference<Activity>? = null
    private var mFragment: WeakReference<Fragment>? = null
    private var requestConfig = RequestConfig()
    companion object {
        @JvmStatic
        var result: OnCaptureResult? = null
        var imageLoader:IImageLoader? =null
    }


    init {
        activity?.let {
            mActivity = WeakReference(it)
        }

        fragment?.let {
            mFragment = WeakReference(it)
        }
    }

    fun type(type: String):Request {
        requestConfig.type = type
        return this
    }

    fun duration(duration:Int) : Request {
        if (duration > 1000)
            requestConfig.duration = duration
        else
            Logger.w("Request", "duration is too short!")
        return this
    }

    fun setOnCaptureResult(loader:IImageLoader, onCaptureResult: OnCaptureResult) {
        result = onCaptureResult
        imageLoader = loader

        mActivity?.get()?.let { activity ->
            val intent = Intent(activity, CaptureActivity::class.java)
            intent.putExtra(CaptureUtil.REQUEST, requestConfig)
            activity.startActivity(intent)
            activity.overridePendingTransition(R.anim.enter_amin, 0)
        } ?: mFragment?.get()?.let { fragment ->
            val intent = Intent(fragment.context, CaptureActivity::class.java)
            intent.putExtra(CaptureUtil.REQUEST, requestConfig)
            fragment.startActivity(intent)
            fragment.activity?.overridePendingTransition(R.anim.enter_amin, 0)
        }
    }
}