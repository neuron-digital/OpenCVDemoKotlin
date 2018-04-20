@file:Suppress("DEPRECATION")

package digital.neuron.opencvintegration.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.hardware.Camera
import android.util.Log
import android.util.TypedValue
import android.view.Surface

import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.utils.Converters

import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.Comparator

import digital.neuron.opencvintegration.Quadrilateral

import org.opencv.core.CvType.CV_8UC1
import org.opencv.imgproc.Imgproc.THRESH_BINARY
import org.opencv.imgproc.Imgproc.THRESH_OTSU
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * This class provides utilities for camera.
 */

object ScanUtils {
    private val TAG = ScanUtils::class.java.simpleName

    private fun compareFloats(left: Float, right: Float): Boolean = abs(left - right) < 0.00000001

    fun determinePictureSize(previewSize: Camera.Size, pictureSizeList: Iterable<Camera.Size>): Camera.Size? {
        var retSize: Camera.Size? = null

        // if the preview size is not supported as a picture size
        val reqRatio = previewSize.width.toFloat() / previewSize.height
        var curRatio: Float
        var deltaRatio: Float
        var deltaRatioMin = Float.MAX_VALUE
        for (size in pictureSizeList) {
            curRatio = size.width.toFloat() / size.height
            deltaRatio = abs(reqRatio - curRatio)
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio
                retSize = size
            }
            if (compareFloats(deltaRatio, 0f)) {
                break
            }
        }

