package com.example.epassport.data.auth

import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.bouncycastle.util.CollectionStore
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Security
import java.util.Date

class SodParserTest {

    @Before
    fun setUp() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    @Test
    fun parseDataGroupHashes_returnsExpectedHashes() {
        val dg1Hash = MessageDigest.getInstance("SHA-256").digest(byteArrayOf(1, 2, 3))
        val dg2Hash = MessageDigest.getInstance("SHA-256").digest(byteArrayOf(4, 5, 6))
        val sodBytes = buildSod(dg1Hash, dg2Hash)

        val hashes = SodParser.parseDataGroupHashes(sodBytes)

        assertEquals(2, hashes.size)
        assertArrayEquals(dg1Hash, hashes[1])
        assertArrayEquals(dg2Hash, hashes[2])
    }

    @Test
    fun verifyHashes_matchingHashes_returnsTrue() {
        val dg1Bytes = byteArrayOf(1, 2, 3)
        val dg2Bytes = byteArrayOf(4, 5, 6)
        val dg1Hash = MessageDigest.getInstance("SHA-256").digest(dg1Bytes)
        val dg2Hash = MessageDigest.getInstance("SHA-256").digest(dg2Bytes)
        val sodBytes = buildSod(dg1Hash, dg2Hash)

        val valid = SodParser.verifyHashes(sodBytes, mapOf(1 to dg1Bytes, 2 to dg2Bytes))

        assertTrue(valid)
    }

    private fun buildSod(vararg dgHashes: Pair<Int, ByteArray>): ByteArray {
        val hashAlgId = AlgorithmIdentifier(ASN1ObjectIdentifier("2.16.840.1.101.3.4.2.1"), DERNull.INSTANCE)

        val dgHashSequences = dgHashes.map { (number, hash) ->
            DERSequence(arrayOf(ASN1Integer(number.toLong()), DEROctetString(hash)))
        }.toTypedArray()

        val ldsSecurityObject = DERSequence(arrayOf(
            ASN1Integer(0),
            hashAlgId,
            DERSequence(dgHashSequences)
        ))

        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val certBuilder = JcaX509v3CertificateBuilder(
            X500Name("CN=Test CSCA"),
            BigInteger.valueOf(System.currentTimeMillis()),
            Date(System.currentTimeMillis() - 60_000),
            Date(System.currentTimeMillis() + 86_400_000),
            X500Name("CN=Test DSC"),
            keyPair.public
        )
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val cert = JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(signer))

        val gen = CMSSignedDataGenerator()
        gen.addSignerInfoGenerator(
            JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().setProvider("BC").build())
                .build(signer, cert)
        )
        gen.addCertificates(CollectionStore(listOf(X509CertificateHolder(cert.encoded))))

        val cms = gen.generate(CMSProcessableByteArray(ldsSecurityObject.encoded), true)
        return cms.encoded
    }

    private fun buildSod(dg1Hash: ByteArray, dg2Hash: ByteArray): ByteArray {
        return buildSod(1 to dg1Hash, 2 to dg2Hash)
    }
}
