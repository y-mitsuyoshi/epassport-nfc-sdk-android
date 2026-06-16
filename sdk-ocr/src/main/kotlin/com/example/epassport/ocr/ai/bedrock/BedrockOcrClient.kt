package com.example.epassport.ocr.ai.bedrock

import android.util.Base64
import com.example.epassport.ocr.ai.AiOcrClient
import com.example.epassport.ocr.ai.AiOcrConfig
import com.example.epassport.ocr.ai.AiOcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Amazon Bedrock（Claude等）を使用した AI OCR クライアント。
 */
class BedrockOcrClient(private val config: AiOcrConfig) : AiOcrClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .writeTimeout(config.timeoutMs, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun recognize(imageBytes: ByteArray): AiOcrResult {
        return withContext(Dispatchers.IO) {
            try {
                val base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                val requestBody = buildRequestBody(base64Image)

                val apiKey = config.resolveApiKey() // API Key or token (if proxy is used)
                val modelId = config.model ?: "anthropic.claude-3-sonnet-20240229-v1:0"
                val region = "us-east-1"
                val endpointUrl = config.endpoint
                    ?: "https://bedrock-runtime.$region.amazonaws.com/model/$modelId/invoke"

                val requestBuilder = Request.Builder()
                    .url(endpointUrl)
                    .header("Content-Type", "application/json")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))

                if (apiKey.isNotBlank()) {
                    requestBuilder.header("X-Api-Key", apiKey)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        return@use AiOcrResult.Failure(
                            RuntimeException("Amazon Bedrock API error ${response.code}: $errorBody")
                        )
                    }

                    val responseBody = response.body?.string()
                        ?: return@use AiOcrResult.Failure(RuntimeException("Empty response body"))

                    // Parse Claude response
                    val parsed = json.parseToJsonElement(responseBody).jsonObject
                    val contentArray = parsed["content"]?.jsonArray
                    val contentText = contentArray?.getOrNull(0)
                        ?.jsonObject?.get("text")
                        ?.jsonPrimitive?.content

                    if (contentText.isNullOrBlank()) {
                        AiOcrResult.Failure(RuntimeException("No content in Amazon Bedrock response"))
                    } else {
                        AiOcrResult.Success(contentText)
                    }
                }
            } catch (e: Exception) {
                AiOcrResult.Failure(e)
            }
        }
    }

    private fun buildRequestBody(base64Image: String): String {
        return buildJsonObject {
            put("anthropic_version", "bedrock-2023-05-31")
            put("max_tokens", 1000)
            put("temperature", 0.0)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", base64Image)
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", MRZ_PROMPT)
                        }
                    }
                }
            }
        }.toString()
    }

    companion object {
        private val MRZ_PROMPT = """
            あなたは高品質なパスポートMRZ（Machine Readable Zone）認識エンジンです。
            提供されたパスポート画像から、下部にある2行または3行のMRZテキストを正確に読み取ってください。
            
            【厳守ルール】
            1. MRZの各行（通常30, 36, 44文字）のテキストのみを出力してください。
            2. 出力する文字列に、マークダウンのコードブロック（```）や説明文、挨拶、ヘッダーなどは一切含めないでください。
            3. 使用文字は大文字（A-Z）、数字（0-9）、および「<」のみです。
            4. 空白（スペース）が含まれている場合は除去して詰めてください。
        """.trimIndent()
    }
}
