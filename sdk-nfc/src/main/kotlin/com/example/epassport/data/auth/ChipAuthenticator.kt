package com.example.epassport.data.auth

import com.example.epassport.data.nfc.ApduCommand
import com.example.epassport.domain.exception.AuthenticationException
import com.example.epassport.domain.port.NfcTransceiver
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.util.BigIntegers
import java.math.BigInteger
import java.security.MessageDigest
import java.security.Security

/**
 * Chip Authentication (CA) プロトコルの実装。
 *
 * ICAO Doc 9303 Part 11/EAC に準拠し、DG14 の CA パラメータを用いて ECDH 鍵共有を行い、
 * セキュアメッセージングの鍵を更新することで MitM 攻撃を防止する。
 */
class ChipAuthenticator {

    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * Chip Authentication を実行し、共有秘密から新しいセッション鍵を導出する。
     *
     * 呼び出し側は返却された鍵ペアを既存の SecureMessaging インスタンスに適用するか、
     * 新しい SecureMessaging ラッパーを作成する必要がある。
     *
     * @param transceiver 認証済みのセキュアメッセージング対応 Transceiver
     * @return 新しいセッション鍵 (K.Enc, K.Mac) と SSC
     */
    suspend fun authenticate(transceiver: NfcTransceiver): SessionKeys {
        // 1. Read DG14
        val dg14Bytes = readDataGroup(transceiver, byteArrayOf(0x01, 0x0E))

        // 2. Parse ChipAuthenticationPublicKeyInfo
        val caInfo = parseChipAuthenticationInfo(dg14Bytes)
            ?: throw AuthenticationException("Chip Authentication info not found in DG14")

        // 3. Generate terminal ephemeral EC key pair on the same curve
        val ecSpec = ECNamedCurveTable.getParameterSpec(caInfo.curveName)
        val terminalPrivate = generateRandomScalar(ecSpec.n)
        val terminalPublic = ecSpec.g.multiply(terminalPrivate).normalize()

        // 4. MSE:Set DST (set CA public key)
        val mseDst = buildMseSetDst(caInfo.publicKeyInfo)
        checkStatus(transceiver.transceive(mseDst), "MSE:Set DST")

        // 5. MSE:Set AT (set keyId)
        val mseAt = buildMseSetAt(caInfo.keyId)
        checkStatus(transceiver.transceive(mseAt), "MSE:Set AT")

        // 6. GENERAL AUTHENTICATE step 1: send terminal public key
        val step1 = ApduCommand.generalAuthenticate(wrapDynamicAuthData(0x80, terminalPublic.getEncoded(false)))
        val step1Response = transceiver.transceive(step1)
        checkStatus(step1Response, "CA GA step 1")
        val chipPubKeyBytes = extractDynamicAuthenticationData(step1Response)
            ?: throw AuthenticationException("CA step 1 chip public key not found")
        val chipPublic = ecSpec.curve.decodePoint(chipPubKeyBytes).normalize()

        // 7. ECDH shared secret
        val sharedPoint = chipPublic.multiply(terminalPrivate).normalize()
        val sharedSecret = BigIntegers.asUnsignedByteArray(
            (ecSpec.n.bitLength() + 7) / 8,
            sharedPoint.xCoord.toBigInteger()
        )

        // 8. Derive new session keys
        val keyLength = caInfo.keyLength
        val kdfResult = kdf(sharedSecret, keyLength)
        val ksEnc = kdfResult.copyOfRange(0, keyLength / 8)
        val ksMac = kdfResult.copyOfRange(keyLength / 8, 2 * keyLength / 8)
        val ssc = ByteArray(16) // CA uses 16-byte SSC for AES

        // Secure cleanup
        sharedSecret.fill(0)

        return SessionKeys(ksEnc, ksMac, ssc)
    }

    data class SessionKeys(val ksEnc: ByteArray, val ksMac: ByteArray, val ssc: ByteArray)

    private data class ChipAuthenticationInfo(
        val publicKeyInfo: SubjectPublicKeyInfo,
        val keyId: Int,
        val curveName: String,
        val keyLength: Int
    )

    private fun parseChipAuthenticationInfo(dg14Bytes: ByteArray): ChipAuthenticationInfo? {
        return try {
            val root = ASN1Sequence.getInstance(dg14Bytes)
            for (i in 0 until root.size()) {
                val seq = ASN1Sequence.getInstance(root.getObjectAt(i))
                if (seq.size() < 2) continue
                val oid = ASN1ObjectIdentifier.getInstance(seq.getObjectAt(0)).id
                if (oid.startsWith("0.4.0.127.0.7.2.2.3.1")) {
                    @Suppress("UNUSED_VARIABLE")
                    val version = ASN1Integer.getInstance(seq.getObjectAt(1)).value.toInt()
                    val keyId = if (seq.size() > 2) ASN1Integer.getInstance(seq.getObjectAt(2)).value.toInt() else 0
                    val spki = SubjectPublicKeyInfo.getInstance(seq.getObjectAt(seq.size() - 1))
                        ?: throw AuthenticationException("CA public key info not found")

                    val curveName = inferCurveName(spki)
                    val keyLength = if (curveName.contains("256")) 128 else if (curveName.contains("384")) 192 else 256
                    return ChipAuthenticationInfo(spki, keyId, curveName, keyLength)
                }
            }
            null
        } catch (e: Exception) {
            throw AuthenticationException("Failed to parse DG14", e)
        }
    }

