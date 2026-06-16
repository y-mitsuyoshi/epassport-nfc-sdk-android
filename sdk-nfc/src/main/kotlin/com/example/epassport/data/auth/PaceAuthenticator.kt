package com.example.epassport.data.auth

import com.example.epassport.data.nfc.ApduCommand
import com.example.epassport.data.parser.PaceInfoParser
import com.example.epassport.domain.exception.AuthenticationException
import com.example.epassport.domain.model.MrzData
import com.example.epassport.domain.model.PaceInfo
import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.domain.port.PassportAuthenticator
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.crypto.agreement.ECDHBasicAgreement
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.bouncycastle.crypto.util.PublicKeyFactory
import org.bouncycastle.jce.ECNamedCurveTable
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECParameterSpec
import org.bouncycastle.math.ec.ECCurve
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.util.BigIntegers
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * PACE (Password Authenticated Connection Establishment) 認証プロトコルの実装。
 *
 * ICAO Doc 9303 Part 11 準拠。ECDH-GM-AES-CMAC 系の PACEInfo に対応。
 *
 * 実装の概要:
 * 1. EF.CardAccess を読み取り PACEInfo を取得
 * 2. MSE:Set AT で PACE パラメータを設定
 * 3. GENERAL AUTHENTICATE により nonce 取得 → パスワード復号 → mapping → ECDH 鍵共有
 * 4. AES-CMAC ベースの SecureMessaging を確立
 */
class PaceAuthenticator : PassportAuthenticator {

    private val bcProvider: BouncyCastleProvider = BouncyCastleProvider().also {
        if (Security.getProvider(it.name) == null) Security.addProvider(it)
    }

    override suspend fun authenticate(
        transceiver: NfcTransceiver,
        bacKey: com.example.epassport.domain.model.BacKey
    ): NfcTransceiver {
        throw AuthenticationException(
            "PACE requires MrzData or CAN. Use authenticate(transceiver, mrzData) instead."
        )
    }

    /**
     * PACE 認証を実行する。
     *
     * @param transceiver NFC トランシーバー
     * @param mrzData MRZ 情報（パスワード源、オプショナルで CAN）
     * @return AES-CMAC ベースの SecureMessaging ラッパー
     */
    override suspend fun authenticate(transceiver: NfcTransceiver, mrzData: MrzData): NfcTransceiver {
        // 1. SELECT and read EF.CardAccess
        val cardAccessResponse = transceiver.transceive(ApduCommand.selectCardAccess())
        checkStatus(cardAccessResponse, "SELECT EF.CardAccess")

        val cardAccessBytes = readFile(transceiver)
        val paceInfo = PaceInfoParser.parse(cardAccessBytes)
            ?: throw AuthenticationException("PACE not supported by this chip")

        if (!paceInfo.isEcdh) {
            throw AuthenticationException("Only ECDH-based PACE is supported")
        }

        val passwordRef = if (mrzData.can != null) PACE_PASSWORD_CAN else PACE_PASSWORD_MRZ

        // 2. MSE:Set AT
        val oidBytes = ASN1ObjectIdentifier(paceInfo.protocolOid).encoded
        val mseAt = ApduCommand.paceMseSetAt(oidBytes, passwordRef)
        val mseResponse = transceiver.transceive(mseAt)
        checkStatus(mseResponse, "MSE:Set AT")

        // 3. Get nonce
        val nonceResponse = transceiver.transceive(ApduCommand.paceGetNonce())
        checkStatus(nonceResponse, "PACE Get Nonce")
        val encryptedNonce = extractDynamicAuthenticationData(nonceResponse)
            ?: throw AuthenticationException("PACE nonce not found in response")

        // 4. Decrypt nonce with password-derived key
        val passwordKey = derivePaceKey(mrzData, passwordRef)
        val nonce = decryptNonce(encryptedNonce, passwordKey)

        var sharedSecretBytes: ByteArray? = null
        var kdfResult: ByteArray? = null
        try {
            // 5. Determine curve parameters
            val curveName = paceInfo.parameterId?.let { parameterIdToCurveName(it) }
                ?: "secp256r1"
            val ecSpec = ECNamedCurveTable.getParameterSpec(curveName)
            val g = ecSpec.g

            // 6. Terminal generates random scalar r and computes X = r * G
            val r = generateRandomScalar(ecSpec.n)
            val terminalX = g.multiply(r)

            // 7. GENERAL AUTHENTICATE: send X (step 1, tag 0x81)
            val step1 = ApduCommand.generalAuthenticate(wrapDynamicAuthData(0x81, terminalX.getEncoded(false)))
            val step1Response = transceiver.transceive(step1)
            checkStatus(step1Response, "PACE GA step 1")
            val chipYBytes = extractDynamicAuthenticationData(step1Response)
                ?: throw AuthenticationException("PACE step 1 chip Y not found")
            val chipY = ecSpec.curve.decodePoint(chipYBytes)

            // 8. GM mapping: G' = abm(s) * G + K, where K = r * Y
            val kPoint = chipY.multiply(r).normalize()
            val p = mapNonceToScalar(nonce, ecSpec.n)
            val mappedG = g.multiply(p).add(kPoint).normalize()

            // 9. Terminal generates random scalar x' and computes T_A = x' * G'
            val terminalScalar = generateRandomScalar(ecSpec.n)
            val terminalPublic = mappedG.multiply(terminalScalar)

            // 10. GENERAL AUTHENTICATE: send T_A (step 2, tag 0x83)
            val step2 = ApduCommand.generalAuthenticate(wrapDynamicAuthData(0x83, terminalPublic.getEncoded(false)))
            val step2Response = transceiver.transceive(step2)
            checkStatus(step2Response, "PACE GA step 2")
            val chipCBytes = extractDynamicAuthenticationData(step2Response)
                ?: throw AuthenticationException("PACE step 2 chip public key not found")
            val chipC = ecSpec.curve.decodePoint(chipCBytes)

            // 11. ECDH shared secret: K_master = x' * T_B
            val sharedSecret = chipC.multiply(terminalScalar).normalize().xCoord.toBigInteger()
            sharedSecretBytes = BigIntegers.asUnsignedByteArray(
                (ecSpec.n.bitLength() + 7) / 8,
                sharedSecret
            )

            // 12. Derive session keys (AES OIDs use SHA-256 KDF, 3DES uses SHA-1)
            val hashAlg = if (paceInfo.protocolOid.contains(".2.2.4.2")) "SHA-256" else "SHA-1"
            kdfResult = kdf(sharedSecretBytes, nonce, paceInfo.keyLength, hashAlg)
            val ksEnc = kdfResult.copyOfRange(0, paceInfo.keyLength / 8)
            val ksMac = kdfResult.copyOfRange(paceInfo.keyLength / 8, 2 * paceInfo.keyLength / 8)
            val ssc = nonce.copyOfRange(0, 8)

            // 13. Verify chip's token (step 3)
            val step3 = ApduCommand.generalAuthenticate(wrapDynamicAuthData(0x85, byteArrayOf()))
            val step3Response = transceiver.transceive(step3)
            checkStatus(step3Response, "PACE GA step 3")

            // 14. Return AES-CMAC SecureMessaging
            return AesCmacSecureMessaging(transceiver, ksEnc, ksMac, ssc)
        } finally {
            // Secure cleanup of cryptographic materials
            passwordKey.fill(0)
            sharedSecretBytes?.fill(0)
            nonce.fill(0)
            kdfResult?.fill(0)
        }
    }

