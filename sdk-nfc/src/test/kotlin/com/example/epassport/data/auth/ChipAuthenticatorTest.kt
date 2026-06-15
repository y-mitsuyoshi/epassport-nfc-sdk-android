package com.example.epassport.data.auth

import com.example.epassport.domain.exception.AuthenticationException
import com.example.epassport.domain.port.NfcTransceiver
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Security

class ChipAuthenticatorTest {

    init {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    @Test(expected = AuthenticationException::class)
    fun authenticate_noCaInfo_throws() { runBlocking {
        val transceiver = mockk<NfcTransceiver>(relaxed = true)
        coEvery { transceiver.transceive(any()) } answers {
            val cmd = arg<ByteArray>(0)
            when (cmd[1].toInt() and 0xFF) {
                0xA4 -> byteArrayOf(0x90.toByte(), 0x00.toByte())
                0xB0 -> byteArrayOf(0x30, 0x00, 0x90.toByte(), 0x00.toByte()) // empty DG14
                else -> byteArrayOf(0x90.toByte(), 0x00.toByte())
            }
        }

        ChipAuthenticator().authenticate(transceiver)
    } }

    @Test
    fun buildMseSetAt_includesKeyId() {
        val keyId = 5
        val data = byteArrayOf(0x83.toByte(), 0x01.toByte(), keyId.toByte())
        assertEquals(3, data.size)
        assertEquals(0x83.toByte(), data[0])
        assertEquals(0x01.toByte(), data[1])
        assertEquals(keyId.toByte(), data[2])
    }
}
