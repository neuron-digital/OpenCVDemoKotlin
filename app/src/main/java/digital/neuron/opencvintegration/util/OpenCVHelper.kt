package digital.neuron.opencvintegration.util

import android.util.Log
import digital.neuron.opencvintegration.activities.CameraPreviewHandler
import digital.neuron.opencvintegration.data.RectInfo
import io.reactivex.Observable
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object OpenCVHelper {
    private const val TAG = "OpenCVHelper"

    fun createMat(frame: CameraPreviewHandler.Frame): Observable<Mat> {
        return Observable.create({
            val pictureSize = frame.camera.parameters.previewSize
            Log.d(TAG, "onPreviewFrame - received image ${pictureSize.width}x${pictureSize.height}")

            val yuv = Mat(Size(pictureSize.width.toDouble(), pictureSize.height * 1.5), CvType.CV_8UC1)
            yuv.put(0, 0, frame.data)

            val mat = Mat(Size(pictureSize.width.toDouble(), pictureSize.height.toDouble()), CvType.CV_8UC4)
            Imgproc.cvtColor(yuv, mat, Imgproc.COLOR_YUV2BGR_NV21, 4)
            yuv.release()

            it.onNext(mat)
            it.onComplete()
        })
    }

    fun findLargestQuadrilateral(mat: Mat): Observable<RectInfo> {
        return Observable.create({
            val originalPreviewSize = mat.size()
            val originalPreviewArea = mat.rows() * mat.cols()
            val largestQuad = ScanUtils.detectLargestQuadrilateral(mat)
            mat.release()

            if(largestQuad != null) {
                it.onNext(RectInfo(largestQuad.contour, largestQuad.points, originalPreviewSize, originalPreviewArea))
            }
            it.onComplete()
        })
    }
}

