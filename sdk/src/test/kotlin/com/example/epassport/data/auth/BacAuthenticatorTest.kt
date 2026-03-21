package com.example.epassport.data.auth

import com.example.epassport.domain.model.BacKey
import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.util.CryptoUtils
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import java.security.Security
import javax.crypto.Cipher

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
    fun authenticate_successful_icaoAppendixD() { runBlocking {
        // Based on ICAO 9303 Part 11, Appendix D.3 Test Vectors
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

        // Mock GET CHALLENGE response (returns RND.IC)
        val getChallengeResponse = rndIc + byteArrayOf(0x90.toByte(), 0x00.toByte())
        coEvery { transceiver.transceive(match { it[1] == 0x84.toByte() }) } returns getChallengeResponse

        // We can't inject the authenticator's RND.IFD, so we must grab it from the command it tries to send.
        coEvery { transceiver.transceive(match { it[1] == 0x82.toByte() }) } answers {
            val command = arg<ByteArray>(0)
            val cmdData = command.copyOfRange(5, command.size) // data part
            val eIfd = cmdData.copyOfRange(0, 32)
            
            // Decrypt eIFD to extract RND.IFD and K.IFD
            val cipher = Cipher.getInstance("DESede/CBC/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, CryptoUtils.get3desKey(kEnc), CryptoUtils.getIv(kEnc, kMac))
            val decryptedData = cipher.doFinal(eIfd)
            
            val rndIfd = decryptedData.copyOfRange(0, 8)
            val kIfd = decryptedData.copyOfRange(8, 24)

            // Now, construct the PICC's response (R_ifd)
            val rPicc = rndIfd + rndIc + kIfd
            
            val cipherEnc = Cipher.getInstance("DESede/CBC/NoPadding")
            cipherEnc.init(Cipher.ENCRYPT_MODE, CryptoUtils.get3desKey(kEnc), CryptoUtils.getIv(kEnc, kMac))
            val ePicc = cipherEnc.doFinal(rPicc)
            
            val mPicc = CryptoUtils.calculateMac(kMac, ePicc)
            
            val response = ePicc + mPicc + 0x90.toByte() + 0x00.toByte()
            response
        }

        val secureMessaging = authenticator.authenticate(transceiver, bacKey)
        assertNotNull(secureMessaging)
    } }
}

