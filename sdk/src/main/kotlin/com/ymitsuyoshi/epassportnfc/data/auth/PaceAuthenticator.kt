package com.ymitsuyoshi.epassportnfc.data.auth

import com.ymitsuyoshi.epassportnfc.core.EPassportException
import com.ymitsuyoshi.epassportnfc.domain.repository.BacKey

/**
 * Cipher Algorithm identifiers used in PACE.
 * Defined in BSI TR-03110 and ICAO Doc 9303 Part 11.
 */
enum class PaceCipherAlgorithm {
    AES_128_CBC,
    AES_192_CBC,
    AES_256_CBC,
}

/**
 * Key agreement protocol used in PACE.
 */
enum class PaceKeyAgreement {
    DH,
    ECDH,
}

/**
 * Holds the PACE session configuration advertised by the chip in the EF.CardAccess file.
 *
 * @property cipherAlgorithm The symmetric cipher algorithm to use.
 * @property keyAgreement The key-agreement protocol to use.
 * @property parameterId Object identifier of the domain parameters.
 */
data class PaceInfo(
    val cipherAlgorithm: PaceCipherAlgorithm,
    val keyAgreement: PaceKeyAgreement,
    val parameterId: Int,
)

/**
 * Implements the Password Authenticated Connection Establishment (PACE) protocol
 * as defined in BSI TR-03110 and ICAO Doc 9303 Part 11.
 *
 * PACE is a more secure alternative to BAC, using Diffie-Hellman or ECDH key agreement
 * with an authenticated key exchange to establish session keys.
 *
 * @param transceive Lambda that sends raw APDU bytes to the NFC chip and returns the response bytes.
 */
class PaceAuthenticator(
    private val transceive: suspend (ByteArray) -> ByteArray,
) {
    /**
     * Performs the PACE handshake using the MRZ data as the password (CAN or MRZ).
     *
     * @param bacKey The MRZ-derived key material used as the PACE password.
     * @param paceInfo The PACE configuration read from EF.CardAccess.
     * @return [PaceSession] with the negotiated session keys.
     * @throws EPassportException.PaceAuthenticationException if the handshake fails.
     */
    @Suppress("UnusedParameter")
    suspend fun authenticate(bacKey: BacKey, paceInfo: PaceInfo): PaceSession {
        throw EPassportException.UnsupportedFeatureException(
            "PACE with ${paceInfo.keyAgreement} / ${paceInfo.cipherAlgorithm} is not yet implemented"
        )
    }

    /**
     * Reads the EF.CardAccess file to determine whether PACE is supported by this chip.
     *
     * @return [PaceInfo] if the chip supports PACE, or null if only BAC is available.
     */
    suspend fun readPaceInfo(): PaceInfo? {
        return try {
            // TODO: Read and parse EF.CardAccess (FID 011C) to determine PACE support
            null
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Holds the session keys established during PACE.
 *
 * @property kEnc AES encryption key for secure messaging.
 * @property kMac AES MAC key for secure messaging.
 */
data class PaceSession(
    val kEnc: ByteArray,
    val kMac: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PaceSession) return false
        return kEnc.contentEquals(other.kEnc) && kMac.contentEquals(other.kMac)
    }

    override fun hashCode(): Int {
        var result = kEnc.contentHashCode()
        result = 31 * result + kMac.contentHashCode()
        return result
    }
}
