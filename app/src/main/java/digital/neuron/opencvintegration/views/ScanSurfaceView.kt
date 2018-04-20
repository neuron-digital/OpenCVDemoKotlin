@file:Suppress("DEPRECATION")

package digital.neuron.opencvintegration.views

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.hardware.Camera
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

import java.io.IOException

import digital.neuron.opencvintegration.util.ScanUtils

@SuppressLint("ViewConstructor")
/**
 * This class previews the live images from the camera
 */
class ScanSurfaceView constructor(context: Context,
                                  private val previewCallback: Camera.PreviewCallback)
    : SurfaceView(context), SurfaceHolder.Callback {

    private var camera: Camera? = null

    private var previewSizeList: List<Camera.Size>? = null
    private var pictureSizeList: List<Camera.Size>? = null

    companion object {
        private val TAG = ScanSurfaceView::class.java.simpleName
    }

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            if (camera == null) {
                camera = Camera.open()
            }
            this.camera!!.setPreviewDisplay(holder)
            this.camera!!.startPreview()
            this.camera!!.setPreviewCallback(previewCallback)
            val cameraParams = camera!!.parameters

            previewSizeList = cameraParams.supportedPreviewSizes
            pictureSizeList = cameraParams.supportedPictureSizes.sortedWith(
                    Comparator { a, b -> b.width * b.height - a.width * a.height })

            if (cameraParams.supportedFlashModes != null && cameraParams.supportedFlashModes
                            .contains(Camera.Parameters.FLASH_MODE_AUTO)) {
                cameraParams.flashMode = Camera.Parameters.FLASH_MODE_AUTO
            }
            if (cameraParams.supportedFocusModes != null && cameraParams.supportedFocusModes
                            .contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                cameraParams.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            } else if (cameraParams.supportedFocusModes != null && cameraParams.supportedFocusModes
                            .contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                cameraParams.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
            }

            camera!!.parameters = cameraParams
        } catch (e: IOException) {
            Log.e(TAG, e.message, e)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        var previewSize: Camera.Size? = ScanUtils.getOptimalPreviewSize(width, height, previewSizeList)
        if (previewSize == null) {
            previewSize = camera!!.parameters.previewSize
        }
        var pictureSize = ScanUtils.determinePictureSize(previewSize!!, pictureSizeList!!)
        if (pictureSize == null) {
            pictureSize = camera!!.parameters.pictureSize
        }

        this.holder.setFixedSize(previewSize.height, previewSize.width)

        val cameraParams = camera!!.parameters

        camera!!.setDisplayOrientation(ScanUtils.configureCameraAngle((context as Activity?)!!))

        cameraParams.setPreviewSize(previewSize.width, previewSize.height)
        cameraParams.setPictureSize(pictureSize!!.width, pictureSize.height)

        camera!!.parameters = cameraParams
        camera!!.startPreview()
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (camera != null) {
            // Call stopPreview() to stop updating the preview surface.
            camera!!.stopPreview()
            camera!!.setPreviewCallback(null)
            // Important: Call release() to release the camera for use by other
            // applications. Applications should release the camera immediately
            // during onPause() and re-open() it during onResume()).
            camera!!.release()
            camera = null
        }
    }

}
