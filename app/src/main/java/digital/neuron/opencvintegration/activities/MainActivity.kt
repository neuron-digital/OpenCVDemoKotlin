package digital.neuron.opencvintegration.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.shapes.Shape
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import digital.neuron.opencvintegration.R
import digital.neuron.opencvintegration.data.ScanHint
import digital.neuron.opencvintegration.views.ScanSurfaceView
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.toast

private const val MY_PERMISSIONS_REQUEST_CAMERA = 101

class MainActivity : AppCompatActivity(), IScanner {

    companion object {
        init {
            System.loadLibrary("opencv_java3")
        }
    }

    private var isPermissionNotGranted: Boolean = false
    private val previewHandler = CameraPreviewHandler(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkCameraPermissions()
    }

    private fun checkCameraPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                        Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            isPermissionNotGranted = true
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                            Manifest.permission.CAMERA)) {
                toast("Enable camera permission from settings")
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA),
                        MY_PERMISSIONS_REQUEST_CAMERA)
            }
        } else {
            if (!isPermissionNotGranted) {
                cameraPreviewLayout.addView(ScanSurfaceView(this, previewHandler))
            } else {
                isPermissionNotGranted = false
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_CAMERA -> onRequestCamera(grantResults)
        }
    }

    private fun onRequestCamera(grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Handler().postDelayed({
                runOnUiThread {
                    cameraPreviewLayout.addView(ScanSurfaceView(this, previewHandler))
                }
            }, 500)
        } else {
            toast(R.string.camera_activity_permission_denied_toast)
            finish()
        }
    }

    override fun onNewBox(box: Shape, paint: Paint, border: Paint) {
        scanCanvasView.clear()
        scanCanvasView.addShape(box, paint, border)
        scanCanvasView.invalidate()
    }

    override fun clearAndInvalidateCanvas() {
        scanCanvasView.clear()
        scanCanvasView.invalidate()
    }

    override fun onPictureClicked(bitmap: Bitmap) {

    }

    override fun displayHint(scanHint: ScanHint) {
        when (scanHint) {
            ScanHint.MOVE_CLOSER -> toast(R.string.move_closer)
            ScanHint.MOVE_AWAY -> toast(R.string.move_away)
            ScanHint.ADJUST_ANGLE -> toast(R.string.adjust_angle)
            ScanHint.FIND_RECT -> toast(R.string.finding_rect)
            ScanHint.CAPTURING_IMAGE -> toast(R.string.hold_still)
            ScanHint.CAPTURED -> toast(R.string.captured)
            else -> {
            }
        }
    }
}