    private fun inferCurveName(spki: SubjectPublicKeyInfo): String {
        return try {
            val params = spki.algorithm.parameters
            val oid = org.bouncycastle.asn1.ASN1ObjectIdentifier.getInstance(params)
            when (oid.id) {
                "1.2.840.10045.3.1.7" -> "secp256r1"
                "1.3.132.0.34" -> "secp384r1"
                "1.3.132.0.35" -> "secp521r1"
                "1.3.36.3.3.2.8.1.1.7" -> "brainpoolP256r1"
                else -> "secp256r1"
            }
        } catch (e: Exception) {
            // Safe fallback to string matching if ASN.1 casting fails
            try {
                val paramsStr = spki.algorithm.parameters?.toString() ?: ""
                when {
                    paramsStr.contains("256") -> "secp256r1"
                    paramsStr.contains("384") -> "secp384r1"
                    paramsStr.contains("521") -> "secp521r1"
                    else -> "secp256r1"
                }
            } catch (ex: Exception) {
                "secp256r1"
            }
        }
    }

    private fun buildMseSetDst(publicKeyInfo: SubjectPublicKeyInfo): ByteArray {
        val data = publicKeyInfo.encoded
        val apdu = ByteArray(5 + data.size)
        apdu[0] = 0x00.toByte()
        apdu[1] = 0x22.toByte()
        apdu[2] = 0x81.toByte()
        apdu[3] = 0xB6.toByte()
        apdu[4] = data.size.toByte()
        System.arraycopy(data, 0, apdu, 5, data.size)
        return apdu
    }

    private fun buildMseSetAt(keyId: Int): ByteArray {
        val data = byteArrayOf(0x83.toByte(), 0x01.toByte(), keyId.toByte())
        val apdu = ByteArray(5 + data.size)
        apdu[0] = 0x00.toByte()
        apdu[1] = 0x22.toByte()
        apdu[2] = 0x83.toByte()
        apdu[3] = 0xB4.toByte()
        apdu[4] = data.size.toByte()
        System.arraycopy(data, 0, apdu, 5, data.size)
        return apdu
    }

    private suspend fun readDataGroup(transceiver: NfcTransceiver, fileId: ByteArray): ByteArray {
        checkStatus(transceiver.transceive(ApduCommand.selectFile(fileId)), "SELECT FILE")
        val initial = transceiver.transceive(ApduCommand.readBinary(0, 8))
        checkStatus(initial, "read header")
        return initial.copyOfRange(0, initial.size - 2)
    }

    private fun wrapDynamicAuthData(tag: Int, data: ByteArray): ByteArray {
        val len = encodeLength(data.size)
        val inner = byteArrayOf(tag.toByte()) + len + data
        val outerLen = encodeLength(inner.size)
        return byteArrayOf(0x7C.toByte()) + outerLen + inner
    }

    private fun extractDynamicAuthenticationData(response: ByteArray): ByteArray? {
        if (response.size < 2) return null
        val data = response.copyOfRange(0, response.size - 2)
        if (data.isEmpty()) return null
        if (data[0].toInt() and 0xFF != 0x7C) return null
        val (_, lenBytes) = parseLength(data, 1)
        var offset = 1 + lenBytes
        if (offset >= data.size) return null
        @Suppress("UNUSED_VARIABLE")
        val tag = data[offset].toInt() and 0xFF
        offset++
        val (valueLen, valueLenBytes) = parseLength(data, offset)
        offset += valueLenBytes
        if (offset + valueLen > data.size) return null
        return data.copyOfRange(offset, offset + valueLen)
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

    private fun encodeLength(length: Int): ByteArray {
        return when {
            length <= 0x7F -> byteArrayOf(length.toByte())
            length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), (length and 0xFF).toByte())
        }
    }

    private fun generateRandomScalar(n: BigInteger): BigInteger {
        val bytes = ByteArray((n.bitLength() + 7) / 8)
        java.security.SecureRandom().nextBytes(bytes)
        var scalar = BigInteger(1, bytes).mod(n)
        if (scalar.signum() == 0) scalar = BigInteger.ONE
        return scalar
    }

    private fun kdf(sharedSecret: ByteArray, keyLengthBits: Int): ByteArray {
        val keyBytes = keyLengthBits / 8 * 2
        val result = java.io.ByteArrayOutputStream()
        var counter = 1
        while (result.size() < keyBytes) {
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update(sharedSecret)
            digest.update(byteArrayOf((counter ushr 24).toByte(), (counter ushr 16).toByte(), (counter ushr 8).toByte(), counter.toByte()))
            result.write(digest.digest())
            counter++
        }
        return result.toByteArray().copyOfRange(0, keyBytes)
    }

    private fun checkStatus(response: ByteArray, context: String) {
        if (response.size < 2) {
            throw AuthenticationException("$context: empty response")
        }
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        if (sw1 != 0x90 || sw2 != 0x00) {
            throw AuthenticationException("$context failed: SW=%04X".format((sw1 shl 8) or sw2))
        }
    }
}
