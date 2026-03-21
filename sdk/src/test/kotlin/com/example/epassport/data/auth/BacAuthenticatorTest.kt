package com.example.epassport.data.auth

import com.example.epassport.domain.exception.AuthenticationException
import com.example.epassport.domain.model.BacKey
import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.util.CryptoUtils
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class BacAuthenticatorTest {

    private lateinit var transceiver: NfcTransceiver
    private val authenticator = BacAuthenticator()

    init {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    @Before
    fun setUp() {
        transceiver = mockk()
    }

    @Test
    fun authenticate_successful_returnsSecureMessaging() { runBlocking {
        // ICAO 9303 Part 11, Appendix D.3 Test Vectors
        val kEnc = byteArrayOf(
            0xAB.toByte(), 0x94.toByte(), 0xFD.toByte(), 0xEC.toByte(),
            0xF2.toByte(), 0x67.toByte(), 0x4F.toByte(), 0xDF.toByte(),
            0xB9.toByte(), 0xB3.toByte(), 0x91.toByte(), 0xF8.toByte(),
            0x5D.toByte(), 0x7F.toByte(), 0x76.toByte(), 0xF2.toByte()
        )
        val kMac = byteArrayOf(
            0x79.toByte(), 0x62.toByte(), 0xD9.toByte(), 0xEC.toByte(),
            0xE0.toByte(), 0x3D.toByte(), 0x1A.toByte(), 0xCD.toByte(),
            0x4C.toByte(), 0x76.toByte(), 0x08.toByte(), 0x9D.toByte(),
            0xCE.toByte(), 0x13.toByte(), 0x15.toByte(), 0x43.toByte()
        )
        val bacKey = BacKey(kEnc, kMac)

        val rndIc = byteArrayOf(
            0x46.toByte(), 0x0F.toByte(), 0x88.toByte(), 0x39.toByte(),
            0x8C.toByte(), 0x12.toByte(), 0xBF.toByte(), 0x90.toByte()
        )

        // Mock GET CHALLENGE response (returns RND.IC + SW 9000)
        val getChallengeResponse = rndIc + byteArrayOf(0x90.toByte(), 0x00.toByte())
        coEvery { transceiver.transceive(match { it[1] == 0x84.toByte() }) } returns getChallengeResponse

        // Mock MUTUAL AUTHENTICATE: decrypt IFD's command, build PICC response
        coEvery { transceiver.transceive(match { it[1] == 0x82.toByte() }) } answers {
            val command = arg<ByteArray>(0)
            val cmdData = command.copyOfRange(5, 5 + 40) // Lc=40, data part
            val eIfd = cmdData.copyOfRange(0, 32)

            // Decrypt eIFD to extract S = RND.IFD || RND.IC || K.IFD (actually RND.IC || RND.IFD || K.IFD)
            val iv = ByteArray(8)
            val secretKey = SecretKeySpec(kEnc, "DESede")
            val cipher = Cipher.getInstance("DESede/CBC/NoPadding", "BC")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            val sDecrypted = cipher.doFinal(eIfd)

            // s = RND.IC || RND.IFD || K.IFD
            val rndIfdDecrypted = sDecrypted.copyOfRange(8, 16)
            val kIfd = sDecrypted.copyOfRange(16, 32)

            // Build R = RND.IC || RND.IFD || K.IC (using kIfd as mock K.IC)
            val rPicc = ByteArray(32)
            System.arraycopy(rndIc, 0, rPicc, 0, 8)
            System.arraycopy(rndIfdDecrypted, 0, rPicc, 8, 8)
            System.arraycopy(kIfd, 0, rPicc, 16, 16)

            val cipherEnc = Cipher.getInstance("DESede/CBC/NoPadding", "BC")
            cipherEnc.init(Cipher.ENCRYPT_MODE, secretKey, IvParameterSpec(iv))
            val ePicc = cipherEnc.doFinal(rPicc)

            val mPicc = CryptoUtils.calculateMac(kMac, CryptoUtils.pad(ePicc))

            ePicc + mPicc + byteArrayOf(0x90.toByte(), 0x00.toByte())
        }

        val secureMessaging = authenticator.authenticate(transceiver, bacKey)
        assertNotNull(secureMessaging)
        assertTrue(secureMessaging is SecureMessaging)
    } }

    @Test(expected = AuthenticationException::class)
    fun authenticate_shortChallengeResponse_throwsException() { runBlocking {
        val bacKey = BacKey(ByteArray(16), ByteArray(16))

        // Return too short response for GET CHALLENGE
        coEvery { transceiver.transceive(match { it[1] == 0x84.toByte() }) } returns byteArrayOf(0x90.toByte(), 0x00.toByte())

        authenticator.authenticate(transceiver, bacKey)
    } }

    @Test(expected = AuthenticationException::class)
    fun authenticate_shortAuthResponse_throwsException() { runBlocking {
        val kEnc = ByteArray(16) { 0x01.toByte() }
        val kMac = ByteArray(16) { 0x02.toByte() }
        val bacKey = BacKey(kEnc, kMac)

        val rndIc = ByteArray(8) { 0x11.toByte() }
        val getChallengeResponse = rndIc + byteArrayOf(0x90.toByte(), 0x00.toByte())
        coEvery { transceiver.transceive(match { it[1] == 0x84.toByte() }) } returns getChallengeResponse

        // Return too short response for MUTUAL AUTHENTICATE
        coEvery { transceiver.transceive(match { it[1] == 0x82.toByte() }) } returns byteArrayOf(0x90.toByte(), 0x00.toByte())

        authenticator.authenticate(transceiver, bacKey)
    } }
}
