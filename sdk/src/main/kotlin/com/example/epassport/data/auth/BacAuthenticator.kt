package com.example.epassport.data.auth

import com.example.epassport.data.nfc.ApduCommand
import com.example.epassport.domain.exception.AuthenticationException
import com.example.epassport.domain.model.BacKey
import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.domain.port.PassportAuthenticator
import com.example.epassport.util.CryptoUtils
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays

class BacAuthenticator : PassportAuthenticator {

    override suspend fun authenticate(transceiver: NfcTransceiver, bacKey: BacKey): NfcTransceiver {
        // 1. GET CHALLENGE
        val challengeResponse = transceiver.transceive(ApduCommand.getChallenge())
        if (challengeResponse.size < 10) {
            throw AuthenticationException("Invalid GET CHALLENGE response length")
        }
        val rndIc = challengeResponse.copyOfRange(0, 8)

        // 2. Generate RND.IFD and K.IFD
        val random = SecureRandom()
        val rndIfd = ByteArray(8)
        val kIfd = ByteArray(16)
        random.nextBytes(rndIfd)
        random.nextBytes(kIfd)

        // 3. Build S = RND.IC || RND.IFD || K.IFD
        val s = ByteArray(32)
        System.arraycopy(rndIc, 0, s, 0, 8)
        System.arraycopy(rndIfd, 0, s, 8, 8)
        System.arraycopy(kIfd, 0, s, 16, 16)

        // 4. Encrypt S and compute MAC
        val eS = CryptoUtils.encrypt3DesCbc(bacKey.encKey, s)
        val macS = CryptoUtils.calculateMac(bacKey.macKey, CryptoUtils.pad(eS))

        // 5. EXTERNAL AUTHENTICATE
        val authData = ByteArray(40)
        System.arraycopy(eS, 0, authData, 0, 32)
        System.arraycopy(macS, 0, authData, 32, 8)

        val authCmd = ApduCommand.mutualAuthenticate(authData)
        val authCmdResponse = transceiver.transceive(authCmd)

        if (authCmdResponse.size < 42) {
            throw AuthenticationException("Invalid EXTERNAL AUTHENTICATE response length")
        }

        val responseData = authCmdResponse.copyOfRange(0, 40)
        val eR = responseData.copyOfRange(0, 32)
        val macR = responseData.copyOfRange(32, 40)

        // 6. Verify MAC of Response
        val expectedMacR = CryptoUtils.calculateMac(bacKey.macKey, CryptoUtils.pad(eR))
        if (!Arrays.equals(macR, expectedMacR)) {
            throw AuthenticationException("MAC verification failed for EXTERNAL AUTHENTICATE response")
        }

        // 7. Decrypt R
        val r = CryptoUtils.decrypt3DesCbc(bacKey.encKey, eR)
        val rndIcResp = r.copyOfRange(0, 8)
        val rndIfdResp = r.copyOfRange(8, 16)
        val kIc = r.copyOfRange(16, 32)

        if (!Arrays.equals(rndIc, rndIcResp) || !Arrays.equals(rndIfd, rndIfdResp)) {
            throw AuthenticationException("RND.IC or RND.IFD mismatch in authentication response")
        }

        // 8. Derive Session Keys (KS.Enc, KS.Mac)
        val kSeed = ByteArray(16)
        for (i in 0 until 16) {
            kSeed[i] = (kIfd[i].toInt() xor kIc[i].toInt()).toByte()
        }

        val ksEnc = deriveSessionKey(kSeed, byteArrayOf(0x00, 0x00, 0x00, 0x01))
        val ksMac = deriveSessionKey(kSeed, byteArrayOf(0x00, 0x00, 0x00, 0x02))

        // 9. Initial Send Sequence Counter (SSC) = RND.IC[-4:] || RND.IFD[-4:]
        val ssc = ByteArray(8)
        System.arraycopy(rndIc, 4, ssc, 0, 4)
        System.arraycopy(rndIfd, 4, ssc, 4, 4)

        return SecureMessaging(transceiver, ksEnc, ksMac, ssc)
    }

    private fun deriveSessionKey(kSeed: ByteArray, counter: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update(kSeed)
        digest.update(counter)
        val hash = digest.digest()
        val keyBytes = hash.copyOfRange(0, 16)
        adjustParity(keyBytes)
        return keyBytes
    }

    private fun adjustParity(bytes: ByteArray) {
        for (i in bytes.indices) {
            val b = bytes[i].toInt()
            var bits = 0
            for (j in 1..7) {
                if (((b shr j) and 1) == 1) bits++
            }
            if (bits % 2 == 0) {
                bytes[i] = (b or 1).toByte()
            } else {
                bytes[i] = (b and -2).toByte()
            }
        }
    }
}
