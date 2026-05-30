package com.example.epassport.ocr.ai

/**
 * プライマリAI OCRクライアントが失敗した際に、セカンダリクライアントにフォールバックするデコレータ。
 */
class FallbackAiOcrClient(
    private val primary: AiOcrClient,
    private val fallback: AiOcrClient
) : AiOcrClient {

    override suspend fun recognize(imageBytes: ByteArray): AiOcrResult {
        return when (val primaryResult = primary.recognize(imageBytes)) {
            is AiOcrResult.Success -> primaryResult
            is AiOcrResult.Failure -> fallback.recognize(imageBytes)
        }
    }
}
