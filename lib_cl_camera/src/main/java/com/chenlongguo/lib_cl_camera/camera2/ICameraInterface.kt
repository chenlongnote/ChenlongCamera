package com.chenlongguo.lib_cl_camera.camera2

import com.chenlongguo.lib_cl_camera.camera2.view.AutoFitTextureView
import java.io.File

interface ICameraInterface {

    /**
     * 设置预览使用的 AutoFitTextureView
     * @param textureView
     */
    fun setTextureView(textureView: AutoFitTextureView?)

    /**
     * 设置回调
     * @param callback
     */
    fun setCameraInterfaceCallback(callback: ICameraInterfaceCallback?)

    /**
     * 设置拍照完成的回调
     * @param imageSaveListener
     */
    fun setImageSaveListener(imageSaveListener: ImageSaveListener?)

    /**
     * 设置显示大小
     * @param displaySizeWidth
     * @param displaySizeHeight
     */
    fun setDisplaySize(displaySizeWidth: Int, displaySizeHeight: Int)

    /**
     * 打开相机设备
     * 开始预览
     */
    fun open()

    /**
     * 关闭相机设备
     */
    fun close()

    /**
     * 获取保存的文件
     * @return
     */
    fun getFile(): File?

    /**
     * 拍照
     */
    fun takePicture()

    /**
     * 开始录像
     */
    fun startRecordingVideo()

    /**
     * 结束录像
     */
    fun stopRecordingVideo()

    /**
     * 设置zoom
     * @param zoom
     */
    fun recordZoom(zoom: Float)

    /**
     * 设置闪光灯状态
     * @param state
     */
    fun setFlashState(state: Int)

    /**
     * 设置当前Activity状态是否onPaused
     * @param paused
     */
    fun setPaused(paused: Boolean)


    /**
     * 前后摄切换
     */
    fun switchCamera()

    /**
     * 设置当前屏幕的旋转角度
     * @param angle
     */
    fun setAngle(angle: Int)

    /**
     * 设置视频最大录制时长
     */
    fun setRecordMaxDuration(duration: Int)
}