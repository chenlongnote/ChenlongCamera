package com.chenlongguo.lib_cl_camera.camera2

import android.graphics.*
import android.media.Image
import com.chenlongguo.lib_cl_camera.camera2.ImageSaveListener

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer


/**
 * Saves a JPEG {@link Image} into the specified {@link File}.
 */
class ImageSaver(private var mImage: Image,
                 private var mFile: File,
                 private var isFront:Boolean,
                 private var mListener: ImageSaveListener
) : Runnable {
    private fun saveImage() {
        val buffer: ByteBuffer = mImage?.getPlanes()?.get(0)?.getBuffer()!!
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var output: FileOutputStream? = null
        try {
            output = FileOutputStream(mFile)
            output?.write(bytes)
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (mListener != null) {
                mListener?.finish()
            }
            mImage?.close()
            if (null != output) {
                try {
                    output.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun saveImageMirror() {
        val buffer: ByteBuffer = mImage?.getPlanes()?.get(0)?.getBuffer()!!
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var fOutput: FileOutputStream? = null
        var bOutput: BufferedOutputStream? = null
        try {
            val origin = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            val target = Bitmap.createBitmap(origin.width, origin.height, origin.config)
            val canvas = Canvas(target)
            val paint = Paint()
            val matrix = Matrix()
            matrix.postScale(-1f, 1f)
            matrix.postTranslate(origin.width as Float, 0f)
            canvas.drawBitmap(origin, matrix, paint)
            fOutput = FileOutputStream(mFile)
            bOutput = BufferedOutputStream(fOutput)
            target.compress(Bitmap.CompressFormat.JPEG, 100, bOutput)
            bOutput.flush()
            origin.recycle()
            target.recycle()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            if (mListener != null) {
                mListener?.finish()
            }
            mImage?.close()
            if (null != bOutput) {
                try {
                    bOutput.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
            if (null != fOutput) {
                try {
                    fOutput.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun run() {
        if (isFront) {
            saveImageMirror()
        } else {
            saveImage()
        }
    }
}