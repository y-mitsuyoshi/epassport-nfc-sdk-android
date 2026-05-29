package com.example.epassport.data.auth

import com.example.epassport.domain.port.NfcTransceiver
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecureMessagingTest {

    private fun createSm(): SecureMessaging {
        return SecureMessaging(
            delegate = mockk(relaxed = true),
            ksEnc = ByteArray(16),
            ksMac = ByteArray(16),
            ssc = ByteArray(8)
        )
    }

    @Test
    fun parseApdu_extendedLeOnly_parsesCorrectly() {
        // [CLA=00, INS=B0, P1=00, P2=00, 0x00, LeHi=0x10, LeLo=0x00] => Le=4096
        val cmd = byteArrayOf(0x00, 0xB0, 0x00, 0x00, 0x00, 0x10, 0x00)
        val result = createSm().parseApdu(cmd)
        assertEquals(0, result.lc)
        assertEquals(4096, result.le)
        assertNull(result.dataField)
    }

    @Test
    fun parseApdu_extendedLeZero_means65536() {
        val cmd = byteArrayOf(0x00, 0xB0, 0x00, 0x00, 0x00, 0x00, 0x00)
        val result = createSm().parseApdu(cmd)
        assertEquals(0, result.lc)
        assertEquals(65536, result.le)
        assertNull(result.dataField)
    }

    @Test
    fun parseApdu_extendedLeMax65535() {
        val cmd = byteArrayOf(0x00, 0xB0, 0x00, 0x00, 0x00, 0xFF.toByte(), 0xFF.toByte())
        val result = createSm().parseApdu(cmd)
        assertEquals(65535, result.le)
    }

    @Test
    fun parseApdu_shortLeOnly_parsesCorrectly() {
        val cmd = byteArrayOf(0x00, 0xB0, 0x00, 0x00, 0xFF.toByte())
        val result = createSm().parseApdu(cmd)
        assertEquals(0, result.lc)
        assertEquals(255, result.le)
        assertNull(result.dataField)
    }

    @Test
    fun parseApdu_shortLcDataWithLe_parsesCorrectly() {
        val cmd = byteArrayOf(0x00, 0xA4, 0x04, 0x0C, 0x03, 0x01, 0x02, 0x03, 0x00)
        val result = createSm().parseApdu(cmd)
        assertEquals(3, result.lc)
        assertEquals(0, result.le)
        assertEquals(listOf(0x01.toByte(), 0x02.toByte(), 0x03.toByte()), result.dataField!!.toList())
    }

    @Test
    fun parseApdu_shortLcDataWithoutLe_parsesCorrectly() {
        val cmd = byteArrayOf(0x00, 0xA4, 0x02, 0x0C, 0x02, 0x01, 0x01)
        val result = createSm().parseApdu(cmd)
        assertEquals(2, result.lc)
        assertEquals(-1, result.le)
        assertEquals(listOf(0x01.toByte(), 0x01.toByte()), result.dataField!!.toList())
    }

    @Test
    fun parseApdu_case5Short_parsesCorrectly() {
        // GET CHALLENGE with Le=8
        val cmd = byteArrayOf(0x00, 0x84, 0x00, 0x00, 0x08)
        val result = createSm().parseApdu(cmd)
        assertEquals(0, result.lc)
        assertEquals(8, result.le)
        assertNull(result.dataField)
    }
}
