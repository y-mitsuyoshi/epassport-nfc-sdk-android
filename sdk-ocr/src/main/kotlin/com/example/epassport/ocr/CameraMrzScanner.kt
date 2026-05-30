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
import com.example.epassport.ocr.ai.AiOcrClient
import com.example.epassport.ocr.ai.AiOcrResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX と クラウドAI OCR を使用したMRZスキャナーの実装。
 *
 * ## 動作フロー
 * 1. CameraX でカメラ映像をリアルタイムプレビュー
 * 2. 一定間隔（デフォルト800ms）ごとにフレームをキャプチャ
 * 3. JPEG変換後、クラウドAI OCR（OpenAI/Vertex/Bedrock等）に画像を送信
 * 4. AIから返されたテキストを MrzExtractor で解析
 * 5. MRZを検出できれば onSuccess を呼び出し、カメラを停止
 *
 * ## セキュリティ
 * APIキーは [AiOcrConfig.apiKeyProvider] を通じて動的に取得することを推奨。
 * BuildConfig/localProperties への直接埋め込みは避けること。
 *
 * @param aiOcrClient AI OCRクライアント（必須）。AiOcrClientFactory で生成する。
 * @param captureIntervalMs フレームキャプチャ間隔（ミリ秒）。連続API呼び出しを防ぐ。
 */
class CameraMrzScanner(
    context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val aiOcrClient: AiOcrClient,
    private val captureIntervalMs: Long = 800L
) : MrzScanner {

    private val appContext: Context = context.applicationContext
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    private val hasDetected = AtomicBoolean(false)
    private val aiOcrScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val captureRequested = AtomicBoolean(false)

    override fun triggerCapture() {
        captureRequested.set(true)
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun startScan(
        cameraPreviewView: PreviewView,
        onSuccess: (mrzRawText: String) -> Unit,
        onFailure: (exception: Exception) -> Unit
    ) {
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
                    .setTargetResolution(android.util.Size(1280, 720))
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    processImageWithAiOcr(imageProxy, onSuccess)
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
        cameraProvider?.unbindAll()
    }

    override fun release() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        aiOcrScope.cancel()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageWithAiOcr(
        imageProxy: ImageProxy,
        onSuccess: (mrzRawText: String) -> Unit
    ) {
        // ユーザーがシャッター（撮影ボタン）を押していなければ無視する
        if (!captureRequested.compareAndSet(true, false)) {
            imageProxy.close()
            return
        }

        // 既に検出済みなら無視
        if (hasDetected.get()) {
            imageProxy.close()
            return
        }

        // ImageProxy → JPEG変換（close前にByteArrayをコピー）
        val jpegBytes = try {
            ImageProxyUtils.toJpegByteArray(imageProxy)
        } catch (e: Exception) {
            Log.w("CameraMrzScanner", "Image conversion failed: ${e.message}")
            imageProxy.close()
            return
        }
        imageProxy.close()

        // クラウドAI OCRへ非同期送信
        aiOcrScope.launch {
            try {
                when (val result = aiOcrClient.recognize(jpegBytes)) {
                    is AiOcrResult.Success -> {
                        val mrz = extractMrzFromAiText(result.rawText)
                        if (mrz != null && hasDetected.compareAndSet(false, true)) {
                            withContext(Dispatchers.Main) {
                                stopScan()
                                onSuccess(mrz)
                            }
                        }
                    }
                    is AiOcrResult.Failure -> {
                        Log.w("CameraMrzScanner", "AI OCR failed: ${result.error.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w("CameraMrzScanner", "AI OCR exception: ${e.message}")
            }
        }
    }

    /**
     * AI OCRから返された自由形式テキストから、MRZを抽出する。
     */
    private fun extractMrzFromAiText(rawText: String): String? {
        val normalizedLines = rawText
            .lines()
            .map { line ->
                line.trim()
                    .replace(" ", "")
                    .replace("\r", "")
                    .replace(Regex("[\\(\\)\\{\\}\\[\\]«»]"), "<")
                    .uppercase()
            }
            .filter { it.isNotBlank() }

        return MrzExtractor.extractFromLines(normalizedLines)
    }
}
