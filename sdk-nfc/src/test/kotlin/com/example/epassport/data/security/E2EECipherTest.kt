package com.example.epassport.data.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Security
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class E2EECipherTest {

    @Before
    fun setUp() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    @Test
    fun encrypt_thenDecrypt_returnsPlaintext() {
        val plaintext = "sensitive passport data".toByteArray(Charsets.UTF_8)
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val jwe = E2EECipher.encrypt(plaintext, keyPair.public)

        val parts = jwe.split(".")
        assertEquals(5, parts.size)

        val header = Base64.getUrlDecoder().decode(parts[0])
        val encryptedKey = Base64.getUrlDecoder().decode(parts[1])
        val iv = Base64.getUrlDecoder().decode(parts[2])
        val ciphertext = Base64.getUrlDecoder().decode(parts[3])
        val authTag = Base64.getUrlDecoder().decode(parts[4])

        // Decrypt AES key with RSA private key
        val keyCipher = Cipher.getInstance("RSA/ECB/OAEPwithSHA256andMGF1Padding", "BC")
        keyCipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        val aesKey = keyCipher.doFinal(encryptedKey)

        // Decrypt plaintext with AES-GCM
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding", "BC")
        aesCipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        val decrypted = aesCipher.doFinal(ciphertext + authTag)

        assertArrayEquals(plaintext, decrypted)
        assertTrue(String(header, Charsets.UTF_8).contains("RSA-OAEP-256"))
        assertTrue(String(header, Charsets.UTF_8).contains("A256GCM"))

        aesKey.fill(0)
    }

    @Test
    fun encrypt_differentRuns_produceDifferentJwe() {
        val plaintext = "sensitive passport data".toByteArray(Charsets.UTF_8)
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

        val jwe1 = E2EECipher.encrypt(plaintext, keyPair.public)
        val jwe2 = E2EECipher.encrypt(plaintext, keyPair.public)

        assertTrue("JWE must use random IV/key so ciphertext differs", jwe1 != jwe2)
    }
}
