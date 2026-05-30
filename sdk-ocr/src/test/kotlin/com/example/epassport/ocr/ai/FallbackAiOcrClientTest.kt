package com.example.epassport.ocr.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackAiOcrClientTest {

    private fun <T> runBlockingCompat(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }

    @Test
    fun `primary success does not call fallback`() {
        val primary = FakeAiOcrClient(AiOcrResult.Success("primary"))
        val fallback = FakeAiOcrClient(AiOcrResult.Success("fallback"))
        val client = FallbackAiOcrClient(primary, fallback)

        val result = runBlockingCompat { client.recognize(byteArrayOf()) }

        assertTrue(result is AiOcrResult.Success)
        assertEquals("primary", (result as AiOcrResult.Success).rawText)
        assertEquals(1, primary.callCount)
        assertEquals(0, fallback.callCount)
    }

    @Test
    fun `primary failure calls fallback and returns fallback result`() {
        val primary = FakeAiOcrClient(AiOcrResult.Failure(RuntimeException("primary error")))
        val fallback = FakeAiOcrClient(AiOcrResult.Success("fallback"))
        val client = FallbackAiOcrClient(primary, fallback)

        val result = runBlockingCompat { client.recognize(byteArrayOf()) }

        assertTrue(result is AiOcrResult.Success)
        assertEquals("fallback", (result as AiOcrResult.Success).rawText)
        assertEquals(1, primary.callCount)
        assertEquals(1, fallback.callCount)
    }

    @Test
    fun `both failure returns fallback failure`() {
        val primary = FakeAiOcrClient(AiOcrResult.Failure(RuntimeException("primary error")))
        val fallback = FakeAiOcrClient(AiOcrResult.Failure(RuntimeException("fallback error")))
        val client = FallbackAiOcrClient(primary, fallback)

        val result = runBlockingCompat { client.recognize(byteArrayOf()) }

        assertTrue(result is AiOcrResult.Failure)
        assertEquals("fallback error", (result as AiOcrResult.Failure).error.message)
    }

    private class FakeAiOcrClient(
        private val fixedResult: AiOcrResult
    ) : AiOcrClient {
        var callCount = 0

        override suspend fun recognize(imageBytes: ByteArray): AiOcrResult {
            callCount++
            return fixedResult
        }
    }
}
