package com.chenlongguo.lib_cl_camera

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import java.io.File

class LongMediaScanner(
    val context: Context,
    private val file: File,
    private val mimeType: String,
    private var onScanCompletedListener: OnScanCompletedListener?
) : MediaScannerConnection.MediaScannerConnectionClient {
    interface OnScanCompletedListener {
        fun onScanCompleted(path: String?, uri: Uri?)
    }

    private var mediaScannerConnection:MediaScannerConnection? = MediaScannerConnection(context, this)

    init {
        mediaScannerConnection?.connect()
    }

    override fun onScanCompleted(path: String?, uri: Uri?) {
        mediaScannerConnection?.disconnect()
        mediaScannerConnection = null
        onScanCompletedListener?.onScanCompleted(path, uri)
        onScanCompletedListener = null
    }

    override fun onMediaScannerConnected() {
        mediaScannerConnection?.scanFile(file.absolutePath, mimeType)
    }
}