package com.chenlongguo.lib_cl_camera

import android.app.Activity
import android.content.Intent
import androidx.fragment.app.Fragment
import java.lang.ref.WeakReference

class Request(activity: Activity?, fragment: Fragment?) {
    private var mActivity: WeakReference<Activity>? = null
    private var mFragment: WeakReference<Fragment>? = null
    private var requestConfig: RequestConfig = RequestConfig()

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

    fun setOnCaptureResult(result: OnCaptureResult) {
        requestConfig.result = result
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