package com.example.epassport.ocr.ai.vertex

import com.example.epassport.ocr.ai.AiOcrClient
import com.example.epassport.ocr.ai.AiOcrConfig
import com.example.epassport.ocr.ai.AiOcrResult

/**
 * Google Cloud Vertex AI（Gemini / Imagen等）を使用したAI OCRクライアントのスタブ実装。
 *
 * 完全な実装時には以下のエンドポイントを使用する:
 * - Gemini: https://{LOCATION}-aiplatform.googleapis.com/v1/projects/{PROJECT}/locations/{LOCATION}/publishers/google/models/{MODEL}:predict
 * - または Gemini API: https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-vision:generateContent
 *
 * TODO: 実際のAPI呼び出しとレスポンスパースを実装する。
 */
class VertexAiOcrClient(private val config: AiOcrConfig) : AiOcrClient {

    override suspend fun recognize(imageBytes: ByteArray): AiOcrResult {
        // TODO: Vertex AI Gemini API へのリクエスト実装
        return AiOcrResult.Failure(
            NotImplementedError("VertexAiOcrClient is not yet fully implemented. " +
                "Configure endpoint with 'vertex' vendor to use Vertex AI.")
        )
    }
}
