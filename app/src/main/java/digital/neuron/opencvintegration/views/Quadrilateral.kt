package digital.neuron.opencvintegration.views

import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point

/**
 * This class defines detected quadrilateral
 */
data class Quadrilateral(val contour: MatOfPoint2f, val points: Array<Point>)