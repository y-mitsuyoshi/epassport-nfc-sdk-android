package com.example.epassport.ocr.ai

/**
 * AI OCR認識結果を表すsealed class。
 */
sealed class AiOcrResult {

    /**
     * 認識成功時の結果。
     *
     * @param rawText AIから返された生テキスト（MRZを含む可能性がある）
     */
    data class Success(val rawText: String) : AiOcrResult()

    /**
     * 認識失敗時の結果。
     *
     * @param error 発生した例外またはエラーメッセージ
     */
    data class Failure(val error: Throwable) : AiOcrResult()
}
