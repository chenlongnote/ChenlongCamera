package com.chenlongguo.lib_cl_camera.camera2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.params.MeteringRectangle
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.util.SparseArray
import android.util.SparseIntArray
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView.SurfaceTextureListener
import android.view.View.OnTouchListener
import android.view.WindowManager
import androidx.core.content.ContextCompat
import com.chenlongguo.lib_cl_camera.Result
import com.chenlongguo.lib_cl_camera.camera2.flash.FlashState
import com.chenlongguo.lib_cl_camera.camera2.view.AutoFitTextureView
import com.chenlongguo.lib_cl_camera.R
import com.chenlongguo.lib_cl_camera.camera2.utils.Logger
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CameraInterface(private val mContext: Context?) : ICameraInterface {
    companion object {
        private const val TAG = "CameraInterface"

        /**
         * 相机状态: 预览
         */
        private const val STATE_PREVIEW = 0

        /**
         * 相机状态: 等待对焦被锁定
         */
        private const val STATE_WAITING_LOCK = 1

        /**
         * 相机状态: Waiting for the exposure to be precapture state.
         */
        private const val STATE_WAITING_PRECAPTURE = 2

        /**
         * 相机状态: Waiting for the exposure state to be something other than precapture.
         */
        private const val STATE_WAITING_NON_PRECAPTURE = 3

        /**
         * 相机状态: 拍照.
         */
        private const val STATE_PICTURE_TAKEN = 4

        /**
         * 相机状态: 录像.
         */
        private const val STATE_RECORDING = 5

        /**
         * 相机状态: 手动对焦, 正在对焦.
         */
        private const val STATE_MANUAL_FOCUS_PROCESSING = 6

        /**
         * 相机状态: 手动对焦, 完成对焦.
         */
        private const val STATE_MANUAL_FOCUS_FINISH = 7

        /**
         * Max preview width that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_WIDTH = 1920

        /**
         * Max preview height that is guaranteed by Camera2 API
         */
        private const val MAX_PREVIEW_HEIGHT = 1080
        private const val NUM_ZOOM_LEVELS = 13
        private const val MIN_ZOOM = 1f

        /**
         * 错误返回码:
         * CameraDevice.StateCallback#onError
         */
        const val ERROR_CODE_CAMERA_STATE_ERROR = 1

        /**
         * 错误返回码:
         * Currently an NPE is thrown when the Camera2API is used but not supported on the
         * device this code runs.
         */
        const val ERROR_CODE_NPE_ERROR = 2
        private const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        private const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270

        /**
         * Conversion from screen rotation to JPEG orientation.
         */
        private val ORIENTATIONS = SparseIntArray()

        /**
         * 录像时用的
         */
        private val DEFAULT_ORIENTATIONS = SparseIntArray()
        private val INVERSE_ORIENTATIONS = SparseIntArray()

        /**
         * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
         * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
         *
         * @param choices The list of available sizes
         * @return The video size
         */
        private fun chooseVideoSize(choices: Array<Size>): Size {
            Logger.d(TAG, "chooseVideoSize - " + Arrays.asList(*choices))
            val sizes: MutableList<Size> = ArrayList()
            for (size in choices) {
                if (size.width == size.height * 16 / 9 && size.width <= 1080) {
                    Logger.d(TAG, "size: $size")
                    sizes.add(size)
                }
            }
            if (sizes.isNotEmpty()) {
                return Collections.max(sizes, CompareSizesByArea())
            }
            Logger.e(TAG, "Couldn't find any suitable video size")
            return choices[choices.size - 1]
        }

        /**
         * 选择合适的照片尺寸
         *
         * @param choices The list of available sizes
         * @return The video size
         */
        private fun choosePictureSize(choices: Array<Size>, aspectRatio: Size?): Size {
            Logger.d(TAG, "choosePictureSize - " + Arrays.asList(*choices))
            val w = aspectRatio!!.width
            val h = aspectRatio.height
            val sizes: MutableList<Size> = ArrayList()
            for (size in choices) {
                if (size.width == size.height * w / h) {
                    Logger.d(TAG, "size: $size")
                    sizes.add(size)
                }
            }
            if (!sizes.isEmpty()) {
                return Collections.max(sizes, CompareSizesByArea())
            }
            Logger.e(TAG, "Couldn't find any suitable video size")
            return choices[choices.size - 1]
        }

        /**
         * Given `choices` of `Size`s supported by a camera, choose the smallest one that
         * is at least as large as the respective texture view size, and that is at most as large as the
         * respective max size, and whose aspect ratio matches with the specified value. If such size
         * doesn't exist, choose the largest one that is at most as large as the respective max size,
         * and whose aspect ratio matches with the specified value.
         *
         * @param choices           The list of sizes that the camera supports for the intended output
         * class
         * @param textureViewWidth  The width of the texture view relative to sensor coordinate
         * @param textureViewHeight The height of the texture view relative to sensor coordinate
         * @param maxWidth          The maximum width that can be chosen
         * @param maxHeight         The maximum height that can be chosen
         * @param aspectRatio       The aspect ratio
         * @return The optimal `Size`, or an arbitrary one if none were big enough
         */
        private fun chooseOptimalSize(
            choices: Array<Size>, textureViewWidth: Int,
            textureViewHeight: Int, maxWidth: Int, maxHeight: Int, aspectRatio: Size?
        ): Size {
            Logger.d(TAG, "chooseOptimalSize - " + Arrays.asList(*choices))
            // Collect the supported resolutions that are at least as big as the preview Surface
            val bigEnough: MutableList<Size> = ArrayList()
            // Collect the supported resolutions that are smaller than the preview Surface
            val notBigEnough: MutableList<Size> = ArrayList()
            val w = aspectRatio!!.width
            val h = aspectRatio.height
            for (option in choices) {
                if (option.width <= maxWidth && option.height <= maxHeight && option.height == option.width * h / w) {
                    if (option.width >= textureViewWidth &&
                        option.height >= textureViewHeight
                    ) {
                        bigEnough.add(option)
                    } else {
                        notBigEnough.add(option)
                    }
                }
            }

            // Pick the smallest of those big enough. If there is no one big enough, pick the
            // largest of those not big enough.
            return if (bigEnough.size > 0) {
                Collections.min(bigEnough, CompareSizesByArea())
            } else if (notBigEnough.size > 0) {
                Collections.max(notBigEnough, CompareSizesByArea())
            } else {
                Logger.e(TAG, "Couldn't find any suitable preview size")
                choices[0]
            }
        }

        init {
            ORIENTATIONS.append(0, 90)
            ORIENTATIONS.append(90, 0)
            ORIENTATIONS.append(180, 270)
            ORIENTATIONS.append(270, 180)
        }

        init {
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270)
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }

        init {
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90)
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0)
        }

        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val VIDEO_EXTENSION = ".mp4"

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(
                baseFolder, SimpleDateFormat(format, Locale.US)
                    .format(System.currentTimeMillis()) + extension
            )

        fun getOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() }
            }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else appContext.filesDir
        }
    }

    /**
     * [TextureView.SurfaceTextureListener] handles several lifecycle events on a
     * [TextureView].
     */
    private val mSurfaceTextureListener: SurfaceTextureListener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            Logger.d(TAG, "onSurfaceTextureAvailable-isPaused: $isPaused")
            if (!isPaused) {
                openCamera(width, height)
            }
        }

        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
            Logger.d(TAG, "onSurfaceTextureSizeChanged")
            configureTransform(width, height)
        }

        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            Logger.d(TAG, "onSurfaceTextureDestroyed")
            return true
        }

        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {}
    }

    /**
     * 当前Facing
     * CameraCharacteristics.LENS_FACING_BACK
     * CameraCharacteristics.LENS_FACING_FRONT
     */
    private var mFacing = CameraCharacteristics.LENS_FACING_BACK

    /**
     * 前后摄像头ID
     */
    private val mFacingIdInfo = SparseArray<Any?>()

    /**
     * An [AutoFitTextureView] for camera preview.
     */
    private var mTextureView: AutoFitTextureView? = null

    /**
     * A [CameraCaptureSession] for camera preview.
     */
    private var mPreviewSession: CameraCaptureSession? = null

    /**
     * A reference to the opened [CameraDevice].
     */
    private var mCameraDevice: CameraDevice? = null

    /**
     * Current display rotation in degrees.
     */
    private val mDisplayRotation: Int

    /**
     * The [Size] of camera preview.
     */
    private var mPreviewSize: Size? = null
    private var mDisplaySizeWidth = 0
    private var mDisplaySizeHeight = 0

    /**
     * 提供给外界的状态回调
     */
    private var mCallback: ICameraInterfaceCallback? = null

    /**
     * 图标保持的状态回调
     */
    private var mImageSaveListener: ImageSaveListener? = null
    private val mSettings3A: Settings3A
    private var mActiveArraySize: Rect? = null
    private var mCropRegion: Rect? = null
    private var mFlashState = FlashState.AUTO

    /**
     * 表示是否支持自动对焦
     */
    private var isSupportAF = false
    private var mAEMode = CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
    private var mCharacteristics: CameraCharacteristics? = null

    /**
     * Maximum zoom; intialize to 1.0 (disabled)
     */
    private var mMaxZoom = MIN_ZOOM

    /**
     * Current zoom level ranging between 1 and NUM_ZOOM_LEVELS. Each level is
     * associated with a discrete zoom value.
     */
    private var mCurrA11yZoomLevel = 1

    /**
     * 第一次获取距离时的Zoom level
     */
    private var mA11yZoomLevelFirst = mCurrA11yZoomLevel

    /**
     * 真机测试时,发现Bug,在进入锁屏后会调用onSurfaceTextureDestroyed和onSurfaceTextureAvailable
     * 导致onResume时打开camera失败
     * 这里设置一个标志位
     * 在onPause之后，不在onSurfaceTextureAvailable中进行camera初始化
     * 需要外部设置
     */
    private var isPaused = false

    /**
     * 用来判断当前是不是第一次拿到距离
     */
    private var isFirstDistance = false

    /**
     * 用来保存第一次拿到的距离
     */
    private var mDistanceFirst = 0f

    /**
     * 距离的步长
     * 当移动超过这个距离则会出发变焦
     */
    private var mDistanceStepLength = 0

    /**
     * MediaRecorder
     */
    private var mMediaRecorder: MediaRecorder? = null

    private var recordMaxDuration:Int = 10 * 1000

    /**
     * The [Size] of video recording.
     */
    private var mVideoSize: Size? = null
    private var mPictureSize: Size? = null
    override fun setImageSaveListener(imageSaveListener: ImageSaveListener?) {
        mImageSaveListener = imageSaveListener
    }

    override fun setCameraInterfaceCallback(callback: ICameraInterfaceCallback?) {
        mCallback = callback
    }

    override fun setDisplaySize(displaySizeWidth: Int, displaySizeHeight: Int) {
        mDisplaySizeWidth = displaySizeWidth
        mDisplaySizeHeight = displaySizeHeight
        mDistanceStepLength =
            if (mDisplaySizeWidth < mDisplaySizeHeight) mDisplaySizeWidth else mDisplaySizeHeight
        mDistanceStepLength /= 15
    }

    override fun setTextureView(textureView: AutoFitTextureView?) {
        mTextureView = textureView
        mTextureView?.setOnTouchListener(mTextureTouchListener)
    }

    override fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    override fun getFile(): File? {
        return mFile
    }

    private val isFrontCamera: Boolean
        private get() = mFacing == CameraCharacteristics.LENS_FACING_FRONT

    /**
     * [CameraDevice.StateCallback] is called when [CameraDevice] changes its state.
     */
    private val mStateCallback: CameraDevice.StateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cameraDevice: CameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            Logger.d(TAG, "Camera state - onOpened")
            mCameraOpenCloseLock.release()
            mCameraDevice = cameraDevice
            createCameraPreviewSession()
        }

        override fun onDisconnected(cameraDevice: CameraDevice) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            Logger.d(TAG, "Camera state - onDisconnected")
        }

        override fun onError(cameraDevice: CameraDevice, error: Int) {
            mCameraOpenCloseLock.release()
            cameraDevice.close()
            mCameraDevice = null
            if (mCallback != null) {
                mCallback!!.onError(ERROR_CODE_CAMERA_STATE_ERROR)
            }
            Logger.d(TAG, "Camera state - onError")
        }
    }

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private var mBackgroundThread: HandlerThread? = null

    /**
     * A [Handler] for running tasks in the background.
     */
    private var mBackgroundHandler: Handler? = null

    /**
     * An [ImageReader] that handles still image capture.
     */
    private var mImageReader: ImageReader? = null

    /**
     * This is the output file for our picture.
     */
    private var mFile: File? = null

    /**
     * This a callback object for the [ImageReader]. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */
    private val mOnImageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        Logger.d(TAG, "onImageAvailable-File: " + mFile!!.absolutePath)
        mBackgroundHandler!!.post(
            ImageSaver(
                reader.acquireNextImage(),
                mFile!!,
                isFrontCamera,
                mImageSaveListener!!
            )
        )
    }

    /**
     * [CaptureRequest.Builder] for the camera preview
     */
    private var mRequestBuilder: CaptureRequest.Builder? = null
    private var mPreviewRequestBuilder: CaptureRequest.Builder? = null

    /**
     * [CaptureRequest] generated by [.mPreviewRequestBuilder]
     */
    private var mPreviewRequest: CaptureRequest? = null

    /**
     * The current state of camera state for taking pictures.
     *
     * @see .mCaptureCallback
     */
    private var mState = STATE_PREVIEW

    /**
     * A [Semaphore] to prevent the app from exiting before closing the camera.
     */
    private val mCameraOpenCloseLock = Semaphore(1)

    /**
     * Whether the current camera device supports Flash or not.
     */
    private var mFlashSupported = false

    /**
     * Orientation of the camera sensor
     */
    private var mSensorOrientation = 0
    private var mAngle = 0

    /**
     * A [CameraCaptureSession.CaptureCallback] that handles events related to JPEG capture.
     */
    private val mCaptureCallback: CaptureCallback = object : CaptureCallback() {
        private fun process(result: CaptureResult) {
            when (mState) {
                STATE_PREVIEW -> {
                }
                STATE_WAITING_LOCK -> {
                    val afState = result.get(CaptureResult.CONTROL_AF_STATE)
                    Logger.d(TAG, "afState = $afState")
                    if (afState == null
                        /**
                         * 不支持AF
                         * 直接拍摄
                         */
                        /**
                         * 不支持AF
                         * 直接拍摄
                         */
                        || !isSupportAF
                        /**
                         * 当设置mCropRegion后会一直拿到CONTROL_AF_STATE_INACTIVE
                         * 目前没有找到原因
                         * 先直接进行拍摄
                         */
                        /**
                         * 当设置mCropRegion后会一直拿到CONTROL_AF_STATE_INACTIVE
                         * 目前没有找到原因
                         * 先直接进行拍摄
                         */
                        || mCropRegion != null
                    ) {
                        Logger.d(TAG, "STATE_WAITING_LOCK-1")
                        mState = STATE_PICTURE_TAKEN
                        captureStillPicture()
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState
                    ) {
                        // CONTROL_AE_STATE can be null on some devices
                        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                        Logger.d(TAG, "aeState = $aeState")
                        if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED
                        ) {
                            mState = STATE_PICTURE_TAKEN
                            Logger.d(TAG, "STATE_WAITING_LOCK-2")
                            captureStillPicture()
                        } else {
                            runPrecaptureSequence()
                        }
                    }
                }
                STATE_WAITING_PRECAPTURE -> {

                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE || aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE
                    }
                }
                STATE_WAITING_NON_PRECAPTURE -> {

                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        Logger.d(TAG, "STATE_WAITING_NON_PRECAPTURE")
                        mState = STATE_PICTURE_TAKEN
                        captureStillPicture()
                    }
                }
            }
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            Logger.d(TAG, "mCaptureCallback - onCaptureProgressed")
            process(partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
//            Logger.d(TAG, "mCaptureCallback - onCaptureCompleted");
            process(result)
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private fun setUpCameraOutputs(width: Int, height: Int) {
        Logger.d(TAG, "setUpCameraOutputs, width=$width, height=$height")
        try {
            val map = mCharacteristics!!.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP
            )
            if (map == null) {
                Logger.w(TAG, "StreamConfigurationMap is NULL")
                return
            }
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder::class.java))
            Logger.d(TAG, "mVideoSize = $mVideoSize")
            mPictureSize = choosePictureSize(map.getOutputSizes(ImageFormat.JPEG), mVideoSize)
            Logger.d(TAG, "mPictureSize = $mPictureSize")
            mImageReader = ImageReader.newInstance(
                mPictureSize!!.width, mPictureSize!!.height,
                ImageFormat.JPEG,  /*maxImages*/2
            )
            mImageReader?.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler)

            mSensorOrientation = mCharacteristics!!.get(CameraCharacteristics.SENSOR_ORIENTATION)!!
            Logger.d(TAG, "SENSOR_ORIENTATION = $mSensorOrientation")
            var swappedDimensions = false
            when (mDisplayRotation) {
                Surface.ROTATION_0, Surface.ROTATION_180 -> if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                    swappedDimensions = true
                }
                Surface.ROTATION_90, Surface.ROTATION_270 -> if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                    swappedDimensions = true
                }
                else -> Logger.e(TAG, "Display rotation is invalid: $mDisplayRotation")
            }
            Logger.d(TAG, "swappedDimensions: $swappedDimensions")
            var rotatedPreviewWidth = width
            var rotatedPreviewHeight = height
            val maxPreviewWidth = MAX_PREVIEW_WIDTH
            val maxPreviewHeight = MAX_PREVIEW_HEIGHT
            //            int maxPreviewWidth = mDisplaySizeWidth;
