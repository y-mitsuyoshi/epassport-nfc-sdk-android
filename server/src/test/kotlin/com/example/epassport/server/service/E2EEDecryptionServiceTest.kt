package com.example.epassport.server.service

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

class E2EEDecryptionServiceTest {

    private lateinit var service: E2EEDecryptionService

    @BeforeEach
    fun setUp() {
        Security.addProvider(BouncyCastleProvider())
        service = E2EEDecryptionService()
    }

    @Test
    fun decrypt_validJwe_returnsPlaintext() {
        val plaintext = "sensitive passport data".toByteArray(Charsets.UTF_8)
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val jwe = createJwe(plaintext, keyPair.public)
        val decrypted = service.decrypt(jwe, keyPair.private)

        assertArrayEquals(plaintext, decrypted)
    }

    private fun createJwe(plaintext: ByteArray, publicKey: java.security.PublicKey): String {
        val aesKey = ByteArray(32).apply { java.security.SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { java.security.SecureRandom().nextBytes(this) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        val ciphertextWithTag = cipher.doFinal(plaintext)
        val ciphertext = ciphertextWithTag.copyOfRange(0, ciphertextWithTag.size - 16)
        val tag = ciphertextWithTag.copyOfRange(ciphertextWithTag.size - 16, ciphertextWithTag.size)

        val keyCipher = Cipher.getInstance("RSA/ECB/OAEPwithSHA256andMGF1Padding", "BC")
        keyCipher.init(Cipher.ENCRYPT_MODE, publicKey)
        val encryptedKey = keyCipher.doFinal(aesKey)

        val header = """{"alg":"RSA-OAEP-256","enc":"A256GCM","typ":"JWT"}""".toByteArray(Charsets.UTF_8)
        val encoder = Base64.getUrlEncoder().withoutPadding()

        return listOf(header, encryptedKey, iv, ciphertext, tag)
            .joinToString(".") { encoder.encodeToString(it) }
    }
}
