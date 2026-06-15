package com.example.epassport.data.auth

import com.example.epassport.domain.port.NfcTransceiver
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.Security

class AesCmacSecureMessagingTest {

    init {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    @Test
    fun constructor_startsWithGivenSsc() {
        val delegate = mockk<NfcTransceiver>(relaxed = true)
        every { delegate.isConnected } returns true
        val ssc = ByteArray(16) { it.toByte() }
        val sm = AesCmacSecureMessaging(delegate, ByteArray(32), ByteArray(32), ssc)

        assertTrue(sm.isConnected)
        sm.close()
    }

    @Test
    fun close_zeroizesKeys() {
        val delegate = mockk<NfcTransceiver>(relaxed = true)
        every { delegate.isConnected } returns true
        val ksEnc = ByteArray(32) { 0xAA.toByte() }
        val ksMac = ByteArray(32) { 0xBB.toByte() }
        val ssc = ByteArray(16) { 0xCC.toByte() }
        // Pass the same array instances so close() zeroes the caller's arrays.
        val sm = AesCmacSecureMessaging(delegate, ksEnc, ksMac, ssc)

        sm.close()

        assertTrue(ksEnc.all { it == 0x00.toByte() })
        assertTrue(ksMac.all { it == 0x00.toByte() })
        // ssc is copied internally; verify the internal copy was zeroized via reflection.
        val internalSsc = AesCmacSecureMessaging::class.java.getDeclaredField("ssc").apply { isAccessible = true }.get(sm) as ByteArray
        assertTrue(internalSsc.all { it == 0x00.toByte() })
    }

    @Test
    fun transceive_wrapsCommandWithSmStructure() { runBlocking {
        val ksMac = ByteArray(32) { 0x01.toByte() }
        val ssc = ByteArray(16) // all zeros; transceive increments to 00...01

        val delegate = mockk<NfcTransceiver>(relaxed = true)
        coEvery { delegate.transceive(any()) } answers {
            // Build a valid SM response with DO99 and DO8E.
            // transceive() increments SSC twice: once before sending (=> 0..01),
            // once before unwrapping the response (=> 0..02).
            val do99 = byteArrayOf(0x99.toByte(), 0x02.toByte(), 0x90.toByte(), 0x00.toByte())
            val receiveSsc = ssc.copyOf().apply { this[15] = 0x02 }
            val paddedMacInput = padIso7816(receiveSsc + do99)
            val mac = calculateCmac(ksMac, paddedMacInput)
            val do8e = byteArrayOf(0x8E.toByte(), 0x10.toByte()) + mac
            val data = byteArrayOf(0x7C.toByte()) + byteArrayOf((do99.size + do8e.size).toByte()) + do99 + do8e
            data + byteArrayOf(0x90.toByte(), 0x00.toByte())
        }

        val sm = AesCmacSecureMessaging(delegate, ByteArray(32), ksMac, ssc)
        val cmd = byteArrayOf(0x00, 0xB0.toByte(), 0x00, 0x00, 0x00)

        val result = sm.transceive(cmd)

        assertEquals(0x90.toByte(), result[0])
        assertEquals(0x00.toByte(), result[1])
        sm.close()
    } }

    private fun padIso7816(data: ByteArray): ByteArray {
        val padLength = 8 - (data.size % 8)
        val padded = ByteArray(data.size + padLength)
        System.arraycopy(data, 0, padded, 0, data.size)
        padded[data.size] = 0x80.toByte()
        return padded
    }

    private fun calculateCmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = org.bouncycastle.crypto.macs.CMac(org.bouncycastle.crypto.engines.AESEngine())
        mac.init(org.bouncycastle.crypto.params.KeyParameter(key))
        mac.update(data, 0, data.size)
        val result = ByteArray(16)
        mac.doFinal(result, 0)
        return result
    }
}
