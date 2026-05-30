package com.example.epassport.ocr.ai.openai

import com.example.epassport.ocr.ai.AiOcrConfig
import com.example.epassport.ocr.ai.AiOcrResult
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiOcrClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OpenAiOcrClient

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val config = AiOcrConfig(
            vendor = "openai",
            apiKey = "test-api-key",
            endpoint = server.url("/v1/chat/completions").toString()
        )
        client = OpenAiOcrClient(config)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `recognize returns Success when API responds with valid content`() {
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                {
                    "choices": [
                        {
                            "message": {
                                "content": "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\nL898902C36UTO7408122F1204159ZE184226B<<<<<14"
                            }
                        }
                    ]
                }
                """.trimIndent()
            )
        server.enqueue(mockResponse)

        val result = runBlockingCompat { client.recognize(byteArrayOf(0xFF, 0xD8, 0xFF)) }

        assertTrue(result is AiOcrResult.Success)
        assertEquals(
            "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\nL898902C36UTO7408122F1204159ZE184226B<<<<<14",
            (result as AiOcrResult.Success).rawText
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Authorization")!!.contains("test-api-key"))
    }

    @Test
    fun `recognize returns Failure when API responds with error`() {
        val mockResponse = MockResponse()
            .setResponseCode(429)
            .setBody("{\"error\": \"Rate limited\"}")
        server.enqueue(mockResponse)

        val result = runBlockingCompat { client.recognize(byteArrayOf()) }

        assertTrue(result is AiOcrResult.Failure)
        assertTrue((result as AiOcrResult.Failure).error.message!!.contains("429"))
    }

    @Test
    fun `recognize returns Failure on network exception`() {
        server.shutdown()

        val result = runBlockingCompat { client.recognize(byteArrayOf()) }

        assertTrue(result is AiOcrResult.Failure)
    }

    /**
     * テスト用の簡易 runBlocking。
     * Android環境で kotlinx.coroutines.test の runBlocking が使えない場合の代替。
     */
    private fun <T> runBlockingCompat(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }
}
