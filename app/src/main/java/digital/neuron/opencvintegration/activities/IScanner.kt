package digital.neuron.opencvintegration.activities

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.shapes.Shape
import digital.neuron.opencvintegration.data.ScanHint

interface IScanner {
    fun displayHint(scanHint: ScanHint)
    fun onNewBox(box: Shape, paint: Paint, border: Paint)
    fun clearAndInvalidateCanvas()
    fun onPictureClicked(bitmap: Bitmap)
}
