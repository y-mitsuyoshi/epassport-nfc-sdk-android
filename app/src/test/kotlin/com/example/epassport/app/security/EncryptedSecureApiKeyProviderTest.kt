package com.example.epassport.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class EncryptedSecureApiKeyProviderTest {

    private fun createProvider(): EncryptedSecureApiKeyProvider {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return EncryptedSecureApiKeyProvider(context, prefs)
    }

    @Test
    fun storeAndProvideApiKey_roundtrips() { runBlocking {
        val provider = createProvider()
        provider.storeApiKey("test-api-key")

        val result = provider.provide()

        assertEquals("test-api-key", result)
    } }

    @Test
    fun clearApiKey_removesStoredKey() { runBlocking {
        val provider = createProvider()
        provider.storeApiKey("test-api-key")
        provider.clearApiKey()

        val result = provider.provide()

        assertTrue(result.isEmpty())
    } }
}