        return retSize
    }

    fun getOptimalPreviewSize(w: Int, h: Int, previewSizeList: Iterable<Camera.Size>?): Camera.Size? {
        val targetRatio = h.toDouble() / w
        if (previewSizeList == null) return null

        var optimalSize: Camera.Size? = null
        var minDiff = Double.MAX_VALUE

        previewSizeList.forEach {
            val ratio = it.width.toDouble() / it.height
            if (abs(ratio - targetRatio) <= 0.1) {
                val diff = abs(it.height - h)
                if (diff < minDiff) {
                    optimalSize = it
                    minDiff = diff.toDouble()
                }
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE
            previewSizeList.forEach {
                val diff = abs(it.height - h)
                if (diff < minDiff) {
                    optimalSize = it
                    minDiff = diff.toDouble()
                }
            }
        }
        return optimalSize
    }

    fun configureCameraAngle(activity: Activity): Int =
            when (activity.windowManager.defaultDisplay.rotation) {
                Surface.ROTATION_0 // This is display orientation
                -> 90 // This is camera orientation
                Surface.ROTATION_90 -> 0
                Surface.ROTATION_180 -> 270
                Surface.ROTATION_270 -> 180
                else -> 90
            }

    fun detectLargestQuadrilateral(mat: Mat): Quadrilateral? {
        val grayMat = Mat(mat.rows(), mat.cols(), CV_8UC1)
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY, 4)
        Imgproc.threshold(grayMat, grayMat, 150.0, 255.0, THRESH_BINARY + THRESH_OTSU)

        val largestContour = findLargestContour(grayMat)
        if (null != largestContour) {
            val mLargestRect = findQuadrilateral(largestContour)
            if (mLargestRect != null)
                return mLargestRect
        }
        return null
    }

    fun getMaxCosine(maxCosine: Double, approxPoints: Array<Point>): Double {
        var maxCosine = maxCosine
        for (i in 2..4) {
            val cosine = abs(angle(
                    approxPoints[i % 4],
                    approxPoints[i - 2],
                    approxPoints[i - 1]))
            maxCosine = max(cosine, maxCosine)
        }
        return maxCosine
    }

    private fun angle(p1: Point, p2: Point, p0: Point): Double {
        val dx1 = p1.x - p0.x
        val dy1 = p1.y - p0.y
        val dx2 = p2.x - p0.x
        val dy2 = p2.y - p0.y
        return (dx1 * dx2 + dy1 * dy2) / sqrt((dx1 * dx1 + dy1 * dy1) * (dx2 * dx2 + dy2 * dy2) + 1e-10)
    }

    private fun sortPoints(src: Array<Point>): Array<Point> {
        val sumComparator = Comparator<Point> { lhs, rhs -> (lhs.y + lhs.x).compareTo(rhs.y + rhs.x) }
        val diffComparator = Comparator<Point> { lhs, rhs -> (lhs.y - lhs.x).compareTo(rhs.y - rhs.x) }
        return arrayOf(src.minWith(sumComparator)!!,  // top-left corner = minimal sum
                src.minWith(diffComparator)!!,        // top-right corner = minimal difference
                src.maxWith(sumComparator)!!,         // bottom-right corner = maximal sum
                src.maxWith(diffComparator)!!)        // bottom-left corner = maximal difference
    }

    private fun findLargestContour(inputMat: Mat): List<MatOfPoint>? {
        val contourList = ArrayList<MatOfPoint>()
        //finding contours
        Imgproc.findContours(inputMat, contourList, Mat(), Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE)
        Mat().create(inputMat.rows(), inputMat.cols(), CvType.CV_8U)
        if (contourList.isNotEmpty()) {
            contourList.sortWith(Comparator { lhs, rhs ->
                Imgproc.contourArea(rhs).compareTo(Imgproc.contourArea(lhs))
            })
            return contourList
        }
        return null
    }

    private fun findQuadrilateral(contourList: List<MatOfPoint>): Quadrilateral? {
        contourList.forEach {
            val c2f = MatOfPoint2f(*it.toArray())
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(c2f, approx,
                    0.02 * Imgproc.arcLength(c2f, true), true)
            // select biggest 4 angles polygon
            if (approx.rows() == 4) {
                return Quadrilateral(approx, sortPoints(approx.toArray()))
            }
        }
        return null
    }

    fun enhanceReceipt(image: Bitmap, topLeft: Point, topRight: Point, bottomLeft: Point, bottomRight: Point): Bitmap {
        var resultWidth = (topRight.x - topLeft.x).toInt()
        val bottomWidth = (bottomRight.x - bottomLeft.x).toInt()
        if (bottomWidth > resultWidth)
            resultWidth = bottomWidth

        var resultHeight = (bottomLeft.y - topLeft.y).toInt()
        val bottomHeight = (bottomRight.y - topRight.y).toInt()
        if (bottomHeight > resultHeight)
            resultHeight = bottomHeight

        val inputMat = Mat(image.height, image.height, CvType.CV_8UC1)
        Utils.bitmapToMat(image, inputMat)
        val outputMat = Mat(resultWidth, resultHeight, CvType.CV_8UC1)

        Imgproc.warpPerspective(inputMat, outputMat,
                Imgproc.getPerspectiveTransform(
                        Converters.vector_Point2f_to_Mat(arrayListOf(
                                topLeft, topRight, bottomLeft, bottomRight)),
                        Converters.vector_Point2f_to_Mat(arrayListOf(
                                Point(0.0, 0.0),
                                Point(resultWidth.toDouble(), 0.0),
                                Point(0.0, resultHeight.toDouble()),
                                Point(resultWidth.toDouble(), resultHeight.toDouble())))),
                Size(resultWidth.toDouble(), resultHeight.toDouble()))

        val output = Bitmap.createBitmap(resultWidth, resultHeight, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputMat, output)
        return output
    }

    fun saveToInternalMemory(bitmap: Bitmap, mFileDirectory: String,
                             mFileName: String, mContext: Context, mQuality: Int): Array<String?> {

        val returnParams = arrayOfNulls<String>(2)
        val directory = getBaseDirectoryFromPathString(mFileDirectory, mContext)
        try {
            val fileOutputStream = FileOutputStream(File(directory, mFileName))
            //Compress method used on the Bitmap object to write  image to output stream
            bitmap.compress(Bitmap.CompressFormat.JPEG, mQuality, fileOutputStream)
            fileOutputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        }

        returnParams[0] = directory.absolutePath
        returnParams[1] = mFileName
        return returnParams
    }

    private fun getBaseDirectoryFromPathString(mPath: String, mContext: Context): File =
            ContextWrapper(mContext).getDir(mPath, Context.MODE_PRIVATE)

    fun decodeBitmapFromFile(path: String, imageName: String): Bitmap {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        return BitmapFactory.decodeFile(File(path, imageName).absolutePath, options)
    }

    fun dp2px(context: Context, dp: Float): Int =
            Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    dp, context.resources.displayMetrics))

    fun loadEfficientBitmap(data: ByteArray, width: Int, height: Int): Bitmap {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeByteArray(data, 0, data.size, options)

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, width, height)

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options,
                                      reqWidth: Int,
                                      reqHeight: Int): Int {
        // Raw height and width of image
        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun resize(image: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        var image = image
        if (maxHeight > 0 && maxWidth > 0) {
            val ratioBitmap = image.width.toFloat() / image.height.toFloat()
            val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

            var finalWidth = maxWidth
            var finalHeight = maxHeight
            if (ratioMax > 1) {
                finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
            } else {
                finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
            }

            image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true)
            return image
        } else {
            return image
        }
    }

    fun resizeToScreenContentSize(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(newWidth.toFloat() / bm.width, newHeight.toFloat() / bm.height)

        // "RECREATE" THE NEW BITMAP
        val resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, bm.width, bm.height, matrix, false)
        bm.recycle()
        return resizedBitmap
    }

    fun getPolygonDefaultPoints(bitmap: Bitmap): ArrayList<PointF> {
        return arrayListOf(PointF(bitmap.width * 0.14f, bitmap.height.toFloat() * 0.13f),
                PointF(bitmap.width * 0.84f, bitmap.height.toFloat() * 0.13f),
                PointF(bitmap.width * 0.14f, bitmap.height.toFloat() * 0.83f),
                PointF(bitmap.width * 0.84f, bitmap.height.toFloat() * 0.83f))
    }

    fun isScanPointsValid(points: Map<Int, PointF>): Boolean = points.size == 4

}
