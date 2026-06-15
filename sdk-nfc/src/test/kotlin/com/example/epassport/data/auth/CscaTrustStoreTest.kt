package com.example.epassport.data.auth

import com.example.epassport.domain.exception.InvalidDataException
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

class CscaTrustStoreTest {

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    private lateinit var cscaKeyPair: KeyPair
    private lateinit var cscaCert: X509Certificate

    @Before
    fun setUp() {
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
        assertEquals(cscaCert.subjectX500Principal, trustStore.getCertificates().first().subjectX500Principal)
    }

    @Test(expected = InvalidDataException::class)
    fun loadMasterList_withUntrustedSigner_throws() {
        val otherKeyPair = generateRsaKeyPair()
        val otherCert = createSelfSignedCert(
            subject = X500Name("C=XX, O=Other, CN=Other CSCA"),
            keyPair = otherKeyPair,
            keyUsage = KeyUsage(KeyUsage.keyCertSign),
            isCa = true
        )
        // Master list signed by a different key than the one we trust.
        val masterList = createSelfSignedMasterList(listOf(cscaCert), otherKeyPair, otherCert)

        CscaTrustStore().loadMasterList(masterList)
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

    @Test
    fun verifyPassiveAuthentication_withValidData_returnsTrue() {
        val masterList = createSelfSignedMasterList(listOf(cscaCert), cscaKeyPair, cscaCert)
        val trustStore = CscaTrustStore().apply { loadMasterList(masterList) }

        val sod = createSignedSod(cscaKeyPair.private, cscaCert)
        val dataGroups = emptyMap<Int, ByteArray>() // SOD-only signature test

        assertTrue(SodParser.verifyPassiveAuthentication(sod, dataGroups, trustStore))
    }

    @Test
    fun addCertificate_directly_addsToTrustStore() {
        val trustStore = CscaTrustStore()
        trustStore.addCertificate(cscaCert)

        assertEquals(1, trustStore.getCertificates().size)
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

        val content = CMSProcessableByteArray(byteArrayOf(0x30, 0x00)) // Empty SEQUENCE as dummy content
        return generator.generate(content, true).encoded
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

        val ldsSecurityObject = DERSequence(arrayOf(
            ASN1Integer(0),
            AlgorithmIdentifier(ASN1ObjectIdentifier("1.3.14.3.2.26")),
            DERSequence(arrayOf())
        )).encoded
        val content = CMSProcessableByteArray(ldsSecurityObject)
        return generator.generate(content, true).encoded
    }
}
