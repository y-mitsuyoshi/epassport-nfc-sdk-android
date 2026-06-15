package com.example.epassport.server.service

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.springframework.stereotype.Service
import java.security.PrivateKey
import java.security.Security
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * サーバー側で JWE 形式の E2EE データを復号するサービス。
 *
 * クライアント SDK の [com.example.epassport.data.security.E2EECipher] と対になる。
 */
@Service
class E2EEDecryptionService {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * JWE Compact Serialization 風の文字列を秘密鍵で復号する。
     *
     * @param jwe JWE 文字列（header.encryptedKey.iv.ciphertext.tag）
     * @param privateKey RSA 秘密鍵
     * @return 平文バイト列
     */
    fun decrypt(jwe: String, privateKey: PrivateKey): ByteArray {
        val parts = jwe.split(".")
        require(parts.size == 5) { "Invalid JWE format: expected 5 parts, got ${parts.size}" }

        val encryptedKey = base64UrlDecode(parts[1])
        val iv = base64UrlDecode(parts[2])
        val ciphertext = base64UrlDecode(parts[3])
        val authTag = base64UrlDecode(parts[4])

        val keyCipher = Cipher.getInstance("RSA/ECB/OAEPwithSHA256andMGF1Padding", "BC")
        keyCipher.init(Cipher.DECRYPT_MODE, privateKey)
        val aesKey = keyCipher.doFinal(encryptedKey)

        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(aesKey, "AES"),
                GCMParameterSpec(128, iv)
            )
            cipher.doFinal(ciphertext + authTag)
        } finally {
            aesKey.fill(0)
        }
    }

    private fun base64UrlDecode(data: String): ByteArray {
        return Base64.getUrlDecoder().decode(data)
    }
}
