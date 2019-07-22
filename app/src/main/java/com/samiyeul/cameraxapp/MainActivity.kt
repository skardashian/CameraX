package com.samiyeul.cameraxapp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.graphics.Matrix
import android.util.Log
import android.util.Rational
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata

private const val REQUEST_CODE_PERMISSIONS = 10
private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

class MainActivity : AppCompatActivity(), LifecycleOwner {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Add this at the end of onCreate function

        textureView = findViewById(R.id.view_finder)

        // Request camera permissions
        if (allPermissionsGranted()) {
            textureView.post { startCamera() }
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        // Every time the provided texture view changes, recompute layout
        textureView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
    }

    // Add this after onCreate

    private lateinit var textureView: TextureView

    private fun startCamera() {
        // Create configuration object for the viewfinder use case
        val previewConfig = PreviewConfig.Builder().apply {
            setLensFacing(CameraX.LensFacing.BACK)
        }.build()

        // Build the viewfinder use case
        val preview = Preview(previewConfig)

        // Every time the viewfinder is updated, recompute layout
        preview.setOnPreviewOutputUpdateListener { previewOutput ->

            //            // To update the SurfaceTexture, we have to remove it and re-add it
//            val parent = textureView.parent as ViewGroup
//            parent.removeView(textureView)
//            parent.addView(textureView, 0)
//
//            textureView.surfaceTexture = it.surfaceTexture
//            updateTransform()

            textureView.surfaceTexture = previewOutput.surfaceTexture
        }

        val imageAnalysisConfig = ImageAnalysisConfig.Builder()
                .build()
        val imageAnalysis = ImageAnalysis(imageAnalysisConfig)

        val QrCodeAnalyzer = QrCodeAnalyzer { qrCodes ->
            qrCodes.forEach {
                Log.d("MainActivity", "QR Code detected: ${it.rawValue}.")
            }
        }

        imageAnalysis.analyzer  = QrCodeAnalyzer

        // Bind use cases to lifecycle
        // If Android Studio complains about "this" being not a LifecycleOwner
        // try rebuilding the project or updating the appcompat dependency to
        // version 1.1.0 or higher.
        CameraX.bindToLifecycle(this, preview, imageAnalysis)
    }

    private fun updateTransform() {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = textureView.width / 2f
        val centerY = textureView.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when (textureView.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        textureView.setTransform(matrix)
    }

    /**
     * Process result from permission request dialog box, has the request
     * been granted? If yes, start Camera. Otherwise display a toast
     */
    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                textureView.post { startCamera() }
            } else {
                Toast.makeText(
                        this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    /**
     * Check if all permission specified in the manifest have been granted
     */
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
                baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    class QrCodeAnalyzer(
            private val onQrCodesDetected: (qrCodes: List<FirebaseVisionBarcode>) -> Unit
    ) : ImageAnalysis.Analyzer {

        override fun analyze(image: ImageProxy, rotationDegrees: Int) {
            val options = FirebaseVisionBarcodeDetectorOptions.Builder()
                    .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_QR_CODE)
                    .build()

            val detector = FirebaseVision.getInstance().getVisionBarcodeDetector(options)

            val rotation = rotationDegreesToFirebaseRotation(rotationDegrees)
            val visionImage = FirebaseVisionImage.fromMediaImage(image.image!!, rotation)

            detector.detectInImage(visionImage)
                    .addOnSuccessListener { barcodes ->
                        onQrCodesDetected(barcodes)
                    }
                    .addOnFailureListener {
                        Log.e("QrCodeAnalyzer", "something went wrong", it)
                    }

        }

        private fun rotationDegreesToFirebaseRotation(rotationDegrees: Int): Int {
            return when (rotationDegrees) {
                0 -> FirebaseVisionImageMetadata.ROTATION_0
                90 -> FirebaseVisionImageMetadata.ROTATION_90
                180 -> FirebaseVisionImageMetadata.ROTATION_180
                270 -> FirebaseVisionImageMetadata.ROTATION_270
                else -> throw IllegalArgumentException("Not supported")
            }
        }
    }
}
