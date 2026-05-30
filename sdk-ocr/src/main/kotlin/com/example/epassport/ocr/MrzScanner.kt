package com.example.epassport.ocr

import androidx.camera.view.PreviewView

/**
 * パスポートの MRZ (Machine Readable Zone) をカメラ映像から読み取るスキャナー。
 *
 * ICAO Doc 9303 の OCR-B 規格フォントをローカルで読み取るため、
 * Google ML Kit (Text Recognition) などのオンデバイス OCR 統合を推奨します。
 */
interface MrzScanner {

    /**
     * カメラのスキャンセッションを開始し、MRZを検知したら結果を返却します。
     *
     * @param cameraPreviewView カメラ映像を投影する [PreviewView] (ホストアプリが提供)
     * @param onSuccess MRZ文字列の読み取りに成功した際のコールバック
     * @param onFailure エラー発生時（カメラ権限がない等）のコールバック
     */
    fun startScan(
        cameraPreviewView: PreviewView,
        onSuccess: (mrzRawText: String) -> Unit,
        onFailure: (exception: Exception) -> Unit
    )

    /**
     * スキャンセッションを一時停止し、カメラなどのリソースを安全に解放します。
     * 再度 [startScan] を呼び出すことでスキャンを再開できます。
     */
    fun stopScan()

    /**
     * このスキャナーインスタンスを完全に破棄し、ネイティブリソース（TextRecognizer等）を解放します。
     * Activity/Fragment の onDestroy() で必ず呼び出してください。
     */
    fun release()

    /**
     * 現在のカメラプレビューからフレームをキャプチャし、OCR解析（静止画撮影）を実行します。
     */
    fun triggerCapture()
}

