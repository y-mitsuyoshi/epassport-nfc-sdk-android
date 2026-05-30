package com.example.epassport.ocr.ai

import com.example.epassport.ocr.ai.openai.OpenAiOcrClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiOcrClientFactoryTest {

    @Test(expected = IllegalArgumentException::class)
    fun `create throws for unknown vendor`() {
        val config = AiOcrConfig(vendor = "unknown", apiKey = "key")
        AiOcrClientFactory.create(config)
    }

    @Test
    fun `create returns OpenAiOcrClient for openai vendor`() {
        val config = AiOcrConfig(vendor = "openai", apiKey = "key")
        val client = AiOcrClientFactory.create(config)
        assertTrue(client is OpenAiOcrClient)
    }

    @Test
    fun `create returns FallbackAiOcrClient when fallbackConfig is set`() {
        val primaryConfig = AiOcrConfig(vendor = "openai", apiKey = "key1")
        val fallbackConfig = AiOcrConfig(vendor = "openai", apiKey = "key2")
        val config = primaryConfig.copy(fallbackConfig = fallbackConfig)

        val client = AiOcrClientFactory.create(config)
        assertTrue(client is FallbackAiOcrClient)
    }

    @Test
    fun `create normalizes vendor name to lowercase`() {
        val config = AiOcrConfig(vendor = "OPENAI", apiKey = "key")
        val client = AiOcrClientFactory.create(config)
        assertTrue(client is OpenAiOcrClient)
    }

    @Test
    fun `AiOcrConfig validates blank vendor`() {
        try {
            AiOcrConfig(vendor = "", apiKey = "key")
        } catch (e: IllegalArgumentException) {
            assertEquals("vendor must not be blank", e.message)
        }
    }

    @Test
    fun `AiOcrConfig validates when both apiKey and provider are missing`() {
        try {
            AiOcrConfig(vendor = "openai", apiKey = "", apiKeyProvider = null)
        } catch (e: IllegalArgumentException) {
            assertEquals("Either apiKey or apiKeyProvider must be provided", e.message)
        }
    }

    @Test
    fun `AiOcrConfig accepts blank apiKey when provider is set`() {
        val provider = SecureApiKeyProvider { "provided-key" }
        val config = AiOcrConfig(vendor = "openai", apiKey = "", apiKeyProvider = provider)
        assertEquals("provided-key", runBlockingCompat { config.resolveApiKey() })
    }

    @Test
    fun `AiOcrConfig resolves apiKey string when no provider`() {
        val config = AiOcrConfig(vendor = "openai", apiKey = "static-key")
        assertEquals("static-key", runBlockingCompat { config.resolveApiKey() })
    }

    @Test
    fun `AiOcrConfig provider takes precedence over apiKey string`() {
        val provider = SecureApiKeyProvider { "provider-key" }
        val config = AiOcrConfig(vendor = "openai", apiKey = "static-key", apiKeyProvider = provider)
        assertEquals("provider-key", runBlockingCompat { config.resolveApiKey() })
    }

    @Test
    fun `AiOcrConfig forLocalTesting factory creates config with apiKey`() {
        val config = AiOcrConfig.forLocalTesting(
            vendor = "openai",
            apiKey = "test-key",
            model = "gpt-4o-mini"
        )
        assertEquals("openai", config.vendor)
        assertEquals("test-key", config.apiKey)
        assertEquals("gpt-4o-mini", config.model)
        assertEquals(null, config.apiKeyProvider)
    }

    private fun <T> runBlockingCompat(block: suspend () -> T): T {
        return kotlinx.coroutines.runBlocking { block() }
    }
}
