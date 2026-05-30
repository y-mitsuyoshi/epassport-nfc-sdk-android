package com.example.epassport.ocr

import android.annotation.SuppressLint
import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX と Google ML Kit を使用したオンデバイス（ローカル完結型）MRZスキャナーの実装。
 */
class CameraMrzScanner(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) : MrzScanner {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_SIGNATURE)
    private var cameraProvider: ProcessCameraProvider? = null

    @SuppressLint("UnsafeOptInUsageError")
    override fun startScan(
        cameraPreviewView: Any,
        onSuccess: (mrzRawText: String) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        val previewView = cameraPreviewView as? PreviewView ?: run {
            onFailure(IllegalArgumentException("cameraPreviewView must be an instance of PreviewView"))
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // Preview setup
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                // ImageAnalysis setup
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImage(imageProxy, onSuccess, onFailure)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Bind to lifecycle
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

            } catch (e: Exception) {
                onFailure(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun stopScan() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImage(
        imageProxy: ImageProxy,
        onSuccess: (mrzRawText: String) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val mrz = extractMrz(visionText)
                    if (mrz != null) {
                        onSuccess(mrz)
                        stopScan() // Stop scanning immediately after successful extraction
                    }
                }
                .addOnFailureListener {
                    // Ignore OCR failures for individual frames to keep scanning fluent
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    /**
     * OCRテキストブロックからパスポートMRZ（44文字の2行）を抽出する。
     */
    private fun extractMrz(visionText: Text): String? {
        val lines = visionText.textBlocks.flatMap { it.lines }
            .map { it.text.replace(" ", "").replace("\r", "").replace("\n", "") }
        
        // 44 characters containing A-Z, 0-9 and '<'
        val mrzPattern = Regex("^[A-Z0-9<]{44}$")

        for (i in 0 until lines.size - 1) {
            val line1 = lines[i].uppercase()
            val line2 = lines[i + 1].uppercase()

            if (mrzPattern.matches(line1) && mrzPattern.matches(line2)) {
                if (line1.startsWith("P<")) {
                    return "$line1\n$line2"
                }
            }
        }
        return null
    }
}
