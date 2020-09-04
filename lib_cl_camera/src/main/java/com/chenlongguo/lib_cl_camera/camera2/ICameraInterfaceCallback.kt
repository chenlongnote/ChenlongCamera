package com.chenlongguo.lib_cl_camera.camera2

interface ICameraInterfaceCallback {
    fun onError(code: Int)
    fun needCameraPermission()
    fun onConfigureFailed()
    fun onVideoRecordStarted()
    fun onVideoRecordStopped()
    fun onFocusStart(x: Int, y: Int)
    fun onFocusFinish(success: Boolean)
    fun onFlashStateUpdate(supported: Boolean)
}