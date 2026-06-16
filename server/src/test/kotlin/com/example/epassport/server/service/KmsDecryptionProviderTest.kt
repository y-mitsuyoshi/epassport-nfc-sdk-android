package com.example.epassport.server.service

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Security
import javax.crypto.Cipher

class KmsDecryptionProviderTest {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Test
    fun testMockKmsDecryption() {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC")
        keyPairGenerator.initialize(2048)
        val keyPair = keyPairGenerator.generateKeyPair()

        val mockProvider = MockKmsDecryptionProvider(keyPair.private)

        val secretMessage = "MySuperSecretSymmetricKey123".toByteArray()

        // Encrypt using RSA-OAEP
        val cipher = Cipher.getInstance("RSA/ECB/OAEPwithSHA256andMGF1Padding", "BC")
        cipher.init(Cipher.ENCRYPT_MODE, keyPair.public)
        val ciphertext = cipher.doFinal(secretMessage)

        // Decrypt using MockKmsDecryptionProvider
        val decrypted = mockProvider.decryptRsaOaep(ciphertext)

        assertArrayEquals(secretMessage, decrypted)
    }
}
