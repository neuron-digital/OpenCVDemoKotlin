@file:Suppress("DEPRECATION")

package digital.neuron.opencvintegration.activities

import android.content.Context
import android.graphics.*
import android.graphics.drawable.shapes.PathShape
import android.hardware.Camera
import android.media.AudioManager
import android.os.CountDownTimer
import android.util.Log
import digital.neuron.opencvintegration.App
import digital.neuron.opencvintegration.data.ScanHint
import digital.neuron.opencvintegration.util.ImageDetectionProperties
import digital.neuron.opencvintegration.util.OpenCVHelper
import digital.neuron.opencvintegration.util.ScanUtils
import digital.neuron.opencvintegration.views.ScanConstants
import org.opencv.core.*
import org.opencv.core.Point
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.round
import io.reactivex.subjects.PublishSubject

class CameraPreviewHandler(private val iScanner: IScanner) : Camera.PreviewCallback {

    companion object {
        private val TAG = CameraPreviewHandler::class.java.simpleName
    }

    private var autoCaptureTimer: CountDownTimer? = null
    private var secondsLeft: Int = 0
    private var isAutoCaptureScheduled: Boolean = false
    private var camera: Camera? = null
    private val subject = PublishSubject.create<Frame>()

    class Frame(val data: ByteArray, val camera: Camera)

    init {
        subject
//                .observeOn(Schedulers.computation())
                .concatMap {OpenCVHelper.createMat(it)}
                .concatMap {OpenCVHelper.findLargestQuadrilateral(it)}
//                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    iScanner.clearAndInvalidateCanvas()
                    drawLargestRect(
                            it.contour,
                            it.points,
                            it.srcSize,
                            it.srcArea)
                }
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        if (null != camera) {
            if (this.camera != null) {
                this.camera = camera
            }
            if (data != null) {
                subject.onNext(Frame(data, camera))
            }
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
                            val mAudioManager = App.applicationContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
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