package com.chenlongguo.lib_cl_camera.camera2

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Point
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.MediaController
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.chenlongguo.lib_cl_camera.*
import com.chenlongguo.lib_cl_camera.camera2.utils.AngleUtil.Companion.getSensorAngle
import com.chenlongguo.lib_cl_camera.camera2.utils.Logger
import com.chenlongguo.lib_cl_camera.camera2.view.CaptureButtonListener
import com.chenlongguo.lib_cl_camera.camera2.view.OnFlashStateChangedListener
import com.chenlongguo.lib_cl_camera.databinding.FragmentCamera2BasicBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class Camera2BasicFragment : Fragment(), View.OnClickListener,
    ActivityCompat.OnRequestPermissionsResultCallback {

    private lateinit var binding: FragmentCamera2BasicBinding

    private lateinit var mCameraInterface: ICameraInterface
    private lateinit var config: RequestConfig

    // 拍摄的类型，起初是未知的，只有在用户点击或长按后才确定
    private var mType = CaptureUtil.RESULT_TYPE_UNKNOWN
    private var fileName: String? = null
    private var isStopAnimFinished = false
    private var isRecordVideoFinished = false
    private var mSensorManager: SensorManager? = null

    private var isInShowMode = false


    /**
     * Shows a [Toast] on the UI thread.
     *
     * @param text The message to show
     */
    private fun showToast(text: String) {
        activity?.runOnUiThread { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show() }
    }

    private var mCaptureButtonListener: CaptureButtonListener = object : CaptureButtonListener {
        override fun takePictures() {
            mType = CaptureUtil.RESULT_TYPE_PICTURE
            mCameraInterface.takePicture()
        }

        override fun recordShort(time: Long) {}
        override fun startRecord() {
            isStopAnimFinished = false
            isRecordVideoFinished = false
            mType = CaptureUtil.RESULT_TYPE_VIDEO
            mCameraInterface.startRecordingVideo()
        }

        override fun recordEnd(time: Long) {
            Logger.d(TAG, "recordEnd: $time")
            mCameraInterface.stopRecordingVideo()
        }

        override fun recordDurationMax() {
            Logger.d(TAG, "recordDurationMax")
        }

        override fun recordZoom(zoom: Float) {
            mCameraInterface.recordZoom(zoom)
        }

        override fun recordError() {}
        override fun onStopAnimFinished() {
            Logger.d(TAG, "onStopAnimFinished")
            isStopAnimFinished = true
            showRecordedVideo()
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mCameraInterface = CameraInterface(context)
        config = arguments?.get(ARG_CONFIG) as RequestConfig
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?): View? {
        binding =
            DataBindingUtil.inflate(inflater, R.layout.fragment_camera2_basic, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Logger.d(TAG, "onViewCreated")

        binding.captureButton.type = config.type
        binding.captureButton.setDuration(config.duration)
        binding.captureButton.setCaptureButtonListener(mCaptureButtonListener)
        binding.imageSwitch.setOnClickListener(this)
        binding.btnOk.setOnClickListener(this)
        binding.chooseFlash.setOnFlashStateChangedListener(object : OnFlashStateChangedListener {
            override fun onChanged(state: Int) {
                mCameraInterface.setFlashState(state)
            }
        })
        binding.closeUp.setOnClickListener {
            val captureResult = Result()
            captureResult.resultCode = Activity.RESULT_CANCELED
            closeActivity(captureResult)
        }
        mCameraInterface.setRecordMaxDuration(config.duration)
        mCameraInterface.setTextureView(binding.texture)
        mCameraInterface.setCameraInterfaceCallback(
            object : ICameraInterfaceCallback {
                override fun onError(code: Int) {
                    when (code) {
                        CameraInterface.ERROR_CODE_CAMERA_STATE_ERROR -> {
                            val captureResult = Result()
                            captureResult.resultCode = Activity.RESULT_CANCELED
                            closeActivity(captureResult)
                        }
                        CameraInterface.ERROR_CODE_NPE_ERROR -> {
                            ErrorDialog.newInstance(getString(R.string.camera_error))
                                .show(childFragmentManager, FRAGMENT_DIALOG)
                        }
                    }
                }

                override fun needCameraPermission() {
                    requestCameraPermission()
                }

                override fun onConfigureFailed() {
                    showToast(getString(R.string.camera_config_failed))
                }

                override fun onVideoRecordStarted() {
                    activity?.runOnUiThread { binding.captureButton.startRecordAnimation() }
                }

                override fun onVideoRecordStopped() {
                    Logger.d(TAG, "onVideoRecordStopped")
                    isRecordVideoFinished = true
                    showRecordedVideo()
                }

                override fun onFocusStart(x: Int, y: Int) {
                    binding.focusView.visibility = View.VISIBLE
                    binding.focusView.translationX = x - binding.focusView.deltaX.toFloat()
                    binding.focusView.translationY = y - binding.focusView.deltaY.toFloat()
                }

                override fun onFocusFinish(success: Boolean) {
                    activity?.runOnUiThread { binding.focusView.finishFocus(success) }
                }

                override fun onFlashStateUpdate(supported: Boolean) {
                    activity?.runOnUiThread {
                        binding.chooseFlash.visibility = if (supported) View.VISIBLE else View.GONE
                    }
                }
            })
        mCameraInterface.setImageSaveListener(
            object : ImageSaveListener {
                override fun finish() {
                    activity?.runOnUiThread { showCapturedImage() }
                }
            })
    }

    private fun showRecordedVideo() {
        if (!isStopAnimFinished || !isRecordVideoFinished) {
            Logger.d(
                TAG,
                "isStopAnimFinished=$isStopAnimFinished, isRecordVideoFinished=$isRecordVideoFinished"
            )
            return
        }
        isInShowMode = true;

        binding.texture.visibility = View.GONE
        binding.imageSwitch.visibility = View.GONE
        binding.chooseFlash.visibility = View.GONE
        binding.displayImg.visibility = View.GONE
        binding.captureButton.visibility = View.GONE
        binding.displayVideo.visibility = View.VISIBLE
        binding.btnOk.visibility = View.VISIBLE
        binding.displayVideo.setVideoPath(mCameraInterface.getFile()?.absolutePath)

        //创建MediaController对象
        val mediaController = MediaController(context)

        //VideoView与MediaController建立关联
        binding.displayVideo.setMediaController(mediaController)

        //让VideoView获取焦点
        binding.displayVideo.requestFocus()
        binding.displayVideo.start()
        binding.displayVideo.setOnCompletionListener { mp ->
            mp.start()
            mp.isLooping = true
        }
    }

    private fun showCapturedImage() {
        isInShowMode = true;
        binding.texture.visibility = View.GONE
        binding.imageSwitch.visibility = View.GONE
        binding.chooseFlash.visibility = View.GONE
        binding.displayImg.visibility = View.VISIBLE
        binding.captureButton.visibility = View.GONE
        binding.displayVideo.visibility = View.GONE
        binding.btnOk.visibility = View.VISIBLE
        Glide.with(context!!)
            .load(mCameraInterface.getFile())
            .skipMemoryCache(true) // 不使用内存缓存
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(binding.displayImg)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        Logger.d(TAG, "onActivityCreated")
        val displaySize = Point()
        activity?.windowManager?.defaultDisplay?.getSize(displaySize)
        Logger.d(TAG, "displaySize=$displaySize")
        mCameraInterface.setDisplaySize(displaySize.x, displaySize.y)
        fileName = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
            .format(System.currentTimeMillis())
    }

    override fun onResume() {
        super.onResume()
        Logger.d(TAG, "onResume")
        if (!hasPermissionsGranted(PERMISSIONS)) {
            requestPermissions()
            return
        }
        registerSensorManager()
        mCameraInterface.setPaused(false)
        mCameraInterface.open()

    }

    private fun hasPermissionsGranted(permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (ActivityCompat.checkSelfPermission(activity!!, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    /**
     * Requests permissions needed for recording video.
     */
    private fun requestPermissions() {
        requestPermissions(PERMISSIONS, REQUEST_PERMISSIONS)
    }

    override fun onPause() {
        super.onPause()
        unregisterSensorManager()
        mCameraInterface.setPaused(true)
        Logger.d(TAG, "onPause")
        mCameraInterface.close()
    }

    override fun onStart() {
        super.onStart()
        Logger.d(TAG, "onStart")
    }

    override fun onStop() {
        super.onStop()
        Logger.d(TAG, "onStop")
        if (binding.displayVideo.isPlaying) {
            binding.displayVideo.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.d(TAG, "onDestroy")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Logger.d(TAG, "onDestroyView")
    }

    private fun requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            ConfirmationDialog().show(childFragmentManager, FRAGMENT_DIALOG)
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_permission))
                    .show(childFragmentManager, FRAGMENT_DIALOG)
            }
        } else if (REQUEST_STORAGE_PERMISSION == requestCode) {
            if (grantResults.size != 1 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                ErrorDialog.newInstance(getString(R.string.request_storage_permission))
                    .show(childFragmentManager, FRAGMENT_DIALOG)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onClick(view: View) {
        val id = view.id
        if (id == R.id.image_switch) {
            mCameraInterface.switchCamera()
        } else if (id == R.id.btn_ok) {
            Logger.d(TAG, "CLICK ok")
            mCameraInterface.getFile()?.let {
                onFileSaved(it)
            }


        }
    }

//    fun onBackPressed() {
//        if (isInShowMode) {
//            mCameraInterface?.getFile()?.delete();
//        }
//    }

    fun onScanFinished() {
        val captureResult = Result()
        captureResult.resultCode = Activity.RESULT_OK
        captureResult.type = mType
        captureResult.originPath = mCameraInterface.getFile()?.absolutePath
        closeActivity(captureResult)
    }

    private fun onFileSaved(file: File) {
        Logger.d(TAG, "onFileSaved:" + file.absolutePath)
        val mimeType = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(file.extension)

        LongMediaScanner(requireContext().applicationContext, file, mimeType!!, object : LongMediaScanner.OnScanCompletedListener {
            override fun onScanCompleted(path: String?, uri: Uri?) {
                Logger.d(TAG, "$path , $uri")
                onScanFinished()
            }
        })
    }

    /**
     * Shows an error message dialog.
     */
    class ErrorDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return AlertDialog.Builder(activity)
                .setMessage(arguments?.getString(ARG_MESSAGE))
                .setPositiveButton(android.R.string.ok) { _, _ -> activity?.finish() }
                .create()
        }

        companion object {
            private const val ARG_MESSAGE = "message"
            fun newInstance(message: String?): ErrorDialog {
                val dialog = ErrorDialog()
                val args = Bundle()
                args.putString(ARG_MESSAGE, message)
                dialog.arguments = args
                return dialog
            }
        }
    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    class ConfirmationDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val parent = parentFragment
            return AlertDialog.Builder(activity)
                .setMessage(R.string.request_permission)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    parent!!.requestPermissions(
                        arrayOf(Manifest.permission.CAMERA),
                        REQUEST_STORAGE_PERMISSION
                    )
                }
                .setNegativeButton(
                    android.R.string.cancel
                ) { _, _ ->
                    val activity: Activity? = parent!!.activity
                    activity?.finish()
                }
                .create()
        }
    }

    /**
     * Shows OK/Cancel confirmation dialog about Storage permission.
     */
    class ConfirmationStorageDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val parent = parentFragment
            return AlertDialog.Builder(activity)
                .setMessage(R.string.request_storage_permission)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    parent!!.requestPermissions(
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        REQUEST_STORAGE_PERMISSION
                    )
                }
                .setNegativeButton(
                    android.R.string.cancel
                ) { _, _ ->
                    val activity: Activity? = parent?.activity
                    activity?.finish()
                }
                .create()
        }
    }

    private val mSensorEventListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (Sensor.TYPE_ACCELEROMETER != event.sensor.type) {
                return
            }
            val values = event.values
            val angle = getSensorAngle(values[0], values[1], values[2])
            mCameraInterface.setAngle(angle)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    private fun registerSensorManager() {
        if (mSensorManager == null) {
            mSensorManager = context?.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }
        mSensorManager?.registerListener(
            mSensorEventListener,
            mSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_NORMAL
        )
    }

    private fun unregisterSensorManager() {
        if (mSensorManager == null) {
            mSensorManager = context?.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        }
        mSensorManager?.unregisterListener(mSensorEventListener)
    }

    private fun closeActivity(result: Result) {
        activity?.let {
            (it as CaptureActivity).close(result)
        }
    }

    companion object {
        private const val TAG = "CameraFragment"
        private const val ARG_CONFIG = "config"

        private val PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        private const val REQUEST_PERMISSIONS = 101
        private const val REQUEST_CAMERA_PERMISSION = 1
        private const val REQUEST_STORAGE_PERMISSION = 2
        private const val FRAGMENT_DIALOG = "dialog"

        fun newInstance(config: RequestConfig): Camera2BasicFragment {
            val fragment = Camera2BasicFragment()
            val args = Bundle()
            args.putSerializable(ARG_CONFIG, config)
            fragment.arguments = args
            return fragment
        }
    }
}