package digital.neuron.opencvintegration

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.shapes.Shape
import android.util.AttributeSet
import android.view.View

/**
 * Draws an array of shapes on a canvas
 */
class ScanCanvasView @JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null,
        defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    private val shapes = mutableListOf<ScanShape>()

    inner class ScanShape(private val shape: Shape, private val paint: Paint) {

        private var border: Paint? = null

        constructor(shape: Shape, paint: Paint, border: Paint) : this(shape, paint) {
            this.border = border
            this.border?.style = Paint.Style.STROKE
        }

        fun resize(width: Float, height: Float) = shape.resize(width, height)

        fun draw(canvas: Canvas) {
            shape.draw(canvas, paint)
            if (border != null) {
                shape.draw(canvas, border)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // allocations per draw cycle.
        val paddingLeft = paddingLeft
        val paddingTop = paddingTop
        val paddingRight = paddingRight
        val paddingBottom = paddingBottom

        val contentWidth = width - paddingLeft - paddingRight
        val contentHeight = height - paddingTop - paddingBottom

        shapes.forEach {
            it.resize(contentWidth.toFloat(), contentHeight.toFloat())
            it.draw(canvas)
        }
    }

    fun addShape(shape: Shape, paint: Paint): ScanShape {
        val scanShape = ScanShape(shape, paint)
        shapes.add(scanShape)
        return scanShape
    }

    fun addShape(shape: Shape, paint: Paint, border: Paint) =
            shapes.add(ScanShape(shape, paint, border))

    fun removeShape(shape: ScanShape) = shapes.remove(shape)
    fun removeShape(index: Int) = shapes.removeAt(index)
    fun clear() = shapes.clear()

}