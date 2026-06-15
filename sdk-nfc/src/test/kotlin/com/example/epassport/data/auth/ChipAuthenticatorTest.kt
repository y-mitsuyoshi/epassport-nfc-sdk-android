package com.example.epassport.data.auth

import com.example.epassport.domain.port.NfcTransceiver
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.ECKeyGenerationParameters
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.util.BigIntegers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.Security

class ChipAuthenticatorTest {

    private lateinit var transceiver: NfcTransceiver
    private val authenticator = ChipAuthenticator()
    private var capturedTerminalPublic: ByteArray? = null

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    @Before
    fun setUp() {
        transceiver = mockk()
        capturedTerminalPublic = null
    }

    @Test
    fun authenticate_successful_returnsSessionKeys() { runBlocking {
        val curveName = "secp256r1"
        val ecSpec = ECNamedCurveTable.getParameterSpec(curveName)
        val domainParams = ECDomainParameters(ecSpec.curve, ecSpec.g, ecSpec.n, ecSpec.h)
        val chipKeyPair = ECKeyPairGenerator().apply {
            init(ECKeyGenerationParameters(domainParams, java.security.SecureRandom()))
        }.generateKeyPair()
        val chipPublic = chipKeyPair.public as org.bouncycastle.crypto.params.ECPublicKeyParameters
        val chipPrivate = chipKeyPair.private as org.bouncycastle.crypto.params.ECPrivateKeyParameters

        val ecPubPoint = chipPublic.q.getEncoded(false)
        val curveOid = ASN1ObjectIdentifier("1.2.840.10045.3.1.7") // secp256r1
        val algorithmIdentifier = org.bouncycastle.asn1.x509.AlgorithmIdentifier(
            org.bouncycastle.asn1.x9.X9ObjectIdentifiers.id_ecPublicKey,
            curveOid
        )
        val spki = SubjectPublicKeyInfo(algorithmIdentifier, ecPubPoint)

        val caOid = ASN1ObjectIdentifier("0.4.0.127.0.7.2.2.3.1.2") // CA-ECDH-AES-CBC-CMAC-128
        val caInfoSeq = DERSequence(arrayOf(caOid, ASN1Integer(1), ASN1Integer(1), spki))
        val dg14 = DERSequence(arrayOf(caInfoSeq)).encoded

        coEvery { transceiver.transceive(match { it[1] == 0xA4.toByte() }) } returns byteArrayOf(0x90.toByte(), 0x00.toByte())
        coEvery { transceiver.transceive(match { it[1] == 0xB0.toByte() }) } returns dg14 + byteArrayOf(0x90.toByte(), 0x00.toByte())
        coEvery { transceiver.transceive(match { it[1] == 0x22.toByte() && it[2] == 0x81.toByte() }) } returns byteArrayOf(0x90.toByte(), 0x00.toByte())
        coEvery { transceiver.transceive(match { it[1] == 0x22.toByte() && it[2] == 0x83.toByte() }) } returns byteArrayOf(0x90.toByte(), 0x00.toByte())
        coEvery { transceiver.transceive(match { it[1] == 0x86.toByte() }) } answers {
            val cmd = arg<ByteArray>(0)
            val cmdData = cmd.copyOfRange(5, cmd.size - 1)
            val terminalPubBytes = extractTag80(cmdData)
            capturedTerminalPublic = terminalPubBytes
            val chipPubWrapped = wrapDynamicAuthData(0x80, chipPublic.q.getEncoded(false))
            chipPubWrapped + byteArrayOf(0x90.toByte(), 0x00.toByte())
        }

        val keys = authenticator.authenticate(transceiver)
        assertNotNull(keys)
        assertEquals(16, keys.ksEnc.size)
        assertEquals(16, keys.ksMac.size)
        assertEquals(16, keys.ssc.size)

        // Verify derived keys with our own KDF using the captured terminal public.
        assertNotNull(capturedTerminalPublic)
        val terminalPubPoint = ecSpec.curve.decodePoint(capturedTerminalPublic!!).normalize()
        val sharedPoint = terminalPubPoint.multiply(chipPrivate.d).normalize()
        val sharedSecret = BigIntegers.asUnsignedByteArray(
            (ecSpec.n.bitLength() + 7) / 8,
            sharedPoint.xCoord.toBigInteger()
        )
        val expectedKdf = kdf(sharedSecret, 128)
        assertEquals(expectedKdf.toList(), (keys.ksEnc + keys.ksMac).toList())
    } }

    private fun extractTag80(data: ByteArray): ByteArray {
        var offset = 0
        // Skip 0x7C wrapper
        offset++
        val (outerLen, outerLenBytes) = parseLength(data, offset)
        offset += outerLenBytes
        // Skip 0x80 tag
        offset++
        val (innerLen, innerLenBytes) = parseLength(data, offset)
        offset += innerLenBytes
        return data.copyOfRange(offset, offset + innerLen)
    }

    private fun parseLength(data: ByteArray, offset: Int): Pair<Int, Int> {
        val first = data[offset].toInt() and 0xFF
        return when {
            first <= 0x7F -> Pair(first, 1)
            first == 0x81 -> Pair(data[offset + 1].toInt() and 0xFF, 2)
            first == 0x82 -> Pair(((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset + 2].toInt() and 0xFF), 3)
            else -> throw IllegalArgumentException("Unsupported length encoding")
        }
    }

    private fun wrapDynamicAuthData(tag: Int, data: ByteArray): ByteArray {
        val len = encodeLength(data.size)
        val inner = byteArrayOf(tag.toByte()) + len + data
        val outerLen = encodeLength(inner.size)
        return byteArrayOf(0x7C.toByte()) + outerLen + inner
    }

    private fun encodeLength(length: Int): ByteArray {
        return when {
            length <= 0x7F -> byteArrayOf(length.toByte())
            length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), (length and 0xFF).toByte())
        }
    }

    private fun kdf(sharedSecret: ByteArray, keyLengthBits: Int): ByteArray {
        val keyBytes = keyLengthBits / 8 * 2
        val result = java.io.ByteArrayOutputStream()
        var counter = 1
        while (result.size() < keyBytes) {
            val digest = java.security.MessageDigest.getInstance("SHA-1")
            digest.update(sharedSecret)
            digest.update(byteArrayOf((counter ushr 24).toByte(), (counter ushr 16).toByte(), (counter ushr 8).toByte(), counter.toByte()))
            result.write(digest.digest())
            counter++
        }
        return result.toByteArray().copyOfRange(0, keyBytes)
    }
}
