package com.example.epassport.ocr.ai.studio

import com.example.epassport.ocr.ai.AiOcrClient
import com.example.epassport.ocr.ai.AiOcrConfig
import com.example.epassport.ocr.ai.AiOcrResult

/**
 * Google AI Studio（Gemini API）を使用したAI OCRクライアントのスタブ実装。
 *
 * 完全な実装時には以下のエンドポイントを使用する:
 * https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent?key={API_KEY}
 *
 * TODO: Gemini REST API へのリクエスト実装。
 *       parts[] に { inlineData: { mimeType: "image/jpeg", data: "BASE64" } } を含める。
 */
class GoogleAiStudioOcrClient(private val config: AiOcrConfig) : AiOcrClient {

    override suspend fun recognize(imageBytes: ByteArray): AiOcrResult {
        // TODO: Google AI Studio Gemini API へのリクエスト実装
        return AiOcrResult.Failure(
            NotImplementedError("GoogleAiStudioOcrClient is not yet fully implemented. " +
                "Configure endpoint with 'google_ai_studio' vendor to use Google AI Studio.")
        )
    }
}
