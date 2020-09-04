package com.chenlongguo.lib_cl_camera.camera2.view

interface CaptureButtonListener {
    fun takePictures()
    fun recordShort(time: Long)
    fun startRecord()
    fun recordEnd(time: Long)
    fun recordDurationMax()
    fun recordZoom(zoom: Float)
    fun recordError()
    fun onStopAnimFinished()
}