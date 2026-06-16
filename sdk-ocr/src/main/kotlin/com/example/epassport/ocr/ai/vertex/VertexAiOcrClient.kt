package com.example.epassport.ocr.ai.vertex

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
 * Google Cloud Vertex AI を使用した AI OCR クライアント。
 */
class VertexAiOcrClient(private val config: AiOcrConfig) : AiOcrClient {

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

                val accessToken = config.resolveApiKey() // OAuth access token
                val model = config.model ?: "gemini-1.5-flash"
                val endpointUrl = config.endpoint
                    ?: "https://us-central1-aiplatform.googleapis.com/v1/projects/YOUR_PROJECT_ID/locations/us-central1/publishers/google/models/$model:generateContent"

                val request = Request.Builder()
                    .url(endpointUrl)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer $accessToken")
                    .post(requestBody.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        return@use AiOcrResult.Failure(
                            RuntimeException("Vertex AI API error ${response.code}: $errorBody")
                        )
                    }

                    val responseBody = response.body?.string()
                        ?: return@use AiOcrResult.Failure(RuntimeException("Empty response body"))

                    val parsed = json.parseToJsonElement(responseBody).jsonObject
                    val candidates = parsed["candidates"]?.jsonArray
                    val content = candidates?.getOrNull(0)
                        ?.jsonObject?.get("content")
                        ?.jsonObject?.get("parts")
                        ?.jsonArray?.getOrNull(0)
                        ?.jsonObject?.get("text")
                        ?.jsonPrimitive?.content

                    if (content.isNullOrBlank()) {
                        AiOcrResult.Failure(RuntimeException("No content in Vertex AI response"))
                    } else {
                        AiOcrResult.Success(content)
                    }
                }
            } catch (e: Exception) {
                AiOcrResult.Failure(e)
            }
        }
    }

    private fun buildRequestBody(base64Image: String): String {
        return buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject {
                            put("text", MRZ_PROMPT)
                        }
                        addJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", "image/jpeg")
                                put("data", base64Image)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.0)
                put("responseMimeType", "text/plain")
            }
        }.toString()
    }

    companion object {
        private val MRZ_PROMPT = """
            あなたは高性能なパスポートMRZ（Machine Readable Zone）認識エンジンです。
            提供されたパスポート画像から、下部にある2行または3行のMRZテキストを正確に読み取ってください。
            
            【厳守ルール】
            1. MRZ의 各行（通常30, 36, 44文字）のテキストのみを出力してください。
            2. 出力する文字列に、マークダウンのコードブロック（```）や説明文、挨拶、ヘッダーなどは一切含めないでください。
            3. 使用文字は大文字（A-Z）、数字（0-9）、および「<」のみです。
            4. 空白（スペース）が含まれている場合は除去して詰めてください。
        """.trimIndent()
    }
}
