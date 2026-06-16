package com.example.epassport.data.auth

import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.util.CryptoUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class SecureMessagingTest {

    init {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    @Test
    fun constructor_startsWithGivenSsc() {
        val delegate = mockk<NfcTransceiver>(relaxed = true)
        every { delegate.isConnected } returns true
        val ssc = ByteArray(8) { it.toByte() }
        val sm = SecureMessaging(delegate, ByteArray(16), ByteArray(16), ssc)

        assertTrue(sm.isConnected)
        sm.close()
    }

    @Test
    fun close_zeroizesKeys() {
        val delegate = mockk<NfcTransceiver>(relaxed = true)
        every { delegate.isConnected } returns true
        val ksEnc = ByteArray(16) { 0xAA.toByte() }
        val ksMac = ByteArray(16) { 0xBB.toByte() }
        val ssc = ByteArray(8) { 0xCC.toByte() }
        val sm = SecureMessaging(delegate, ksEnc, ksMac, ssc)

        sm.close()

        assertTrue(ksEnc.all { it == 0x00.toByte() })
        assertTrue(ksMac.all { it == 0x00.toByte() })
        val internalSsc = SecureMessaging::class.java.getDeclaredField("ssc").apply { isAccessible = true }.get(sm) as ByteArray
        assertTrue(internalSsc.all { it == 0x00.toByte() })
    }

    @Test
    fun transceive_wrapsCommandAndUnwrapsResponse() { runBlocking {
        val ksEnc = ByteArray(16) { 0x01.toByte() }
        val ksMac = ByteArray(16) { 0x02.toByte() }
        val ssc = ByteArray(8) // 00..00

        val delegate = mockk<NfcTransceiver>(relaxed = true)
        coEvery { delegate.transceive(any()) } answers {
            // Build a valid 3DES-SM response: DO99 + DO8E + SW9000.
            // transceive() increments SSC twice, so receive SSC is 00..02.
            val do99 = byteArrayOf(0x99.toByte(), 0x02.toByte(), 0x90.toByte(), 0x00.toByte())
            val receiveSsc = ssc.copyOf().apply { this[7] = 0x02 }
            val macData = CryptoUtils.pad(receiveSsc + byteArrayOf(0x99.toByte(), 0x02.toByte()) + do99.drop(2).toByteArray())
            val mac = CryptoUtils.calculateMac(ksMac, macData)
            val do8e = byteArrayOf(0x8E.toByte(), 0x08.toByte()) + mac
            do99 + do8e + byteArrayOf(0x90.toByte(), 0x00.toByte())
        }

        val sm = SecureMessaging(delegate, ksEnc, ksMac, ssc)
        val cmd = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x00)

        val result = sm.transceive(cmd)

        assertEquals(0x90.toByte(), result[0])
        assertEquals(0x00.toByte(), result[1])
        sm.close()
    } }

    @Test
    fun transceive_withDataField_encryptsAndDecrypts() { runBlocking {
        val ksEnc = ByteArray(16) { 0x03.toByte() }
        val ksMac = ByteArray(16) { 0x04.toByte() }
        val ssc = ByteArray(8)

        val plainData = byteArrayOf(0x11, 0x22, 0x33, 0x44)
        val delegate = mockk<NfcTransceiver>(relaxed = true)
        coEvery { delegate.transceive(any()) } answers {
            // Reconstruct the protected command to extract and decrypt DO87.
            val protectedCmd = arg<ByteArray>(0)
            val totalLc = protectedCmd[4].toInt() and 0xFF
            val cmdBody = protectedCmd.copyOfRange(5, 5 + totalLc)

            // Parse DO87
            var offset = 0
            val do87Tag = cmdBody[offset++].toInt() and 0xFF
            assertEquals(0x87, do87Tag)
            val do87Len = cmdBody[offset++].toInt() and 0xFF
            val do87Value = cmdBody.copyOfRange(offset, offset + do87Len)
            offset += do87Len
            // Skip DO97 and DO8E

            // Decrypt DO87 value (first byte is padding indicator)
            val encrypted = do87Value.copyOfRange(1, do87Value.size)
            val decryptedWithPad = CryptoUtils.decrypt3DesCbc(ksEnc, encrypted)
            val decrypted = CryptoUtils.unpad(decryptedWithPad)
            assertTrue(plainData.contentEquals(decrypted))

            // Build response with encrypted data
            val responsePlain = byteArrayOf(0xAA.toByte(), 0xBB.toByte())
            val paddedResponse = CryptoUtils.pad(responsePlain)
            val encryptedResponse = CryptoUtils.encrypt3DesCbc(ksEnc, paddedResponse)
            val do87RespValue = byteArrayOf(0x01) + encryptedResponse
            val do87Resp = byteArrayOf(0x87.toByte(), do87RespValue.size.toByte()) + do87RespValue
            val do99 = byteArrayOf(0x99.toByte(), 0x02.toByte(), 0x90.toByte(), 0x00.toByte())

            val receiveSsc = ssc.copyOf().apply { this[7] = 0x02 }
            val macStream = java.io.ByteArrayOutputStream()
            macStream.write(receiveSsc)
            macStream.write(do87Resp)
            macStream.write(do99)
            val mac = CryptoUtils.calculateMac(ksMac, CryptoUtils.pad(macStream.toByteArray()))
            val do8e = byteArrayOf(0x8E.toByte(), 0x08.toByte()) + mac
            do87Resp + do99 + do8e + byteArrayOf(0x90.toByte(), 0x00.toByte())
        }

        val sm = SecureMessaging(delegate, ksEnc, ksMac, ssc)
        val cmd = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x0C, plainData.size.toByte()) + plainData

        val result = sm.transceive(cmd)

        assertEquals(0xAA.toByte(), result[0])
        assertEquals(0xBB.toByte(), result[1])
        assertEquals(0x90.toByte(), result[2])
        assertEquals(0x00.toByte(), result[3])
        sm.close()
    } }

    @Test
    fun parseApdu_cases() {
        val sm = SecureMessaging(mockk(relaxed = true), ByteArray(16), ByteArray(16), ByteArray(8))

        // Short Le only
        val r1 = sm.parseApdu(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x10))
        assertEquals(0, r1.lc)
        assertEquals(0x10, r1.le)
        assertEquals(null, r1.dataField)

        // Short Lc + data + Le
        val data = byteArrayOf(0x11, 0x22, 0x33)
        val r2 = sm.parseApdu(byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x0C, data.size.toByte()) + data + byteArrayOf(0x00))
        assertEquals(data.size, r2.lc)
        assertEquals(0, r2.le)
        assertTrue(data.contentEquals(r2.dataField))

        // Extended Le only (65536)
        val r3 = sm.parseApdu(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00))
        assertEquals(65536, r3.le)

        // Unsupported Extended Lc+data (should throw IllegalArgumentException)
        try {
            sm.parseApdu(byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x02, 0x11, 0x22, 0x00, 0x00))
            org.junit.Assert.fail("Expected IllegalArgumentException to be thrown")
        } catch (e: IllegalArgumentException) {
            assertEquals("Extended Lc+data APDU is not supported by this SecureMessaging implementation", e.message)
        }

        sm.close()
    }
}
