package com.example.epassport.ocr

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
     * @param cameraPreviewView カメラ映像を投影するプレビュー領域 (ホストアプリが提供)
     * @param onSuccess MRZ文字列の読み取りに成功した際のコールバック
     * @param onFailure エラー発生時（カメラ権限がない等）のコールバック
     */
    fun startScan(
        cameraPreviewView: Any, // 実装時に具体的な View タイプに置き換えます
        onSuccess: (mrzRawText: String) -> Unit,
        onFailure: (exception: Exception) -> Unit
    )

    /**
     * スキャンセッションを終了し、カメラなどのリソースを安全に解放します。
     */
    fun stopScan()
}
