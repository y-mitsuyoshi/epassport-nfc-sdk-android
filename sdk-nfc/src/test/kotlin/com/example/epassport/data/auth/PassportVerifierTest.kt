package com.example.epassport.data.auth

import com.example.epassport.domain.model.ActiveAuthenticationData
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

class PassportVerifierTest {

    private lateinit var cscaKeyPair: KeyPair
    private lateinit var cscaCert: X509Certificate
    private lateinit var aaKeyPair: KeyPair
    private lateinit var verifier: PassportVerifier

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    @Before
    fun setUp() {
        cscaKeyPair = generateRsaKeyPair()
        cscaCert = createSelfSignedCert(
            subject = X500Name("C=JP, O=Government, CN=CSCA Japan"),
            keyPair = cscaKeyPair,
            keyUsage = KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
            isCa = true
        )
        aaKeyPair = generateRsaKeyPair()
        verifier = PassportVerifier()
    }

    @Test
    fun verifyPassiveAuthentication_withValidSodAndHashes_returnsSuccess() {
        val dg1Bytes = byteArrayOf(0x01, 0x02, 0x03)
        val dg2Bytes = byteArrayOf(0x04, 0x05, 0x06)
        val dataGroups = mapOf(1 to dg1Bytes, 2 to dg2Bytes)
        val sod = createSod(dataGroups, cscaKeyPair.private, cscaCert)
        val trustStore = CscaTrustStore().apply { addCertificate(cscaCert) }

        val result = verifier.verifyPassiveAuthentication(sod, dataGroups, trustStore)

        assertTrue(result.success)
        assertEquals("PA", result.stepName)
    }

    @Test
    fun verifyPassiveAuthentication_withEmptyTrustStore_returnsFailure() {
        val dg1Bytes = byteArrayOf(0x01, 0x02, 0x03)
        val dataGroups = mapOf(1 to dg1Bytes)
        val sod = createSod(dataGroups, cscaKeyPair.private, cscaCert)
        val emptyTrustStore = CscaTrustStore()

        val result = verifier.verifyPassiveAuthentication(sod, dataGroups, emptyTrustStore)

        assertFalse(result.success)
        assertEquals("PA", result.stepName)
        assertTrue(result.detail.contains("CSCA trust store is empty", ignoreCase = true))
    }

    @Test
    fun verifyPassiveAuthentication_withInvalidHash_returnsFailure() {
        val dg1Bytes = byteArrayOf(0x01, 0x02, 0x03)
        val correctGroups = mapOf(1 to dg1Bytes)
        val tamperedGroups = mapOf(1 to byteArrayOf(0xFF.toByte()))
        val sod = createSod(correctGroups, cscaKeyPair.private, cscaCert)
        val trustStore = CscaTrustStore().apply { addCertificate(cscaCert) }

        val result = verifier.verifyPassiveAuthentication(sod, tamperedGroups, trustStore)

        assertFalse(result.success)
        assertTrue(result.detail.contains("hash mismatch", ignoreCase = true))
    }

    @Test
    fun verifyPassiveAuthentication_withUntrustedSigner_returnsFailure() {
        val otherKeyPair = generateRsaKeyPair()
        val otherCert = createSelfSignedCert(
            subject = X500Name("C=XX, O=Other, CN=Other CSCA"),
            keyPair = otherKeyPair,
            keyUsage = KeyUsage(KeyUsage.keyCertSign),
            isCa = true
        )
        val sod = createSod(mapOf(1 to byteArrayOf(0x01)), otherKeyPair.private, otherCert)
        val trustStore = CscaTrustStore().apply { addCertificate(cscaCert) }

        val result = verifier.verifyPassiveAuthentication(sod, mapOf(1 to byteArrayOf(0x01)), trustStore)

        assertFalse(result.success)
        assertTrue(result.detail.contains("SOD signature verification failed", ignoreCase = true))
    }

