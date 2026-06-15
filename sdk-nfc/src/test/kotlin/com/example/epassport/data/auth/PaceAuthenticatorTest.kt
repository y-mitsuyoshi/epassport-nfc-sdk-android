package com.example.epassport.data.auth

import com.example.epassport.domain.exception.AuthenticationException
import com.example.epassport.domain.model.MrzData
import com.example.epassport.domain.port.NfcTransceiver
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Security

class PaceAuthenticatorTest {

    init {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    @Test(expected = AuthenticationException::class)
    fun authenticate_noPaceInfo_throws() { runBlocking {
        val transceiver = mockk<NfcTransceiver>(relaxed = true)
        coEvery { transceiver.transceive(any()) } answers {
            val cmd = arg<ByteArray>(0)
            when (cmd[1].toInt() and 0xFF) {
                0xA4 -> byteArrayOf(0x90.toByte(), 0x00.toByte()) // SELECT OK
                0xB0 -> byteArrayOf(0x30, 0x00, 0x90.toByte(), 0x00.toByte()) // empty SEQUENCE
                else -> byteArrayOf(0x90.toByte(), 0x00.toByte())
            }
        }

        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())
        PaceAuthenticator().authenticate(transceiver, mrzData)
    } }

    @Test
    fun buildPaceApdus_producesExpectedStructure() {
        val oid = "0.4.0.127.0.7.2.2.4.2.4" // ECDH-GM-AES-CMAC-256
        val oidBytes = oid.split(".").map { it.toInt().toByte() }.toByteArray()
        val apdu = com.example.epassport.data.nfc.ApduCommand.paceMseSetAt(oidBytes, 0x01)

        assertTrue(apdu.size > 5)
        assertTrue(apdu[1].toInt() and 0xFF == 0x22)
        assertTrue(apdu[2].toInt() and 0xFF == 0xC1)
        assertTrue(apdu[3].toInt() and 0xFF == 0xA4)
    }

    @Test
    fun paceGetNonce_apduStructure() {
        val apdu = com.example.epassport.data.nfc.ApduCommand.paceGetNonce()
        assertTrue(apdu[1].toInt() and 0xFF == 0x86)
        // Contains 0x7C 0x00
        assertTrue(apdu.any { (it.toInt() and 0xFF) == 0x7C })
    }
}
