package com.example.epassport.ocr

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX と Google ML Kit を使用したオンデバイス（ローカル完結型）MRZスキャナーの実装。
 *
 * 修正済みの問題:
 * - cameraExecutor を stopScan() で shutdown しないことでクラッシュを防止（再スキャン対応）
 * - AtomicBoolean で onSuccess の重複呼び出しを防止（レースコンディション解消）
 * - applicationContext を使用することで Activity のメモリリークを防止
 * - release() メソッドで TextRecognizer のネイティブリソースを確実に解放
 */
class CameraMrzScanner(
    context: Context,
    private val lifecycleOwner: LifecycleOwner
) : MrzScanner {

    // ApplicationContext を保持することで Activity のメモリリークを防止
    private val appContext: Context = context.applicationContext

    // executor は stopScan() では shutdown しない（再スキャン可能にするため）
    // release() でのみ shutdown する
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())
    private var cameraProvider: ProcessCameraProvider? = null

    // 重複コールバック防止フラグ（複数フレームが同時に成功しないよう AtomicBoolean で保護）
    private val hasDetected = AtomicBoolean(false)

    @SuppressLint("UnsafeOptInUsageError")
    override fun startScan(
        cameraPreviewView: PreviewView,
        onSuccess: (mrzRawText: String) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
        // 新しいスキャンセッション開始時に検知フラグをリセット
        hasDetected.set(false)

        val cameraProviderFuture = ProcessCameraProvider.getInstance(appContext)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(cameraPreviewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImage(imageProxy, onSuccess)
                }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

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
        }, ContextCompat.getMainExecutor(appContext))
    }

    override fun stopScan() {
        // cameraProvider のバインドを解除するのみ。executor は shutdown しない（再利用のため）
        cameraProvider?.unbindAll()
    }

    override fun release() {
        // Activity/Fragment の onDestroy() で呼び出す
        // ここでのみ executor を終了し、TextRecognizer のネイティブリソースを解放する
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        recognizer.close()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImage(
        imageProxy: ImageProxy,
        onSuccess: (mrzRawText: String) -> Unit
    ) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val mrz = extractMrz(visionText)
                    // AtomicBoolean で「最初に検知した1フレームのみ」処理する（重複防止）
                    if (mrz != null && hasDetected.compareAndSet(false, true)) {
                        stopScan()
                        onSuccess(mrz)
                    }
                }
                .addOnFailureListener { e ->
                    // 個別フレームの OCR エラーはスキップしてスキャンを続行する。
                    // モデル未ダウンロード等の永続障害検知のため警告ベルブでログ出力する（PII を含まないため安全）
                    Log.w("CameraMrzScanner", "ML Kit OCR frame error: ${e.message}")
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
     * ICAO OCR-B フォーマット: `P<` で始まる 44 文字の行が2連続で並ぶ。
     */
    private fun extractMrz(visionText: Text): String? {
        val lines = visionText.textBlocks.flatMap { it.lines }
            .map { it.text.replace(" ", "").replace("\r", "").replace("\n", "") }

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
