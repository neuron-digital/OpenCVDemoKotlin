package digital.neuron.opencvintegration.util

import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point

/**
 * This class holds configuration of detected edges
 */
class ImageDetectionProperties(
        private val previewWidth: Double, private val previewHeight: Double,
        private val resultWidth: Double, private val resultHeight: Double,
        private val previewArea: Double, private val resultArea: Double,
        private val topLeftPoint: Point, private val bottomLeftPoint: Point,
        private val bottomRightPoint: Point, private val topRightPoint: Point) {

    val isDetectedAreaBeyondLimits: Boolean
        get() = resultArea > previewArea * 0.95 || resultArea < previewArea * 0.20

    val isDetectedWidthAboveLimit: Boolean
        get() = resultWidth / previewWidth > 0.9

    val isDetectedHeightAboveLimit: Boolean
        get() = resultHeight / previewHeight > 0.9

    val isDetectedHeightAboveNinetySeven: Boolean
        get() = resultHeight / previewHeight > 0.97

    val isDetectedHeightAboveEightyFive: Boolean
        get() = resultHeight / previewHeight > 0.85

    val isDetectedAreaAboveLimit: Boolean
        get() = resultArea > previewArea * 0.75

    val isDetectedImageDisProportionate: Boolean
        get() = resultHeight / resultWidth > 4

    val isDetectedAreaBelowLimits: Boolean
        get() = resultArea < previewArea * 0.25

    val isDetectedAreaBelowRatioCheck: Boolean
        get() = resultArea < previewArea * 0.35

    private val isRightEdgeDistorted: Boolean
        get() = Math.abs(topRightPoint.y - bottomRightPoint.y) > 100

    private val isLeftEdgeDistorted: Boolean
        get() = Math.abs(topLeftPoint.y - bottomLeftPoint.y) > 100

    val isEdgeTouching: Boolean
        get() = isTopEdgeTouching || isBottomEdgeTouching || isLeftEdgeTouching || isRightEdgeTouching

    val isReceiptToughingSides: Boolean
        get() = isLeftEdgeTouching || isRightEdgeTouching

    val isReceiptTouchingTopOrBottom: Boolean
        get() = isTopEdgeTouching || isBottomEdgeTouching

    val isReceiptTouchingTopAndBottom: Boolean
        get() = isTopEdgeTouchingProper && isBottomEdgeTouchingProper

    private val isBottomEdgeTouchingProper: Boolean
        get() = bottomLeftPoint.x >= previewHeight - 10 || bottomRightPoint.x >= previewHeight - 10

    private val isTopEdgeTouchingProper: Boolean
        get() = topLeftPoint.x <= 10 || topRightPoint.x <= 10

    private val isBottomEdgeTouching: Boolean
        get() = bottomLeftPoint.x >= previewHeight - 50 || bottomRightPoint.x >= previewHeight - 50

    private val isTopEdgeTouching: Boolean
        get() = topLeftPoint.x <= 50 || topRightPoint.x <= 50

    private val isRightEdgeTouching: Boolean
        get() = topRightPoint.y >= previewWidth - 50 || bottomRightPoint.y >= previewWidth - 50

    private val isLeftEdgeTouching: Boolean
        get() = topLeftPoint.y <= 50 || bottomLeftPoint.y <= 50

    fun isAngleNotCorrect(approx: MatOfPoint2f): Boolean =
            getMaxCosine(approx) || isLeftEdgeDistorted || isRightEdgeDistorted

    //(smallest angle is below 87 deg)
    private fun getMaxCosine(approx: MatOfPoint2f): Boolean =
            ScanUtils.getMaxCosine(0.0, approx.toArray()) >= 0.085
}
