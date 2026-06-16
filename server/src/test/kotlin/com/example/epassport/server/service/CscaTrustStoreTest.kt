package com.example.epassport.server.service

import org.bouncycastle.asn1.x500.X500Name
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

class CscaTrustStoreTest {

    private lateinit var cscaKeyPair: KeyPair
    private lateinit var cscaCert: X509Certificate

    @BeforeEach
    fun setUp() {
        Security.addProvider(BouncyCastleProvider())
        cscaKeyPair = generateRsaKeyPair()
        cscaCert = createSelfSignedCert(
            subject = X500Name("C=JP, O=Government, CN=CSCA Japan"),
            keyPair = cscaKeyPair,
            keyUsage = KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
            isCa = true
        )
    }

    @Test
    fun loadMasterList_withSelfSignedList_loadsCertificates() {
        val masterList = createSelfSignedMasterList(listOf(cscaCert), cscaKeyPair, cscaCert)

        val trustStore = CscaTrustStore()
        trustStore.loadMasterList(masterList)

        assertEquals(1, trustStore.getCertificates().size)
    }

    @Test
    fun loadMasterList_withUntrustedSigner_throws() {
        val otherKeyPair = generateRsaKeyPair()
        val otherCert = createSelfSignedCert(
            subject = X500Name("C=XX, O=Other, CN=Other CSCA"),
            keyPair = otherKeyPair,
            keyUsage = KeyUsage(KeyUsage.keyCertSign),
            isCa = true
        )
        val masterList = createSelfSignedMasterList(listOf(cscaCert), otherKeyPair, otherCert)

        assertThrows(IllegalArgumentException::class.java) {
            CscaTrustStore().loadMasterList(masterList)
        }
    }

    @Test
    fun verifySodSignature_withTrustedCsca_returnsTrue() {
        val masterList = createSelfSignedMasterList(listOf(cscaCert), cscaKeyPair, cscaCert)
        val trustStore = CscaTrustStore().apply { loadMasterList(masterList) }

        val sod = createSignedSod(cscaKeyPair.private, cscaCert)

        assertTrue(trustStore.verifySodSignature(sod))
    }

    @Test
    fun verifySodSignature_withUntrustedSigner_returnsFalse() {
        val otherKeyPair = generateRsaKeyPair()
        val otherCert = createSelfSignedCert(
            subject = X500Name("C=XX, O=Other, CN=Other CSCA"),
            keyPair = otherKeyPair,
            keyUsage = KeyUsage(KeyUsage.keyCertSign),
            isCa = true
        )
        val masterList = createSelfSignedMasterList(listOf(cscaCert), cscaKeyPair, cscaCert)
        val trustStore = CscaTrustStore().apply { loadMasterList(masterList) }

        val sod = createSignedSod(otherKeyPair.private, otherCert)

        assertFalse(trustStore.verifySodSignature(sod))
    }

    private fun generateRsaKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    private fun createSelfSignedCert(
        subject: X500Name,
        keyPair: KeyPair,
        keyUsage: KeyUsage,
        isCa: Boolean
    ): X509Certificate {
        val notBefore = Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
        val notAfter = Date(System.currentTimeMillis() + 365 * 24 * 60 * 60 * 1000)
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

    private fun createSelfSignedMasterList(
        certificates: List<X509Certificate>,
        signerKey: KeyPair,
        signerCert: X509Certificate
    ): ByteArray {
        val certHolders = certificates.map { org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(it) }
        val generator = CMSSignedDataGenerator()
        val signer = JcaSignerInfoGeneratorBuilder(
            JcaDigestCalculatorProviderBuilder().setProvider("BC").build()
        ).build(
            JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(signerKey.private),
            org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(signerCert)
        )
        generator.addSignerInfoGenerator(signer)
        generator.addCertificates(org.bouncycastle.util.CollectionStore(certHolders))
        return generator.generate(CMSProcessableByteArray(byteArrayOf(0x30, 0x00)), true).encoded
    }

    private fun createSignedSod(signingKey: java.security.PrivateKey, signerCert: X509Certificate): ByteArray {
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
        val ldsSecurityObject = byteArrayOf(0x30.toByte(), 0x0E.toByte(), 0x02.toByte(), 0x01.toByte(), 0x00.toByte())
        return generator.generate(CMSProcessableByteArray(ldsSecurityObject), true).encoded
    }
}
