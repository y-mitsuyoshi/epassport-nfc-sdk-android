package com.example.epassport.ocr.ai.studio

import android.util.Base64
import com.example.epassport.ocr.ai.AiOcrConfig
import com.example.epassport.ocr.ai.AiOcrResult
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoogleAiStudioOcrClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GoogleAiStudioOcrClient

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg() as ByteArray)
        }

        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString()
        val config = AiOcrConfig(
            vendor = "google_ai_studio",
            apiKey = "test-api-key",
            endpoint = "${baseUrl}v1beta/models/{MODEL}:generateContent?key={API_KEY}"
        )
        client = GoogleAiStudioOcrClient(config)
    }

    @After
    fun tearDown() {
        server.shutdown()
        unmockkStatic(Base64::class)
    }

    @Test
    fun `recognize returns Success when Gemini API responds with valid content`() {
        val mockResponse = MockResponse()
            .setResponseCode(200)
            .setBody(
                """
                {
                    "candidates": [
                        {
                            "content": {
                                "parts": [
                                    {
                                        "text": "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\nL898902C36UTO7408122F1204159ZE184226B<<<<<14"
                                    }
                                ],
                                "role": "model"
                            },
                            "finishReason": "STOP"
                        }
                    ]
                }
                """.trimIndent()
            )
        server.enqueue(mockResponse)

        val result = runBlockingCompat { client.recognize(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())) }

        assertTrue(result is AiOcrResult.Success)
        assertEquals(
            "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\nL898902C36UTO7408122F1204159ZE184226B<<<<<14",
            (result as AiOcrResult.Success).rawText
        )

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertTrue(request.path!!.contains("gemini-1.5-flash"))
        assertTrue(request.path!!.contains("test-api-key"))
    }

    @Test
    fun `recognize returns Failure when Gemini API responds with error`() {
        val mockResponse = MockResponse()
            .setResponseCode(400)
            .setBody("{\"error\": \"Invalid API key\"}")
        server.enqueue(mockResponse)

        val result = runBlockingCompat { client.recognize(byteArrayOf()) }

        assertTrue(result is AiOcrResult.Failure)
        assertTrue((result as AiOcrResult.Failure).error.message!!.contains("400"))
    }

    @Test
    fun `recognize returns Failure on network exception`() {
        server.shutdown()

        val result = runBlockingCompat { client.recognize(byteArrayOf()) }

        assertTrue(result is AiOcrResult.Failure)
    }

    private fun <T> runBlockingCompat(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }
}