//            int maxPreviewHeight = mDisplaySizeHeight;
            if (swappedDimensions) {
                rotatedPreviewWidth = height
                rotatedPreviewHeight = width
                //                maxPreviewWidth = mDisplaySizeHeight;
//                maxPreviewHeight = mDisplaySizeWidth;
            }
            Logger.d(TAG, "maxPreviewWidth: $maxPreviewWidth, maxPreviewHeight: $maxPreviewHeight")

//            if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
//                maxPreviewWidth = MAX_PREVIEW_WIDTH;
//            }
//
//            if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
//                maxPreviewHeight = MAX_PREVIEW_HEIGHT;
//            }


            // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
            // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
            // garbage capture data.
            mPreviewSize = chooseOptimalSize(
                map.getOutputSizes(SurfaceTexture::class.java),
                rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                maxPreviewHeight, mVideoSize
            )
            Logger.d(TAG, "mPreviewSize - " + mPreviewSize.toString())

            // We fit the aspect ratio of TextureView to the size of preview we picked.
            val orientation = mContext!!.resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Logger.d(TAG, "ORIENTATION_LANDSCAPE")
                mTextureView!!.setAspectRatio(
                    mPreviewSize!!.width, mPreviewSize!!.height
                )
            } else {
                Logger.d(TAG, "ORIENTATION_PORTRAIT")
                mTextureView!!.setAspectRatio(
                    mPreviewSize!!.height, mPreviewSize!!.width
                )
            }
            return
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            mCallback!!.onError(ERROR_CODE_NPE_ERROR)
        }
    }

    /**
     * onResume中调用
     */
    override fun open() {
        startBackgroundThread()

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView!!.isAvailable) {
            openCamera(mTextureView!!.width, mTextureView!!.height)
        } else {
            mTextureView!!.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    /**
     * Opens the camera specified by [CameraInterface.mFacing].
     */
    private fun openCamera(width: Int, height: Int) {
        Logger.d(TAG, "openCamera, width=$width, height=$height")
        if (ContextCompat.checkSelfPermission(mContext!!, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            mCallback!!.needCameraPermission()
            return
        }
        val manager = mContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            var characteristics: CameraCharacteristics? = null
            if (mFacingIdInfo.size() == 0) {
                //没有初始化，进行初始化
                Logger.d(TAG, "mCameraId IS NULL ")
                for (cameraId in manager.cameraIdList) {
                    characteristics = manager.getCameraCharacteristics(cameraId)
                    // We don't use a front facing camera in this sample.
                    val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                    /**
                     * 不同的cameraId可能查到同样的facing，这里默认使用第一个cameraId
                     * 还需要找到实际能用的。才行，目前没有做
                     */
                    if (mFacingIdInfo[facing!!] == null) {
                        mFacingIdInfo.append(facing, cameraId)
                    }
                    Logger.d(TAG, "$facing APPEND cameraId:$cameraId")
                    if (facing == mFacing) {
                        mCharacteristics = characteristics
                    }
                }
            } else {
                //已经初始化了，直接获取属性
                mCharacteristics =
                    manager.getCameraCharacteristics((mFacingIdInfo[mFacing] as String))
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        mActiveArraySize =
            mCharacteristics!!.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        Logger.d(TAG, "mActiveArraySize: $mActiveArraySize")
        mMaxZoom = mCharacteristics!!.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)!!
        Logger.d(TAG, "mMaxZoom: $mMaxZoom")
        val afAvailableModes =
            mCharacteristics!!.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        isSupportAF = afAvailableModes!!.size > 1
        Logger.d(TAG, "isSupportAF: $isSupportAF")
        // Check if the flash is supported.
        val available = mCharacteristics!!.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
        mFlashSupported = available ?: false
        Logger.d(TAG, "mFlashSupported=$mFlashSupported")
        mCallback?.onFlashStateUpdate(mFlashSupported)
        setUpCameraOutputs(width, height)
        configureTransform(width, height)
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            mMediaRecorder = MediaRecorder()
            Logger.d(TAG, "OPEN ${mFacingIdInfo[mFacing]}")
            manager.openCamera(
                (mFacingIdInfo[mFacing] as String),
                mStateCallback,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    /**
     * 在onPause中调用
     */
    override fun close() {
        closeCamera()
        stopBackgroundThread()
    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        Logger.d(TAG, "closeCamera")
        try {
            mCameraOpenCloseLock.acquire()
            mPreviewSession?.close()
            mPreviewSession = null
            mCameraDevice?.close()
            mCameraDevice = null
            mImageReader?.close()
            mImageReader = null
            mMediaRecorder?.release()
            mMediaRecorder = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            mCameraOpenCloseLock.release()
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startBackgroundThread() {
        mBackgroundThread = HandlerThread("CameraBackground")
        mBackgroundThread!!.start()
        mBackgroundHandler = Handler(mBackgroundThread!!.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopBackgroundThread() {
        if (mBackgroundThread != null) {
            mBackgroundThread!!.quitSafely()
            try {
                mBackgroundThread!!.join()
                mBackgroundThread = null
                mBackgroundHandler = null
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession() {
        try {
            val texture = mTextureView!!.surfaceTexture!!

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)

            // This is the output Surface we need to start preview.
            val surface = Surface(texture)

            // We set up a CaptureRequest.Builder with the output Surface.
            mRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mRequestBuilder!!.addTarget(surface)

            // A: for preview builder (S)
            mPreviewRequestBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            mPreviewRequestBuilder!!.addTarget(surface)
            // A: for preview builder (E)

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice!!.createCaptureSession(
                Arrays.asList(surface, mImageReader!!.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (null == mCameraDevice) {
                            return
                        }

                        // When the session is ready, we start displaying the preview.
                        mPreviewSession = cameraCaptureSession
                        try {
                            // Auto focus should be continuous for camera preview.
                            mRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            // Flash is automatically enabled when necessary.
                            setFlashModeForBuilder(mRequestBuilder, mAEMode)

                            // A: for preview builder (S)
                            mPreviewRequestBuilder!!.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            setFlashModeForBuilder(mPreviewRequestBuilder, mAEMode)
                            // A: for preview builder (E)

                            // Finally, we start displaying the camera preview.
                            mPreviewRequest = mPreviewRequestBuilder!!.build()
                            mPreviewSession!!.setRepeatingRequest(
                                mPreviewRequest!!,
                                mCaptureCallback, mBackgroundHandler
                            )
                        } catch (e: CameraAccessException) {
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(
                        cameraCaptureSession: CameraCaptureSession
                    ) {
                        mCallback!!.onConfigureFailed()
                    }
                }, null
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Configures the necessary [Matrix] transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private fun configureTransform(viewWidth: Int, viewHeight: Int) {
        if (null == mTextureView || null == mPreviewSize || null == mContext) {
            return
        }
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewWidth.toFloat(), viewHeight.toFloat())
        val bufferRect =
            RectF(0f, 0f, mPreviewSize!!.height.toFloat(), mPreviewSize!!.width.toFloat())
        val centerX = viewRect.centerX()
        val centerY = viewRect.centerY()
        if (Surface.ROTATION_90 == mDisplayRotation || Surface.ROTATION_270 == mDisplayRotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL)
            val scale = Math.max(
                viewHeight.toFloat() / mPreviewSize!!.height,
                viewWidth.toFloat() / mPreviewSize!!.width
            )
            matrix.postScale(scale, scale, centerX, centerY)
            matrix.postRotate(90 * (mDisplayRotation - 2).toFloat(), centerX, centerY)
        } else if (Surface.ROTATION_180 == mDisplayRotation) {
            matrix.postRotate(180f, centerX, centerY)
        }
        mTextureView!!.setTransform(matrix)
    }

    /**
     * Initiate a still image capture.
     */
    override fun takePicture() {
        mFile = createFile(getOutputDirectory(mContext!!), FILENAME, PHOTO_EXTENSION)
        lockFocus()
    }

    /**
     * Lock the focus as the first step for a still image capture.
     */
    private fun lockFocus() {
        if (mState != STATE_PREVIEW) {
            Logger.d(TAG, "not STATE_PREVIEW, current state is $mState")
            return
        }
        try {
            mRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            setFlashModeForBuilder(mRequestBuilder, mAEMode)
            // This is how to tell the camera to lock focus.
            mRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START
            )
            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK
            mPreviewSession!!.capture(
                mRequestBuilder!!.build(), mCaptureCallback,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in [.mCaptureCallback] from [.lockFocus].
     */
    private fun runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mRequestBuilder!!.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE
            mPreviewSession!!.capture(
                mRequestBuilder!!.build(), mCaptureCallback,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * [.mCaptureCallback] from both [.lockFocus].
     */
    private fun captureStillPicture() {
        Logger.d(TAG, "captureStillPicture")
        try {
            if (null == mContext || null == mCameraDevice) {
                return
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            val captureBuilder =
                mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureBuilder.addTarget(mImageReader!!.surface)

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            setFlashModeForBuilder(captureBuilder, mAEMode)

            // Orientation
//            int captureOrientation = getImageRotation(mSensorOrientation, mDisplayRotation, isFrontCamera());
            val captureOrientation = getOrientation(mAngle, isFrontCamera)
            Logger.d(
                TAG,
                "mSensorOrientation: $mSensorOrientation, captureOrientation: $captureOrientation"
            )
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, captureOrientation)
            if (mCropRegion != null) {
                captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, mCropRegion)
            }
            val CaptureCallback: CaptureCallback = object : CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    unlockFocus()
                }
            }
            mPreviewSession!!.stopRepeating()
            mPreviewSession!!.abortCaptures()
            mPreviewSession!!.capture(captureBuilder.build(), CaptureCallback, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    private fun getOrientation(rotation: Int, isFront: Boolean): Int {
        return if (isFront) {
            ((360 - ORIENTATIONS[rotation]) % 360 + mSensorOrientation + 90) % 360
        } else {
            // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
            // We have to take that into account and rotate JPEG properly.
            // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
            // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
            (ORIENTATIONS[rotation] + mSensorOrientation + 270) % 360
        }
    }

    /**
     * Given the camera sensor orientation and device orientation, this returns a clockwise angle
     * which the final image needs to be rotated to be upright on the device screen.
     *
     * @param sensorOrientation Clockwise angle through which the output image needs to be rotated
     * to be upright on the device screen in its native orientation.
     * @param deviceOrientation Clockwise angle of the device orientation from its
     * native orientation when front camera faces user.
     * @param isFrontCamera     True if the camera is front-facing.
     * @return The angle to rotate image clockwise in degrees. It should be 0, 90, 180, or 270.
     */
    private fun getImageRotation(
        sensorOrientation: Int,
        deviceOrientation: Int,
        isFrontCamera: Boolean
    ): Int {
        var deviceOrientation = deviceOrientation
        Logger.d(
            TAG, "getImageRotation - sensorOrientation: " + sensorOrientation
                    + ", deviceOrientation: " + deviceOrientation
                    + ", isFrontCamera: " + isFrontCamera
        )

        // The sensor of front camera faces in the opposite direction from back camera.
        if (isFrontCamera) {
            deviceOrientation = (360 - deviceOrientation) % 360
        }
        return (sensorOrientation + deviceOrientation) % 360
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */
    private fun unlockFocus() {
        try {
            // Reset the auto-focus trigger
            mRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
            )
            setFlashModeForBuilder(mRequestBuilder, mAEMode)
            mPreviewSession!!.capture(
                mRequestBuilder!!.build(), mCaptureCallback,
                mBackgroundHandler
            )
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW
            mPreviewSession!!.setRepeatingRequest(
                mPreviewRequest!!, mCaptureCallback,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    /**
     * Compares two `Size`s based on their areas.
     */
    internal class CompareSizesByArea : Comparator<Size> {
        override fun compare(lhs: Size, rhs: Size): Int {
            // We cast here to ensure the multiplications won't overflow
            return java.lang.Long.signum(
                lhs.width.toLong() * lhs.height -
                        rhs.width.toLong() * rhs.height
            )
        }
    }

    override fun setFlashState(state: Int) {
        mFlashState = state
        if (!mFlashSupported) {
            Logger.i(TAG, "NOT support flash")
            return
        }
        // 构建Builder
        setRequestBuilderByFlashState(state)

        // 重新开始预览
        try {
            mPreviewSession!!.setRepeatingRequest(
                mPreviewRequest!!,
                mCaptureCallback, mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun setAEModeByFlashState(state: Int) {
        when (state) {
            FlashState.ON -> {
                mAEMode = CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH
            }
            FlashState.OFF -> {
                mAEMode = CaptureRequest.CONTROL_AE_MODE_OFF
            }
            FlashState.AUTO -> {
                mAEMode = CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
            }
            FlashState.ALWAYS_ON -> {
                mAEMode = CaptureRequest.CONTROL_AE_MODE_ON
            }
        }
    }

    private fun setRequestBuilderByFlashState(flashState: Int) {
        setAEModeByFlashState(flashState)
        if (mState == STATE_PREVIEW
            || mState == STATE_RECORDING
        ) {
            setFlashModeForBuilder(mRequestBuilder, mAEMode)
            mRequestBuilder!!.set(
                CaptureRequest.FLASH_MODE,
                CaptureRequest.FLASH_MODE_TORCH
            )
            // A: for preview builder (S)
            setFlashModeForBuilder(mPreviewRequestBuilder, mAEMode)
            mPreviewRequestBuilder!!.set(
                CaptureRequest.FLASH_MODE,
                CaptureRequest.FLASH_MODE_TORCH
            )
            // A: for preview builder (E)
            mPreviewRequest = mPreviewRequestBuilder!!.build()
        }
    }

    private fun setFlashModeForBuilder(builder: CaptureRequest.Builder?, mode: Int) {
        if (!mFlashSupported) {
            return
        }
        builder!!.set(
            CaptureRequest.CONTROL_AE_MODE,
            mode
        )
    }

    override fun switchCamera() {
        if (mState != STATE_PREVIEW) {
            Logger.w(TAG, "非PREVIEW状态,不能切换前后摄像头  mState=$mState")
            return
        }

        // 释放资源
        closeCamera()
        stopBackgroundThread()
        mTextureView!!.setAspectRatio(mDisplaySizeWidth, mDisplaySizeHeight)

        // 切换ID
        mFacing = if (mFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }

        // 重开Camera
        startBackgroundThread()
        if (mTextureView!!.isAvailable) {
            openCamera(mTextureView!!.width, mTextureView!!.height)
        } else {
            mTextureView!!.surfaceTextureListener = mSurfaceTextureListener
        }
    }

    override fun setAngle(angle: Int) {
        mAngle = angle
    }

    override fun setRecordMaxDuration(duration: Int) {
        recordMaxDuration = duration
    }

    private fun getDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return Math.sqrt(
            Math.pow(x1 - x2.toDouble(), 2.0) + Math.pow(
                y1 -
                        y2.toDouble(), 2.0
            )
        ).toFloat()
    }

    private var mTextureTouchListener = OnTouchListener { _, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isFirstDistance = true
                if (event.pointerCount == 1) {
                    //一根手指对焦
                    Logger.d(TAG, "onTouch: x=" + event.x + ", y=" + event.y)
                    startActiveFocusAt(event.x, event.y)
                }
                if (event.pointerCount == 2) {
                    //两根手指
                    Logger.d(TAG, "onTouchDown 222222")
                }
            }
            MotionEvent.ACTION_MOVE ->
                if (event.pointerCount == 2) {
                    //两根手指
                    //第一个点
                    val point1X = event.getX(0)
                    val point1Y = event.getY(0)
                    //第二个点
                    val point2X = event.getX(1)
                    val point2Y = event.getY(1)
                    val currentDistance = getDistance(point1X, point1Y, point2X, point2Y)
                    if (isFirstDistance) {
                        isFirstDistance = false
                        mDistanceFirst = currentDistance
                        mA11yZoomLevelFirst = mCurrA11yZoomLevel
                        Logger.d(TAG, "mDistanceFirst=$mDistanceFirst")
                        Logger.d(TAG, "mA11yZoomLevelFirst=$mA11yZoomLevelFirst")
                    } else {
                        val delta = currentDistance - mDistanceFirst
                        tryZoom(delta)
                    }
                }
            MotionEvent.ACTION_UP -> {
            }
        }
        true
    }
    private var afOk = false
    private var aeOk = false
    private var mFocusCaptureCallback: CaptureCallback = object : CaptureCallback() {
        private fun process(result: CaptureResult) {
            if (mState != STATE_MANUAL_FOCUS_PROCESSING) {
                Logger.w(TAG, "mFocusCaptureCallback STATE ERROR mState = $mState")
                return
            }
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            if (afState == null
                /**
                 * 支持
                 * 去预览
                 */
                || !isSupportAF
            ) {
                afOk = true
            } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState
            ) {
                afOk = true
            }
            val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
            if (aeState == null
                /**
                 * 支持
                 * 去预览
                 */
                || !mFlashSupported || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED
            ) {
                aeOk = true
            } else if (aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED
            ) {
                Logger.d(TAG, "1 -- aeState = $aeState")
                aeOk = true
            }

            if (afOk && aeOk) {
                goToPreviewForFocus()
            }
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            super.onCaptureProgressed(session, request, partialResult)
            Logger.d(TAG, "startFocus onCaptureProgressed")
            process(partialResult)
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            Logger.d(TAG, "startFocus Completed")
            process(result)
        }
    }

    // 手动对焦开始
    private fun startActiveFocusAt(viewX: Float, viewY: Float) {
        if (mState != STATE_PREVIEW) {
            Logger.w(TAG, "startActiveFocusAt, state not STATE_PREVIEW, mState=$mState")
            return
        }
        mState = STATE_MANUAL_FOCUS_PROCESSING
        mCallback?.onFocusStart(viewX.toInt(), viewY.toInt())
        val points = FloatArray(2)
        points[0] = (viewX - (mDisplaySizeWidth - mTextureView!!.width)) / mTextureView!!.width
        points[1] = (viewY - (mDisplaySizeHeight - mTextureView!!.height)) / mTextureView!!.height

        // Rotate coordinates to portrait orientation per CameraOne API.
        val rotationMatrix = Matrix()
        rotationMatrix.setRotate(mDisplayRotation.toFloat(), 0.5f, 0.5f)
        rotationMatrix.mapPoints(points)

        // Invert X coordinate on front camera since the display is mirrored.
        if (mFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            points[0] = 1 - points[0]
        }
        var cropRegion = mPreviewRequest!!.get(CaptureRequest.SCALER_CROP_REGION)
        Logger.d(TAG, "startActiveFocusAt - $viewX, $viewY, cropRegion=$cropRegion")
        if (cropRegion == null) {
            cropRegion = mActiveArraySize
            Logger.d(TAG, "use activeArraySize - $mActiveArraySize")
        }
        afOk = false
        aeOk = false
        startControlAFRequest(
            regionForNormalizedCoord(PointF(points[0], points[1]), cropRegion),
            mFocusCaptureCallback
        )
    }

    private fun goToPreviewForFocus() {
        Logger.d(TAG, "goToPreviewForFocus")
        mState = STATE_MANUAL_FOCUS_FINISH
        mCallback!!.onFocusFinish(true)
        try {
            // Reset the auto-focus trigger
            mRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_CANCEL
            )
            mPreviewSession!!.capture(
                mRequestBuilder!!.build(), mCaptureCallback,
                mBackgroundHandler
            )
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW
            mRequestBuilder!!.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_IDLE
            )
            mPreviewSession!!.setRepeatingRequest(
                mPreviewRequest!!, mCaptureCallback,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun regionForNormalizedCoord(
        point: PointF,
        cropRegion: Rect?
    ): MeteringRectangle {
        Logger.d(TAG, "============ START ============== ")
        Logger.d(TAG, "point = $point")
        Logger.d(TAG, "cropRegion = $cropRegion")
        // Compute half side length in pixels.
        val minCropEdge = Math.min(cropRegion!!.width(), cropRegion.height())
        Logger.d(TAG, "minCropEdge = $minCropEdge")
        val halfSideLength = (0.5f * mSettings3A.getMeteringRegionFraction() * minCropEdge).toInt()
        Logger.d(TAG, "halfSideLength = $halfSideLength")

        // Compute the output MeteringRectangle in sensor space.
        // point is normalized to the screen.
        // Crop region itself is specified in sensor coordinates (see
        // CameraCharacteristics#SENSOR_INFO_ACTIVE_ARRAY_SIZE).

        // Normalized coordinates, now rotated into sensor space.
        val nsc = transformPortraitCoordinatesToSensorCoordinates(point)
        Logger.d(TAG, "nsc = $nsc")
        val xCenterSensor = (cropRegion.left + nsc.x * cropRegion.width()).toInt()
        Logger.d(TAG, "xCenterSensor = $xCenterSensor")
        val yCenterSensor = (cropRegion.top + nsc.y * cropRegion.height()).toInt()
        Logger.d(TAG, "yCenterSensor = $yCenterSensor")
        val meteringRegion = Rect(
            xCenterSensor - halfSideLength,
            yCenterSensor - halfSideLength,
            xCenterSensor + halfSideLength,
            yCenterSensor + halfSideLength
        )
        Logger.d(TAG, "meteringRegion = $meteringRegion")
        // Clamp meteringRegion to cropRegion.
        meteringRegion.left = clamp(
            meteringRegion.left, cropRegion.left,
            cropRegion.right
        )
        Logger.d(
            TAG,
            meteringRegion.left.toString() + ": " + cropRegion.left + " : " + cropRegion.right
        )
        meteringRegion.top = clamp(meteringRegion.top, cropRegion.top, cropRegion.bottom)
        Logger.d(
            TAG,
            meteringRegion.top.toString() + ": " + cropRegion.top + " : " + cropRegion.bottom
        )
        meteringRegion.right = clamp(
            meteringRegion.right, cropRegion.left,
            cropRegion.right
        )
        Logger.d(
            TAG,
            meteringRegion.right.toString() + ": " + cropRegion.left + " : " + cropRegion.right
        )
        meteringRegion.bottom = clamp(
            meteringRegion.bottom, cropRegion.top,
            cropRegion.bottom
        )
        Logger.d(
            TAG,
            meteringRegion.bottom.toString() + ": " + cropRegion.top + " : " + cropRegion.bottom
        )
        Logger.d(TAG, "clamp meteringRegion = $meteringRegion")
        Logger.d(TAG, "============ NED ============== ")
        return MeteringRectangle(meteringRegion, mSettings3A.getMeteringWeight())
    }

    /**
     * Given (nx, ny) \in [0, 1]^2, in the display's portrait coordinate system,
     * returns normalized sensor coordinates \in [0, 1]^2 depending on how the
     * sensor's orientation \in {0, 90, 180, 270}.
     */
    private fun transformPortraitCoordinatesToSensorCoordinates(
        point: PointF
    ): PointF {
        return when (mSensorOrientation) {
            0 -> point
            90 -> PointF(point.y, 1.0f - point.x)
            180 -> PointF(1.0f - point.x, 1.0f - point.y)
            270 -> PointF(1.0f - point.y, point.x)
            else -> throw IllegalArgumentException("Unsupported Sensor Orientation")
        }
    }

    private fun clamp(value: Int, min: Int, max: Int): Int {
        return value.coerceAtLeast(min).coerceAtMost(max)
    }

    private fun startControlAFRequest(
        rect: MeteringRectangle,
        captureCallback: CaptureCallback
    ) {
        Logger.d(TAG, "startControlAFRequest - $rect")
        val rectangle = arrayOf(rect)
        // 对焦模式必须设置为AUTO
        mRequestBuilder!!.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        //AE
        mRequestBuilder!!.set(CaptureRequest.CONTROL_AE_REGIONS, rectangle)
        //AF 此处AF和AE用的同一个rect, 实际AE矩形面积比AF稍大, 这样测光效果更好
        mRequestBuilder!!.set(CaptureRequest.CONTROL_AF_REGIONS, rectangle)
        try {
            // AE/AF区域设置通过setRepeatingRequest不断发请求
            mPreviewSession!!.setRepeatingRequest(
                mRequestBuilder!!.build(),
                captureCallback,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        mRequestBuilder!!.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        // 曝光
        mRequestBuilder!!.set(
            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
            CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START
        )
        // 触发对焦
        mRequestBuilder!!.set(
            CaptureRequest.CONTROL_AF_TRIGGER,
            CameraMetadata.CONTROL_AF_TRIGGER_START
        )
        try {
            //触发对焦通过capture发送请求, 因为用户点击屏幕后只需触发一次对焦
            mPreviewSession!!.capture(
                mRequestBuilder!!.build(),
                captureCallback,
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    // 录视频相关的代码 ----------------------  START
    private fun closePreviewSession() {
        Logger.d(TAG, "closePreviewSession")
        if (mPreviewSession != null) {
            mPreviewSession!!.close()
            mPreviewSession = null
        }
    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        if (null == mContext) {
            return
        }
        mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mMediaRecorder!!.setOutputFile(mFile!!.absolutePath)
        mMediaRecorder!!.setVideoEncodingBitRate(10000000)
        mMediaRecorder!!.setVideoFrameRate(30)
        mMediaRecorder!!.setVideoSize(mVideoSize!!.width, mVideoSize!!.height)
        mMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        mMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        when (mSensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES -> mMediaRecorder!!.setOrientationHint(
                DEFAULT_ORIENTATIONS[mDisplayRotation]
            )
            SENSOR_ORIENTATION_INVERSE_DEGREES -> mMediaRecorder!!.setOrientationHint(
                INVERSE_ORIENTATIONS[mDisplayRotation]
            )
        }
        mMediaRecorder?.setMaxDuration(recordMaxDuration)
        mMediaRecorder!!.setOnInfoListener{ _: MediaRecorder, what: Int, _: Int ->
            Logger.d(TAG, "what: $what")
            if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                Logger.d(TAG, "MEDIA_RECORDER_INFO_MAX_DURATION_REACHED")
                stopRecordingVideo()
            }
        }
        mMediaRecorder!!.prepare()
    }

    override fun startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView!!.isAvailable || null == mPreviewSize) {
            Logger.w(TAG, "startRecordingVideo something error ")
            return
        }
        mState = STATE_RECORDING
        Logger.d(TAG, "startRecordingVideo")
        mFile = createFile(getOutputDirectory(mContext!!), FILENAME, VIDEO_EXTENSION)
        Logger.d(TAG, "mFile = " + mFile + ", " + mFile?.exists())
        if (mFile?.exists() == true) {
            mFile?.delete()
        }
        try {
            closePreviewSession()
            setUpMediaRecorder()
            val texture = mTextureView!!.surfaceTexture!!
            texture.setDefaultBufferSize(mPreviewSize!!.width, mPreviewSize!!.height)
            mRequestBuilder = mCameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            val surfaces: MutableList<Surface> = ArrayList()

            // Set up Surface for the camera preview
            val previewSurface = Surface(texture)
            surfaces.add(previewSurface)
            mRequestBuilder!!.addTarget(previewSurface)

            // Set up Surface for the MediaRecorder
            val recorderSurface = mMediaRecorder!!.surface
            surfaces.add(recorderSurface)
            mRequestBuilder!!.addTarget(recorderSurface)

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice!!.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (null == mCameraDevice) {
                            return
                        }
                        try {
                            mPreviewSession = cameraCaptureSession
                            mRequestBuilder!!.set(
                                CaptureRequest.CONTROL_MODE,
                                CameraMetadata.CONTROL_MODE_AUTO
                            )
                            //A: Crop & AE(S)
                            setRequestBuilderByFlashState(mFlashState)
                            mA11yZoomLevelFirst = mCurrA11yZoomLevel
                            Logger.d(TAG, "mA11yZoomLevelFirst = $mA11yZoomLevelFirst")
                            mRequestBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, mCropRegion)
                            //A: Crop & AE(E)
                            mPreviewSession!!.setRepeatingRequest(
                                mRequestBuilder!!.build(),
                                null,
                                mBackgroundHandler
                            )
                            mMediaRecorder?.start()
                            mCallback!!.onVideoRecordStarted()
                            Logger.d(TAG, "MediaRecorder - start")
                        } catch (e: CameraAccessException) {
                            Logger.e(TAG, "MediaRecorder - start error: " + e.message)
                            e.printStackTrace()
                        }
                    }

                    override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                        if (null != mCallback) {
                            mCallback!!.onConfigureFailed()
                        }
                    }
                },
                mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun stopRecordingVideo() {
        Logger.e(TAG, "stopRecordingVideo")
        try {
            mPreviewSession?.stopRepeating()
            mPreviewSession?.abortCaptures()
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
        mMediaRecorder!!.setOnErrorListener(null)
        mMediaRecorder!!.setOnInfoListener(null)
        mMediaRecorder!!.setPreviewDisplay(null)

        // Stop recording
        mMediaRecorder!!.stop()
        mMediaRecorder!!.reset()
        mCallback?.onVideoRecordStopped()
    }

    private fun finishRecord() {
        if (null == mCameraDevice || !mTextureView!!.isAvailable || null == mPreviewSize) {
            return
        }
        closePreviewSession()
    }

    override fun recordZoom(zoom: Float) {
//        Logger.d(TAG, "recordZoom:$zoom")
        tryZoom(zoom)
    }
    // 录视频相关的代码 ----------------------  END
    /**
     * Calculates sensor crop region for a zoom level (zoom >= 1.0).
     *
     * @return Crop region.
     */
    private fun cropRegionForZoom(zoom: Float): Rect {
        val xCenter = mActiveArraySize!!.width() / 2
        val yCenter = mActiveArraySize!!.height() / 2
        val xDelta = (0.5f * mActiveArraySize!!.width() / zoom).toInt()
        val yDelta = (0.5f * mActiveArraySize!!.height() / zoom).toInt()
        return Rect(xCenter - xDelta, yCenter - yDelta, xCenter + xDelta, yCenter + yDelta)
    }

    /**
     * Method used in accessibility mode. Ensures that there are evenly spaced
     * zoom values ranging from MIN_ZOOM to NUM_ZOOM_LEVELS
     *
     * @param level is the zoom level being computed in the range
     * @return the zoom value at the given level
     */
    private fun getZoomAtLevel(level: Int): Float {
        var level = level
        if (level > NUM_ZOOM_LEVELS) {
            level = NUM_ZOOM_LEVELS
        } else if (level < 1) {
            level = 1
        }
        return MIN_ZOOM + (level - 1) * ((mMaxZoom - MIN_ZOOM) / (NUM_ZOOM_LEVELS - 1))
    }

    private fun goZoomPreview(zoom: Float) {
        mCropRegion = cropRegionForZoom(zoom)
        mRequestBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, mCropRegion)
        // A: for preview builder (S)
        mPreviewRequestBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, mCropRegion)
        // A: for preview builder (E)
        mPreviewRequest = mPreviewRequestBuilder!!.build()
        try {
            mPreviewSession!!.setRepeatingRequest(
                mPreviewRequest!!,
                mCaptureCallback, mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun goZoomRecording(zoom: Float) {
        mCropRegion = cropRegionForZoom(zoom)
        mRequestBuilder!!.set(CaptureRequest.SCALER_CROP_REGION, mCropRegion)
        try {
            mPreviewSession!!.setRepeatingRequest(
                mRequestBuilder!!.build(),
                mCaptureCallback, mBackgroundHandler
            )
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun tryZoom(delta: Float) {
        val deltaLevel = (delta / mDistanceStepLength).toInt()
        var targetLevel = deltaLevel + mA11yZoomLevelFirst
        if (targetLevel < 1) {
            targetLevel = 1
        } else if (targetLevel > NUM_ZOOM_LEVELS) {
            targetLevel = NUM_ZOOM_LEVELS
        }
        if (targetLevel == mCurrA11yZoomLevel) {
            return
        }
        mCurrA11yZoomLevel = targetLevel
        //        Logger.d(TAG, "mCurrA11yZoomLevel=" + mCurrA11yZoomLevel);
        if (STATE_PREVIEW == mState) {
            goZoomPreview(getZoomAtLevel(mCurrA11yZoomLevel))
        } else if (STATE_RECORDING == mState) {
            goZoomRecording(getZoomAtLevel(mCurrA11yZoomLevel))
        }
    }

    init {
        val windowManager = mContext!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mDisplayRotation = windowManager.defaultDisplay.rotation
        Logger.d(TAG, "mDisplayRotation = $mDisplayRotation")
        mSettings3A = Settings3A()
    }

}