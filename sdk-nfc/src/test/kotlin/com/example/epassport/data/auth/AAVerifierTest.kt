package com.example.epassport.data.auth

import com.example.epassport.domain.model.ActiveAuthenticationData
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature

class AAVerifierTest {

    @Before
    fun setUp() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    @Test
    fun verify_rsaValidSignature_returnsTrue() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val challenge = ByteArray(8) { it.toByte() }

        val signer = Signature.getInstance("SHA1withRSA", "BC")
        signer.initSign(keyPair.private)
        signer.update(challenge)
        val signature = signer.sign()

        val spki = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        val dg15 = spki.encoded

        val aaData = ActiveAuthenticationData(dg15, challenge, signature)

        assertTrue(AAVerifier.verify(aaData))
    }

    @Test
    fun verify_rsaInvalidSignature_returnsFalse() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val challenge = ByteArray(8) { it.toByte() }
        val wrongSignature = ByteArray(256) { 0xFF.toByte() }

        val spki = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        val dg15 = spki.encoded

        val aaData = ActiveAuthenticationData(dg15, challenge, wrongSignature)

        assertFalse(AAVerifier.verify(aaData))
    }

    @Test
    fun verify_differentChallenge_returnsFalse() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val challenge = ByteArray(8) { it.toByte() }
        val differentChallenge = ByteArray(8) { (it + 1).toByte() }

        val signer = Signature.getInstance("SHA1withRSA", "BC")
        signer.initSign(keyPair.private)
        signer.update(challenge)
        val signature = signer.sign()

        val spki = SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        val dg15 = spki.encoded

        val aaData = ActiveAuthenticationData(dg15, differentChallenge, signature)

        assertFalse(AAVerifier.verify(aaData))
    }
}