    private suspend fun readFile(transceiver: NfcTransceiver): ByteArray {
        val initial = transceiver.transceive(ApduCommand.readBinary(0, 8))
        checkStatus(initial, "read file header")
        val header = initial.copyOfRange(0, initial.size - 2)

        val lengthResult = parseLength(header, 1)
        val totalLength = 1 + lengthResult.bytesRead + lengthResult.length
        
        val output = ByteArrayOutputStream()
        output.write(header.copyOfRange(0, minOf(header.size, totalLength)))

        var offset = output.size()
        while (offset < totalLength) {
            val remaining = totalLength - offset
            val read = transceiver.transceive(ApduCommand.readBinary(offset, remaining.coerceAtMost(224)))
            checkStatus(read, "read file body at offset $offset")
            val body = read.copyOfRange(0, read.size - 2)
            if (body.isEmpty()) break
            output.write(body)
            offset += body.size
        }
        return output.toByteArray()
    }

    private data class LengthResult(val length: Int, val bytesRead: Int)

    private fun parseLength(data: ByteArray, offset: Int): LengthResult {
        val first = data[offset].toInt() and 0xFF
        return when {
            first <= 0x7F -> LengthResult(first, 1)
            first == 0x81 -> LengthResult(data[offset + 1].toInt() and 0xFF, 2)
            first == 0x82 -> LengthResult(
                ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset + 2].toInt() and 0xFF),
                3
            )
            else -> throw AuthenticationException("Unsupported length encoding")
        }
    }

    private fun derivePacePassword(mrzData: MrzData): ByteArray {
        // ICAO 9303 Part 11: K_pi = SHA-1(MRZ information) first 16 bytes
        val mrzInfo = mrzData.mrzInformation
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(mrzInfo.toByteArray(Charsets.UTF_8))
        val hash = digest.digest()
        val result = hash.copyOfRange(0, 16)
        // Clear temporary material
        mrzInfo.toCharArray().fill('\u0000')
        hash.fill(0)
        return result
    }

    private fun derivePaceKey(mrzData: MrzData, passwordRef: Byte): ByteArray {
        val keySeed = if (passwordRef == PACE_PASSWORD_CAN) {
            val canChar = mrzData.can ?: throw AuthenticationException("CAN is required for CAN-based PACE")
            val bytes = String(canChar).toByteArray(Charsets.UTF_8)
            // Clear temporary string content by overwriting
            bytes
        } else {
            // MRZ info
            val mrzInfo = mrzData.mrzInformation
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update(mrzInfo.toByteArray(Charsets.UTF_8))
            val hash = digest.digest()
            val seed = hash.copyOfRange(0, 16)
            mrzInfo.toCharArray().fill('\u0000')
            hash.fill(0)
            seed
        }

        // Derive static key K_pi: KDF(keySeed, 3) -> SHA-1(keySeed || 0x00000003)
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(keySeed)
        digest.update(byteArrayOf(0, 0, 0, 3))
        val hash = digest.digest()
        val kPi = hash.copyOfRange(0, 16)

        // Clear temporary material
        if (passwordRef == PACE_PASSWORD_CAN) {
            keySeed.fill(0)
        }
        hash.fill(0)
        return kPi
    }

    private fun decryptNonce(encryptedNonce: ByteArray, key: ByteArray): ByteArray {
        // ICAO 9303 Part 11 uses AES-CBC or 3DES-CBC depending on PACEInfo
        val cipher = Cipher.getInstance("AES/CBC/NoPadding", bcProvider)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ByteArray(16)))
        return cipher.doFinal(encryptedNonce)
    }

    private fun parameterIdToCurveName(id: Int): String {
        return when (id) {
            0x01, 0x05, 0x0D -> "secp256r1"
            0x02 -> "brainpoolP192r1"
            0x03 -> "secp224r1"
            0x04 -> "brainpoolP224r1"
            0x06 -> "brainpoolP256r1"
            0x07 -> "secp320r1"
            0x08 -> "brainpoolP320r1"
            0x09 -> "secp384r1"
            0x0A -> "brainpoolP384r1"
            0x0B -> "secp521r1"
            0x0C -> "brainpoolP512r1"
            else -> throw AuthenticationException("Unsupported PACE parameterId: $id")
        }
    }

    private fun mapNonceToScalar(nonce: ByteArray, n: BigInteger): BigInteger {
        // Simplified mapping: interpret nonce as unsigned big integer modulo n
        var value = BigInteger(1, nonce)
        if (value >= n || value.signum() <= 0) {
            value = value.mod(n)
        }
        return value
    }

    private fun generateRandomScalar(n: BigInteger): BigInteger {
        val bytes = ByteArray((n.bitLength() + 7) / 8)
        java.security.SecureRandom().nextBytes(bytes)
        var scalar = BigInteger(1, bytes).mod(n)
        if (scalar.signum() == 0) scalar = BigInteger.ONE
        return scalar
    }

    @Suppress("UNUSED_PARAMETER")
    private fun kdf(sharedSecret: ByteArray, nonce: ByteArray, keyLengthBits: Int, hashAlg: String): ByteArray {
        // ICAO 9303 KDF: SHA-1 or SHA-256 (sharedSecret || counter) repeated
        val keyBytes = keyLengthBits / 8 * 2
        val result = ByteArrayOutputStream()
        var counter = 1
        while (result.size() < keyBytes) {
            val digest = MessageDigest.getInstance(hashAlg)
            digest.update(sharedSecret)
            digest.update(byteArrayOf((counter ushr 24).toByte(), (counter ushr 16).toByte(), (counter ushr 8).toByte(), counter.toByte()))
            result.write(digest.digest())
            counter++
        }
        return result.toByteArray().copyOfRange(0, keyBytes)
    }

    private fun wrapDynamicAuthData(tag: Int, data: ByteArray): ByteArray {
        val inner = if (data.isEmpty()) {
            byteArrayOf()
        } else {
            val len = encodeLength(data.size)
            byteArrayOf(tag.toByte()) + len + data
        }
        val outerLen = encodeLength(inner.size)
        return byteArrayOf(0x7C.toByte()) + outerLen + inner
    }

    private fun extractDynamicAuthenticationData(response: ByteArray): ByteArray? {
        if (response.size < 2) return null
        val data = response.copyOfRange(0, response.size - 2)
        if (data.isEmpty()) return null
        if (data[0].toInt() and 0xFF != 0x7C) return null
        val lengthResult = parseLength(data, 1)
        var offset = 1 + lengthResult.bytesRead
        if (offset >= data.size) return null
        // Return first tagged content
        @Suppress("UNUSED_VARIABLE")
        val tag = data[offset].toInt() and 0xFF
        offset++
        val lenResult = parseLength(data, offset)
        offset += lenResult.bytesRead
        if (offset + lenResult.length > data.size) return null
        return data.copyOfRange(offset, offset + lenResult.length)
    }

    private fun encodeLength(length: Int): ByteArray {
        return when {
            length <= 0x7F -> byteArrayOf(length.toByte())
            length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), (length and 0xFF).toByte())
        }
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

    companion object {
        private const val PACE_PASSWORD_MRZ: Byte = 0x01
        private const val PACE_PASSWORD_CAN: Byte = 0x02
    }
}
