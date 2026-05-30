package com.example.epassport.ocr.ai

import com.example.epassport.ocr.ai.bedrock.BedrockOcrClient
import com.example.epassport.ocr.ai.openai.OpenAiOcrClient
import com.example.epassport.ocr.ai.studio.GoogleAiStudioOcrClient
import com.example.epassport.ocr.ai.vertex.VertexAiOcrClient

/**
 * [AiOcrConfig] に基づいて適切な [AiOcrClient] インスタンスを生成するファクトリ。
 *
 * fallbackConfig が指定されている場合は [FallbackAiOcrClient] をラップして返す。
 */
object AiOcrClientFactory {

    /**
     * 設定に応じた [AiOcrClient] を生成する。
     *
     * @param config AI OCR設定
     * @return 設定に対応するクライアントインスタンス
     * @throws IllegalArgumentException 未対応のベンダーが指定された場合
     */
    fun create(config: AiOcrConfig): AiOcrClient {
        val primary = createSingle(config)
        return if (config.fallbackConfig != null) {
            FallbackAiOcrClient(primary, create(config.fallbackConfig))
        } else {
            primary
        }
    }

    private fun createSingle(config: AiOcrConfig): AiOcrClient {
        return when (config.vendor.lowercase()) {
            "openai" -> OpenAiOcrClient(config)
            "vertex" -> VertexAiOcrClient(config)
            "bedrock" -> BedrockOcrClient(config)
            "google_ai_studio" -> GoogleAiStudioOcrClient(config)
            else -> throw IllegalArgumentException(
                "Unknown AI OCR vendor: '${config.vendor}'. " +
                "Supported: openai, vertex, bedrock, google_ai_studio"
            )
        }
    }
}
