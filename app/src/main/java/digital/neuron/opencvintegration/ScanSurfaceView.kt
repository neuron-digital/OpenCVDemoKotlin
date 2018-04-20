@file:Suppress("DEPRECATION")

package digital.neuron.opencvintegration

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.shapes.PathShape
import android.hardware.Camera
import android.media.AudioManager
import android.os.CountDownTimer
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

import java.io.IOException

import digital.neuron.opencvintegration.util.ImageDetectionProperties
import digital.neuron.opencvintegration.util.ScanUtils

import org.opencv.core.CvType.*
import kotlin.math.abs
import kotlin.math.round

@SuppressLint("ViewConstructor")
/**
 * This class previews the live images from the camera
 */
class ScanSurfaceView constructor(context: Context,
                                  private val iScanner: IScanner)
    : SurfaceView(context), SurfaceHolder.Callback {

    private var camera: Camera? = null

    private var previewSizeList: List<Camera.Size>? = null
    private var pictureSizeList: List<Camera.Size>? = null
    private var autoCaptureTimer: CountDownTimer? = null
    private var secondsLeft: Int = 0
    private var isAutoCaptureScheduled: Boolean = false

    companion object {
        private val TAG = ScanSurfaceView::class.java.simpleName
    }

    init {
        holder.addCallback(this)
    }

    private val previewCallback = Camera.PreviewCallback { data, camera ->
        if (null != camera) {
            try {
                val pictureSize = camera.parameters.previewSize
                Log.d(TAG, "onPreviewFrame - received image ${pictureSize.width}x${pictureSize.height}")

                val yuv = Mat(Size(pictureSize.width.toDouble(), pictureSize.height * 1.5), CV_8UC1)
                yuv.put(0, 0, data)

                val mat = Mat(Size(pictureSize.width.toDouble(), pictureSize.height.toDouble()), CV_8UC4)
                Imgproc.cvtColor(yuv, mat, Imgproc.COLOR_YUV2BGR_NV21, 4)
                yuv.release()

                val originalPreviewSize = mat.size()
                val originalPreviewArea = mat.rows() * mat.cols()

                val largestQuad = ScanUtils.detectLargestQuadrilateral(mat)

                mat.release()

                iScanner.clearAndInvalidateCanvas()
                if (null != largestQuad) {
                    drawLargestRect(largestQuad.contour, largestQuad.points, originalPreviewSize, originalPreviewArea)
                }
            } catch (e: Exception) {
                iScanner.clearAndInvalidateCanvas()
            }
        }
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

    private fun drawLargestRect(approx: MatOfPoint2f, points: Array<Point>, stdSize: Size, previewArea: Int) {
        // ATTENTION: axis are swapped
        val previewWidth = stdSize.height.toFloat()
        val previewHeight = stdSize.width.toFloat()

        Log.i(TAG, "previewWidth: " + previewWidth.toString())
        Log.i(TAG, "previewHeight: " + previewHeight.toString())

        val path = Path()
        //Points are drawn in anticlockwise direction
        path.moveTo(previewWidth - points[0].y.toFloat(), points[0].x.toFloat())
        path.lineTo(previewWidth - points[1].y.toFloat(), points[1].x.toFloat())
        path.lineTo(previewWidth - points[2].y.toFloat(), points[2].x.toFloat())
        path.lineTo(previewWidth - points[3].y.toFloat(), points[3].x.toFloat())
        path.close()

        val area = abs(Imgproc.contourArea(approx))
        Log.i(TAG, "Contour Area: " + area.toString())

        val newBox = PathShape(path, previewWidth, previewHeight)
        val paint = Paint()
        val border = Paint()

        //Height calculated on Y axis
        var resultHeight = points[1].x - points[0].x
        val bottomHeight = points[2].x - points[3].x
        if (bottomHeight > resultHeight)
            resultHeight = bottomHeight

        //Width calculated on X axis
        var resultWidth = points[3].y - points[0].y
        val bottomWidth = points[2].y - points[1].y
        if (bottomWidth > resultWidth)
            resultWidth = bottomWidth

        Log.i(TAG, "resultWidth: " + resultWidth.toString())
        Log.i(TAG, "resultHeight: " + resultHeight.toString())

        val propsObj = ImageDetectionProperties(
                previewWidth.toDouble(), previewHeight.toDouble(), resultWidth, resultHeight,
                previewArea.toDouble(), area, points[0], points[1], points[2], points[3])

        val scanHint: ScanHint

        if (propsObj.isDetectedAreaBeyondLimits) {
            scanHint = ScanHint.FIND_RECT
            cancelAutoCapture()
        } else if (propsObj.isDetectedAreaBelowLimits) {
            cancelAutoCapture()
            scanHint = if (propsObj.isEdgeTouching) {
                ScanHint.MOVE_AWAY
            } else {
                ScanHint.MOVE_CLOSER
            }
        } else if (propsObj.isDetectedHeightAboveLimit) {
            cancelAutoCapture()
            scanHint = ScanHint.MOVE_AWAY
        } else if (propsObj.isDetectedWidthAboveLimit || propsObj.isDetectedAreaAboveLimit) {
            cancelAutoCapture()
            scanHint = ScanHint.MOVE_AWAY
        } else {
            if (propsObj.isEdgeTouching) {
                cancelAutoCapture()
                scanHint = ScanHint.MOVE_AWAY
            } else if (propsObj.isAngleNotCorrect(approx)) {
                cancelAutoCapture()
                scanHint = ScanHint.ADJUST_ANGLE
            } else {
                Log.i(TAG, "GREEN" + "(resultWidth/resultHeight) > 4: " + resultWidth / resultHeight +
                        " points[0].x == 0 && points[3].x == 0: " + points[0].x + ": " + points[3].x +
                        " points[2].x == previewHeight && points[1].x == previewHeight: " + points[2].x + ": " + points[1].x +
                        "previewHeight: " + previewHeight)
                scanHint = ScanHint.CAPTURING_IMAGE
                iScanner.clearAndInvalidateCanvas()

                if (!isAutoCaptureScheduled) {
                    scheduleAutoCapture(scanHint)
                }
            }
        }
        Log.i(TAG, "Preview Area 95%: " + 0.95 * previewArea +
                " Preview Area 20%: " + 0.20 * previewArea +
                " Area: " + area.toString() +
                " Label: " + scanHint.toString())

        border.strokeWidth = 12f
        iScanner.displayHint(scanHint)
        setPaintAndBorder(scanHint, paint, border)
        iScanner.onNewBox(newBox, paint, border)
    }

    private fun scheduleAutoCapture(scanHint: ScanHint) {
        isAutoCaptureScheduled = true
        secondsLeft = 0
        autoCaptureTimer = object : CountDownTimer(2000, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val roundToFinish = round(millisUntilFinished.toFloat() / 1000.0f).toInt()
                if (roundToFinish != secondsLeft) {
                    secondsLeft = roundToFinish
                }
                Log.v(TAG, "" + millisUntilFinished / 1000)
                when (secondsLeft) {
                    1 -> autoCapture(scanHint)
                    else -> {
                    }
                }
            }

            override fun onFinish() {
                isAutoCaptureScheduled = false
            }
        }
        autoCaptureTimer!!.start()
    }

    private fun autoCapture(scanHint: ScanHint) {
        if (ScanHint.CAPTURING_IMAGE == scanHint) {
            if (false) {
                camera!!.setPreviewCallback(null)
                camera!!.takePicture(
                        Camera.ShutterCallback {
                            val mAudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            mAudioManager.playSoundEffect(AudioManager.FLAG_PLAY_SOUND)
                        },
                        null,
                        Camera.PictureCallback { data, _ ->
                            val options = BitmapFactory.Options()
                            options.inJustDecodeBounds = true
                            BitmapFactory.decodeByteArray(data, 0, data.size, options)

                            val bitmap = ScanUtils.loadEfficientBitmap(data,
                                    ScanConstants.LOWER_SAMPLING_THRESHOLD,
                                    ScanConstants.HIGHER_SAMPLING_THRESHOLD)

                            val matrix = Matrix()
                            matrix.postRotate(90f)
                            iScanner.onPictureClicked(ScanUtils.resize(
                                    Bitmap.createBitmap(bitmap, 0, 0,
                                            bitmap.width, bitmap.height, matrix, true),
                                    ScanConstants.LOWER_SAMPLING_THRESHOLD,
                                    ScanConstants.HIGHER_SAMPLING_THRESHOLD))
                        })
            }
            iScanner.displayHint(ScanHint.CAPTURED)
        }
    }

    private fun cancelAutoCapture() {
        if (isAutoCaptureScheduled) {
            isAutoCaptureScheduled = false
            if (null != autoCaptureTimer) {
                autoCaptureTimer!!.cancel()
            }
        }
    }

    private fun setPaintAndBorder(scanHint: ScanHint, paint: Paint, border: Paint) {
        when (scanHint) {
            ScanHint.MOVE_CLOSER, ScanHint.MOVE_AWAY, ScanHint.ADJUST_ANGLE -> {
                paint.color = Color.argb(30, 255, 38, 0)
                border.color = Color.rgb(255, 38, 0)
            }
            ScanHint.FIND_RECT -> {
                paint.color = Color.argb(0, 0, 0, 0)
                border.color = Color.argb(0, 0, 0, 0)
            }
            ScanHint.CAPTURING_IMAGE -> {
                paint.color = Color.argb(30, 38, 216, 76)
                border.color = Color.rgb(38, 216, 76)
            }
            else -> {
                paint.color = 0
                border.color = 0
            }
        }
    }
}
