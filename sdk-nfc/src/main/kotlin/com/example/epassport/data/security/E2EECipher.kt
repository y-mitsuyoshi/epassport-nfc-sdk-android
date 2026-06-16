package com.example.epassport.data.security

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * サーバー公開鍵を用いたハイブリッド暗号化（E2EE）ユーティリティ。
 *
 * 平文データを AES-256-GCM で暗号化し、内容鍵を RSA-OAEP-SHA256 でサーバー公開鍵で暗号化する。
 * 結果は JWE Compact Serialization 風の文字列として返す。
 */
@OptIn(ExperimentalEncodingApi::class)
object E2EECipher {

    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // BouncyCastleProvider instance to avoid deprecated "BC" string provider on Android P+
    private val bcProvider: BouncyCastleProvider = BouncyCastleProvider().also {
        if (Security.getProvider(it.name) == null) Security.addProvider(it)
    }

    /**
     * 平文データをサーバー公開鍵で暗号化する。
     *
     * @param plaintext 暗号化する平文データ
     * @param serverPublicKey サーバーの RSA 公開鍵
     * @return JWE Compact Serialization 風の文字列
     */
    fun encrypt(plaintext: ByteArray, serverPublicKey: PublicKey): String {
        // 1. Generate random AES-256 content key
        val aesKey = ByteArray(32)
        SecureRandom().nextBytes(aesKey)

        // 2. Encrypt plaintext with AES-256-GCM
        val iv = ByteArray(GCM_IV_LENGTH).apply { SecureRandom().nextBytes(this) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding", bcProvider)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertextWithTag = cipher.doFinal(plaintext)

        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - 16)
        val authTag = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - 16, ciphertextWithTag.size)

        // 3. Encrypt AES key with RSA-OAEP-SHA256
        val keyCipher = Cipher.getInstance("RSA/ECB/OAEPwithSHA256andMGF1Padding", bcProvider)
        keyCipher.init(Cipher.ENCRYPT_MODE, serverPublicKey)
        val encryptedKey = keyCipher.doFinal(aesKey)

        // 4. Build JWE header (manually to avoid Android org.json in unit tests)
        val header = """{"alg":"RSA-OAEP-256","enc":"A256GCM","typ":"JWT"}""".toByteArray(Charsets.UTF_8)

        // 5. JWE Compact Serialization
        val encodedHeader = base64UrlEncode(header)
        val encodedEncryptedKey = base64UrlEncode(encryptedKey)
        val encodedIv = base64UrlEncode(iv)
        val encodedCiphertext = base64UrlEncode(ciphertext)
        val encodedTag = base64UrlEncode(authTag)

        // Secure cleanup of sensitive material
        aesKey.fill(0)

        return "$encodedHeader.$encodedEncryptedKey.$encodedIv.$encodedCiphertext.$encodedTag"
    }

    /**
     * JWE Compact Serialization 風の文字列をサーバー秘密鍵で復号する。
     *
     * @param jwe JWE Compact Serialization 風の文字列
     * @param serverPrivateKey サーバーの RSA 秘密鍵
     * @return 平文データ
     */
    fun decrypt(jwe: String, serverPrivateKey: PrivateKey): ByteArray {
        val parts = jwe.split(".")
        if (parts.size != 5) {
            throw IllegalArgumentException("Invalid JWE format: expected 5 parts, got ${parts.size}")
        }

        val encryptedKey = base64UrlDecode(parts[1])
        val iv = base64UrlDecode(parts[2])
        val ciphertext = base64UrlDecode(parts[3])
        val authTag = base64UrlDecode(parts[4])

        // 1. Decrypt AES key with RSA-OAEP-SHA256
        val keyCipher = Cipher.getInstance("RSA/ECB/OAEPwithSHA256andMGF1Padding", bcProvider)
        keyCipher.init(Cipher.DECRYPT_MODE, serverPrivateKey)
        val aesKey = keyCipher.doFinal(encryptedKey)

        try {
            // 2. Decrypt ciphertext with AES-256-GCM
            val cipher = Cipher.getInstance("AES/GCM/NoPadding", bcProvider)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(aesKey, "AES"),
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )
            return cipher.doFinal(ciphertext + authTag)
        } finally {
            aesKey.fill(0)
        }
    }

    private fun base64UrlEncode(data: ByteArray): String {
        return Base64.UrlSafe.encode(data)
    }

    private fun base64UrlDecode(data: String): ByteArray {
        return Base64.UrlSafe.decode(data)
    }
}
