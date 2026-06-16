package com.example.epassport.server.service

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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.MessageDigest
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.Signature
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Date

class PassportVerificationServiceTest {

    private lateinit var service: PassportVerificationService
    private lateinit var cscaKeyPair: KeyPair
    private lateinit var cscaCert: X509Certificate
    private lateinit var aaKeyPair: KeyPair

    @BeforeEach
    fun setUp() {
        Security.addProvider(BouncyCastleProvider())
        service = PassportVerificationService()
        cscaKeyPair = generateRsaKeyPair()
        cscaCert = createSelfSignedCert(
            X500Name("C=JP, O=Government, CN=CSCA Japan"),
            cscaKeyPair,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign),
            true
        )
        aaKeyPair = generateRsaKeyPair()
    }

    @Test
    fun verify_withValidPaAndAa_returnsSuccess() {
        val dg1 = byteArrayOf(0x01, 0x02)
        val dg2 = byteArrayOf(0x03, 0x04)
        val dg15 = aaKeyPair.public.encoded
        val dataGroups = mapOf(1 to dg1, 2 to dg2, 15 to dg15)
        val sod = createSod(dataGroups, cscaKeyPair.private, cscaCert)

        val challenge = byteArrayOf(0x11, 0x22)
        val signature = signWithRsa(aaKeyPair, challenge)

        val request = PassportVerificationService.VerificationRequest(
            sodBase64 = Base64.getEncoder().encodeToString(sod),
            dataGroups = dataGroups.mapValues { Base64.getEncoder().encodeToString(it.value) },
            aaPublicKeyBase64 = Base64.getEncoder().encodeToString(dg15),
            aaChallengeBase64 = Base64.getEncoder().encodeToString(challenge),
            aaSignatureBase64 = Base64.getEncoder().encodeToString(signature),
            cscaMasterListBase64 = Base64.getEncoder().encodeToString(createMasterList(cscaCert))
        )

        val result = service.verify(request)

        assert(result.successful)
        assert(result.paSuccess)
        assert(result.aaSuccess == true)
    }

    @Test
    fun verify_withTamperedDgHash_returnsFailure() {
        val dg1 = byteArrayOf(0x01, 0x02)
        val dataGroups = mapOf(1 to dg1)
        val sod = createSod(dataGroups, cscaKeyPair.private, cscaCert)

        val tamperedGroups = mapOf(1 to byteArrayOf(0xFF.toByte()))
        val request = PassportVerificationService.VerificationRequest(
            sodBase64 = Base64.getEncoder().encodeToString(sod),
            dataGroups = tamperedGroups.mapValues { Base64.getEncoder().encodeToString(it.value) },
            aaPublicKeyBase64 = null,
            aaChallengeBase64 = null,
            aaSignatureBase64 = null,
            cscaMasterListBase64 = Base64.getEncoder().encodeToString(createMasterList(cscaCert))
        )

        val result = service.verify(request)

        assert(!result.successful)
        assert(result.failureReason == PassportVerificationService.FailureReason.DG_HASH_MISMATCH)
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

    private fun createMasterList(cscaCert: X509Certificate): ByteArray {
        val certHolder = org.bouncycastle.cert.jcajce.JcaX509CertificateHolder(cscaCert)
        val generator = CMSSignedDataGenerator()
        val signer = JcaSignerInfoGeneratorBuilder(
            JcaDigestCalculatorProviderBuilder().setProvider("BC").build()
        ).build(
            JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(cscaKeyPair.private),
            certHolder
        )
        generator.addSignerInfoGenerator(signer)
        generator.addCertificates(org.bouncycastle.util.CollectionStore(listOf(certHolder)))
        return generator.generate(CMSProcessableByteArray(byteArrayOf(0x30, 0x00)), true).encoded
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
                    DEROctetString(MessageDigest.getInstance("SHA-256").digest(bytes))
                )
            )
        }.toTypedArray()

        val ldsSecurityObject = DERSequence(
            arrayOf<ASN1Encodable>(
                ASN1Integer(0),
                AlgorithmIdentifier(ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.1")),
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
        return generator.generate(CMSProcessableByteArray(ldsSecurityObject), true).encoded
    }

    private fun signWithRsa(keyPair: KeyPair, data: ByteArray): ByteArray {
        val signer = Signature.getInstance("SHA1withRSA", "BC")
        signer.initSign(keyPair.private)
        signer.update(data)
        return signer.sign()
    }
}