    @Test
    fun verify_withValidPaAndAa_returnsSuccess() {
        val dg1Bytes = byteArrayOf(0x01, 0x02, 0x03)
        val dg2Bytes = byteArrayOf(0x04, 0x05, 0x06)
        val dg15Bytes = aaKeyPair.public.encoded
        val dataGroups = mapOf(1 to dg1Bytes, 2 to dg2Bytes, 15 to dg15Bytes)
        val sod = createSod(dataGroups, cscaKeyPair.private, cscaCert)

        val challenge = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val signature = signWithRsa(aaKeyPair, challenge)
        val aaData = ActiveAuthenticationData(dg15Bytes, challenge, signature)

        val trustStore = CscaTrustStore().apply { addCertificate(cscaCert) }

        val result = verifier.verify(sod, dataGroups, aaData, trustStore)

        assertTrue(result.isSuccessful)
        assertTrue(result.passiveAuthentication.success)
        assertTrue(result.activeAuthentication?.success ?: false)
    }

    @Test
    fun verify_withTamperedDg15_returnsFailure() {
        val dg1Bytes = byteArrayOf(0x01, 0x02, 0x03)
        val dg2Bytes = byteArrayOf(0x04, 0x05, 0x06)
        val realDg15 = aaKeyPair.public.encoded
        val tamperedDg15 = byteArrayOf(0xFF.toByte()) + realDg15.copyOfRange(1, realDg15.size)
        // dataGroups contains the real DG15 so PA passes; aaData contains the tampered DG15
        // so the explicit DG15 hash consistency check before AA fails.
        val dataGroups = mapOf(1 to dg1Bytes, 2 to dg2Bytes, 15 to realDg15)
        val sod = createSod(dataGroups, cscaKeyPair.private, cscaCert)

        val challenge = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val signature = signWithRsa(aaKeyPair, challenge)
        val aaData = ActiveAuthenticationData(tamperedDg15, challenge, signature)

        val trustStore = CscaTrustStore().apply { addCertificate(cscaCert) }

        val result = verifier.verify(sod, dataGroups, aaData, trustStore)

        assertFalse(result.isSuccessful)
        assertTrue(result.passiveAuthentication.success)
        assertNull(result.activeAuthentication)
        assertEquals(
            "AA public key info does not match read DG15 (possible tampering)",
            result.failureReason
        )
    }

    @Test
    fun verify_withAaAndNoDg15_returnsFailure() {
        val dg1Bytes = byteArrayOf(0x01, 0x02, 0x03)
        val dg2Bytes = byteArrayOf(0x04, 0x05, 0x06)
        val dataGroups = mapOf(1 to dg1Bytes, 2 to dg2Bytes)
        val sod = createSod(dataGroups, cscaKeyPair.private, cscaCert)

        val challenge = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val signature = signWithRsa(aaKeyPair, challenge)
        val aaData = ActiveAuthenticationData(aaKeyPair.public.encoded, challenge, signature)

        val trustStore = CscaTrustStore().apply { addCertificate(cscaCert) }

        val result = verifier.verify(sod, dataGroups, aaData, trustStore)

        assertFalse(result.isSuccessful)
        assertTrue(result.passiveAuthentication.success)
        assertNull(result.activeAuthentication)
        assertTrue(result.failureReason?.contains("DG15 not provided", ignoreCase = true) ?: false)
    }

    @Test
    fun verify_withAaOnly_skipsAaAndReturnsSuccess() {
        val dg1Bytes = byteArrayOf(0x01, 0x02, 0x03)
        val dg2Bytes = byteArrayOf(0x04, 0x05, 0x06)
        val dataGroups = mapOf(1 to dg1Bytes, 2 to dg2Bytes)
        val sod = createSod(dataGroups, cscaKeyPair.private, cscaCert)

        val trustStore = CscaTrustStore().apply { addCertificate(cscaCert) }

        val result = verifier.verify(sod, dataGroups, null, trustStore)

        assertTrue(result.isSuccessful)
        assertTrue(result.passiveAuthentication.success)
        assertNull(result.activeAuthentication)
        assertNull(result.failureReason)
    }

