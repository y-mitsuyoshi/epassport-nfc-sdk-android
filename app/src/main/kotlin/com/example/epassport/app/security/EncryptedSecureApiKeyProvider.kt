package com.example.epassport.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.epassport.ocr.ai.SecureApiKeyProvider

/**
 * [EncryptedSharedPreferences] と Android Keystore を用いて API キーを暗号化保存・取得する
 * [SecureApiKeyProvider] の実装。
 *
 * 平文の API キーを APK 内（BuildConfig 等）に残さず、端末内に暗号化して保持する。
 */
class EncryptedSecureApiKeyProvider(
    context: Context,
    private val prefs: SharedPreferences = createEncryptedPrefs(context)
) : SecureApiKeyProvider {

    companion object {
        private const val PREFS_FILE = "secure_api_keys"
        private const val KEY_AI_OCR_API_KEY = "ai_ocr_api_key"

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            return EncryptedSharedPreferences.create(
                PREFS_FILE,
                masterKeyAlias,
                context.applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    override suspend fun provide(): String {
        return prefs.getString(KEY_AI_OCR_API_KEY, "") ?: ""
    }

    /**
     * API キーを暗号化して保存する。
     *
     * @param apiKey 保存する API キー。空文字列の場合は既存値を削除する。
     */
    fun storeApiKey(apiKey: String) {
        prefs.edit().apply {
            if (apiKey.isBlank()) {
                remove(KEY_AI_OCR_API_KEY)
            } else {
                putString(KEY_AI_OCR_API_KEY, apiKey)
            }
            apply()
        }
    }

    /**
     * 保存されている API キーを削除する。
     */
    fun clearApiKey() {
        prefs.edit().remove(KEY_AI_OCR_API_KEY).apply()
    }
}
