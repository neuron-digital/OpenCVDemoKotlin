package digital.neuron.opencvintegration.data

import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size

data class RectInfo(val contour: MatOfPoint2f,
                    val points: Array<Point>,
                    val srcSize: Size,
                    val srcArea: Int)