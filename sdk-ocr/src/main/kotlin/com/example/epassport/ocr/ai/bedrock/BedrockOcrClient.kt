package com.example.epassport.ocr.ai.bedrock

import com.example.epassport.ocr.ai.AiOcrClient
import com.example.epassport.ocr.ai.AiOcrConfig
import com.example.epassport.ocr.ai.AiOcrResult

/**
 * Amazon Bedrock（Claude, Titan等）を使用したAI OCRクライアントのスタブ実装。
 *
 * 完全な実装時には AWS Signature Version 4 で署名したリクエストを
 * Bedrock Runtime エンドポイントに送信する必要がある:
 * https://bedrock-runtime.{region}.amazonaws.com/model/{modelId}/invoke
 *
 * TODO: AWS SDK for Kotlin または OkHttp + 自前署名 で実装する。
 */
class BedrockOcrClient(private val config: AiOcrConfig) : AiOcrClient {

    override suspend fun recognize(imageBytes: ByteArray): AiOcrResult {
        // TODO: Amazon Bedrock Runtime API へのリクエスト実装
        return AiOcrResult.Failure(
            NotImplementedError("BedrockOcrClient is not yet fully implemented. " +
                "Configure endpoint with 'bedrock' vendor to use Amazon Bedrock.")
        )
    }
}
