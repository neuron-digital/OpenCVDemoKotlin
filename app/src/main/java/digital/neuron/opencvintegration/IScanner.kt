package digital.neuron.opencvintegration

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.shapes.Shape

interface IScanner {
    fun displayHint(scanHint: ScanHint)
    fun onNewBox(box: Shape, paint: Paint, border: Paint)
    fun clearAndInvalidateCanvas()
    fun onPictureClicked(bitmap: Bitmap)
}
