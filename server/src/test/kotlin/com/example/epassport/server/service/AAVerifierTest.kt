package com.example.epassport.server.service

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature

class AAVerifierTest {

    @BeforeEach
    fun setUp() {
        Security.addProvider(BouncyCastleProvider())
    }

    @Test
    fun verify_withValidRsaSignature_returnsTrue() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val challenge = byteArrayOf(0x01, 0x02, 0x03)
        val signer = Signature.getInstance("SHA1withRSA", "BC")
        signer.initSign(keyPair.private)
        signer.update(challenge)
        val signature = signer.sign()

        val data = AAVerifier.ActiveAuthenticationData(
            publicKeyInfo = keyPair.public.encoded,
            challenge = challenge,
            signature = signature
        )

        assertTrue(AAVerifier.verify(data))
    }

    @Test
    fun verify_withInvalidSignature_returnsFalse() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val data = AAVerifier.ActiveAuthenticationData(
            publicKeyInfo = keyPair.public.encoded,
            challenge = byteArrayOf(0x01, 0x02, 0x03),
            signature = byteArrayOf(0xFF.toByte())
        )

        assertFalse(AAVerifier.verify(data))
    }
}