    @Test
    fun verify_withAaAndInvalidSignature_returnsFailure() {
        val dg1Bytes = byteArrayOf(0x01, 0x02, 0x03)
        val dg2Bytes = byteArrayOf(0x04, 0x05, 0x06)
        val dg15Bytes = aaKeyPair.public.encoded
        val dataGroups = mapOf(1 to dg1Bytes, 2 to dg2Bytes, 15 to dg15Bytes)
        val sod = createSod(dataGroups, cscaKeyPair.private, cscaCert)

        val challenge = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val badSignature = signWithRsa(aaKeyPair, byteArrayOf(0xFF.toByte(), 0xEE.toByte(), 0xDD.toByte()))
        val aaData = ActiveAuthenticationData(dg15Bytes, challenge, badSignature)

        val trustStore = CscaTrustStore().apply { addCertificate(cscaCert) }

        val result = verifier.verify(sod, dataGroups, aaData, trustStore)

        assertFalse(result.isSuccessful)
        assertTrue(result.passiveAuthentication.success)
        assertNotNull(result.activeAuthentication)
        assertFalse(result.activeAuthentication!!.success)
    }

    private fun generateRsaKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("RSA", "BC")
        generator.initialize(2048)
        return generator.generateKeyPair()
    }

    private fun createSelfSignedCert(
        subject: X500Name,
        keyPair: KeyPair,
        keyUsage: KeyUsage,
        isCa: Boolean
    ): X509Certificate {
        val notBefore = Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
        val notAfter = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
        val serial = BigInteger.valueOf(System.currentTimeMillis())

        val builder = X509v3CertificateBuilder(
            subject,
            serial,
            notBefore,
            notAfter,
            subject,
            SubjectPublicKeyInfo.getInstance(keyPair.public.encoded)
        )
        builder.addExtension(Extension.keyUsage, true, keyUsage)
        builder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            JcaX509ExtensionUtils().createSubjectKeyIdentifier(keyPair.public)
        )
        if (isCa) {
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        }

        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keyPair.private)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }

    private fun createSod(
        dataGroups: Map<Int, ByteArray>,
        signingKey: java.security.PrivateKey,
        signerCert: X509Certificate
    ): ByteArray {
        val dgHashes = dataGroups.map { (dgNumber, bytes) ->
            DERSequence(
                arrayOf<ASN1Encodable>(
                    ASN1Integer(dgNumber.toLong()),
                    DEROctetString(java.security.MessageDigest.getInstance("SHA-256").digest(bytes))
                )
            )
        }.toTypedArray()

        val hashAlgId = AlgorithmIdentifier(ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.1"), org.bouncycastle.asn1.DERNull.INSTANCE)
        val ldsSecurityObject = DERSequence(
            arrayOf<ASN1Encodable>(
                ASN1Integer(0),
                hashAlgId,
                DERSequence(dgHashes)
            )
        ).encoded

        val generator = CMSSignedDataGenerator()
        val signer = JcaSignerInfoGeneratorBuilder(
            JcaDigestCalculatorProviderBuilder().setProvider("BC").build()
        ).build(
            JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(signingKey),
            org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(signerCert)
        )
        generator.addSignerInfoGenerator(signer)
        generator.addCertificates(
            org.bouncycastle.util.CollectionStore(
                listOf(org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(signerCert))
            )
        )

        val content = CMSProcessableByteArray(ldsSecurityObject)
        return generator.generate(content, true).encoded
    }

    private fun signWithRsa(keyPair: KeyPair, data: ByteArray): ByteArray {
        // AAVerifier expects SHA1withRSA for RSA keys (ICAO 9303 Part 11 common case).
        val signer = java.security.Signature.getInstance("SHA1withRSA", "BC")
        signer.initSign(keyPair.private)
        signer.update(data)
        return signer.sign()
    }
}
