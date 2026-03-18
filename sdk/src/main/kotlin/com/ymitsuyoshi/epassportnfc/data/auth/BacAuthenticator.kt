package com.ymitsuyoshi.epassportnfc.data.auth

import com.ymitsuyoshi.epassportnfc.core.EPassportException
import com.ymitsuyoshi.epassportnfc.data.nfc.NfcApduCommand
import com.ymitsuyoshi.epassportnfc.data.nfc.NfcApduResponse
import com.ymitsuyoshi.epassportnfc.domain.repository.BacKey
import org.bouncycastle.crypto.engines.DESedeEngine
import org.bouncycastle.crypto.macs.ISO9797Alg3Mac
import org.bouncycastle.crypto.paddings.ISO7816d4Padding
import org.bouncycastle.crypto.params.KeyParameter
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.DESedeKeySpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Implements Basic Access Control (BAC) as defined in ICAO Doc 9303 Part 11.
 *
 * BAC prevents unauthorised reading of the passport chip by requiring knowledge of
 * the MRZ data (document number, date of birth, expiry date) to establish a secure
 * session key before any data groups can be read.
 *
 * @param transceive Lambda that sends raw APDU bytes to the NFC chip and returns the response bytes.
 */
class BacAuthenticator(
    private val transceive: suspend (ByteArray) -> ByteArray,
) {
    private val random = SecureRandom()

    /**
     * Performs the full BAC handshake and returns the established [BacSession] containing the
     * session encryption and MAC keys, along with the initial send sequence counter.
     *
     * @param bacKey The MRZ-derived key material.
     * @return [BacSession] with the negotiated session keys.
     * @throws EPassportException.BacAuthenticationException if the handshake fails.
     */
    suspend fun authenticate(bacKey: BacKey): BacSession {
        return try {
            val (kEnc, kMac) = deriveKeys(bacKey)

            // Step 1: GET CHALLENGE
            val challengeResponse = NfcApduResponse.fromBytes(
                transceive(NfcApduCommand.getChallenge.toBytes())
            )
            challengeResponse.requireSuccess("GET CHALLENGE")
            val rndIc = challengeResponse.data

            // Step 2: Build EXTERNAL AUTHENTICATE payload
            val rndIfd = ByteArray(8).also { random.nextBytes(it) }
            val kIfd = ByteArray(16).also { random.nextBytes(it) }
            val s = rndIfd + rndIc + kIfd

            val eifd = encryptDes3(kEnc, s)
            val mifd = computeMac(kMac, eifd)
            val authToken = eifd + mifd

            // Step 3: EXTERNAL AUTHENTICATE
            val authResponse = NfcApduResponse.fromBytes(
                transceive(NfcApduCommand.externalAuthenticate(authToken).toBytes())
            )
            authResponse.requireSuccess("EXTERNAL AUTHENTICATE")

            // Step 4: Derive session keys
            val responseData = authResponse.data
            val eIc = responseData.copyOf(32)
            val mIc = responseData.copyOfRange(32, 40)

            val computedMac = computeMac(kMac, eIc)
            if (!computedMac.contentEquals(mIc)) {
                throw EPassportException.BacAuthenticationException(
                    "BAC MAC verification failed — possible chip tampering"
                )
            }

            val decrypted = decryptDes3(kEnc, eIc)
            val kIc = decrypted.copyOfRange(16, 32)

            val keySeed = ByteArray(16) { i -> (kIfd[i].toInt() xor kIc[i].toInt()).toByte() }
            val sessionKEnc = deriveSessionKey(keySeed, 1)
            val sessionKMac = deriveSessionKey(keySeed, 2)

            val ssc = rndIc.copyOfRange(4, 8) + rndIfd.copyOfRange(4, 8)

            BacSession(sessionKEnc, sessionKMac, ssc)
        } catch (e: EPassportException) {
            throw e
        } catch (e: Exception) {
            throw EPassportException.BacAuthenticationException("BAC failed: ${e.message}", e)
        }
    }

    private fun deriveKeys(bacKey: BacKey): Pair<ByteArray, ByteArray> {
        val mrz = "${bacKey.documentNumber}${computeCheckDigit(bacKey.documentNumber)}" +
            "${bacKey.dateOfBirth}${computeCheckDigit(bacKey.dateOfBirth)}" +
            "${bacKey.dateOfExpiry}${computeCheckDigit(bacKey.dateOfExpiry)}"

        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(mrz.toByteArray(Charsets.UTF_8))
        val seed = hash.copyOf(16)

        return Pair(deriveKey(seed, 1), deriveKey(seed, 2))
    }

    private fun deriveKey(seed: ByteArray, mode: Int): ByteArray {
        val c = ByteArray(4) { if (it == 3) mode.toByte() else 0 }
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(seed + c)
        val key = ByteArray(24)
        hash.copyInto(key, 0, 0, 16)
        hash.copyInto(key, 16, 0, 8)
        adjustDes3Parity(key)
        return key
    }

    private fun deriveSessionKey(seed: ByteArray, mode: Int): ByteArray = deriveKey(seed, mode)

    private fun adjustDes3Parity(key: ByteArray) {
        for (i in key.indices) {
            var b = key[i].toInt() and 0xFF
            var count = 0
            var tmp = b shr 1
            while (tmp != 0) {
                count += tmp and 1
                tmp = tmp shr 1
            }
            if (count % 2 == 0) b = b xor 1
            key[i] = b.toByte()
        }
    }

    private fun encryptDes3(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("DESede/CBC/NoPadding")
        val keySpec = DESedeKeySpec(key)
        val secretKey = SecretKeyFactory.getInstance("DESede").generateSecret(keySpec)
        val iv = IvParameterSpec(ByteArray(8))
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
        return cipher.doFinal(data)
    }

    private fun decryptDes3(key: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("DESede/CBC/NoPadding")
        val keySpec = DESedeKeySpec(key)
        val secretKey = SecretKeyFactory.getInstance("DESede").generateSecret(keySpec)
        val iv = IvParameterSpec(ByteArray(8))
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
        return cipher.doFinal(data)
    }

    private fun computeMac(key: ByteArray, data: ByteArray): ByteArray {
        val macKey = key.copyOf(16)
        val mac = ISO9797Alg3Mac(DESedeEngine(), 64, ISO7816d4Padding())
        mac.init(KeyParameter(macKey))
        mac.update(data, 0, data.size)
        val result = ByteArray(8)
        mac.doFinal(result, 0)
        return result
    }

    private fun computeCheckDigit(value: String): Char {
        val weights = intArrayOf(7, 3, 1)
        val charValues = mapOf(
            '<' to 0,
            '0' to 0, '1' to 1, '2' to 2, '3' to 3, '4' to 4,
            '5' to 5, '6' to 6, '7' to 7, '8' to 8, '9' to 9,
            'A' to 10, 'B' to 11, 'C' to 12, 'D' to 13, 'E' to 14, 'F' to 15,
            'G' to 16, 'H' to 17, 'I' to 18, 'J' to 19, 'K' to 20, 'L' to 21,
            'M' to 22, 'N' to 23, 'O' to 24, 'P' to 25, 'Q' to 26, 'R' to 27,
            'S' to 28, 'T' to 29, 'U' to 30, 'V' to 31, 'W' to 32, 'X' to 33,
            'Y' to 34, 'Z' to 35,
        )
        var sum = 0
        for (i in value.indices) {
            sum += (charValues[value[i]] ?: 0) * weights[i % 3]
        }
        return ('0' + (sum % 10))
    }
}

/**
 * Holds the session keys and send sequence counter established during BAC.
 *
 * @property kEnc 3DES encryption key for secure messaging.
 * @property kMac 3DES MAC key for secure messaging.
 * @property ssc 8-byte send sequence counter initialised from the BAC exchange.
 */
data class BacSession(
    val kEnc: ByteArray,
    val kMac: ByteArray,
    val ssc: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BacSession) return false
        return kEnc.contentEquals(other.kEnc) &&
            kMac.contentEquals(other.kMac) &&
            ssc.contentEquals(other.ssc)
    }

    override fun hashCode(): Int {
        var result = kEnc.contentHashCode()
        result = 31 * result + kMac.contentHashCode()
        result = 31 * result + ssc.contentHashCode()
        return result
    }
}
