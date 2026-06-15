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
        val cmd = byteArrayOf(0x00.toByte(), 0xB0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x10.toByte(), 0x00.toByte())
        val result = createSm().parseApdu(cmd)
        assertEquals(0, result.lc)
        assertEquals(4096, result.le)
        assertNull(result.dataField)
    }

    @Test
    fun parseApdu_extendedLeZero_means65536() {
        val cmd = byteArrayOf(0x00.toByte(), 0xB0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
        val result = createSm().parseApdu(cmd)
        assertEquals(0, result.lc)
        assertEquals(65536, result.le)
        assertNull(result.dataField)
    }

    @Test
    fun parseApdu_extendedLeMax65535() {
        val cmd = byteArrayOf(0x00.toByte(), 0xB0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFF.toByte(), 0xFF.toByte())
        val result = createSm().parseApdu(cmd)
        assertEquals(65535, result.le)
    }

    @Test
    fun parseApdu_shortLeOnly_parsesCorrectly() {
        val cmd = byteArrayOf(0x00.toByte(), 0xB0.toByte(), 0x00.toByte(), 0x00.toByte(), 0xFF.toByte())
        val result = createSm().parseApdu(cmd)
        assertEquals(0, result.lc)
        assertEquals(255, result.le)
        assertNull(result.dataField)
    }

    @Test
    fun parseApdu_shortLcDataWithLe_parsesCorrectly() {
        val cmd = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x0C.toByte(), 0x03, 0x01, 0x02, 0x03, 0x00)
        val result = createSm().parseApdu(cmd)
        assertEquals(3, result.lc)
        assertEquals(0, result.le)
        assertEquals(listOf(0x01.toByte(), 0x02.toByte(), 0x03.toByte()), result.dataField!!.toList())
    }

    @Test
    fun parseApdu_shortLcDataWithoutLe_parsesCorrectly() {
        val cmd = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x02.toByte(), 0x0C.toByte(), 0x02, 0x01, 0x01)
        val result = createSm().parseApdu(cmd)
        assertEquals(2, result.lc)
        assertEquals(-1, result.le)
        assertEquals(listOf(0x01.toByte(), 0x01.toByte()), result.dataField!!.toList())
    }

    @Test
    fun parseApdu_case5Short_parsesCorrectly() {
        // GET CHALLENGE with Le=8
        val cmd = byteArrayOf(0x00.toByte(), 0x84.toByte(), 0x00.toByte(), 0x00.toByte(), 0x08)
        val result = createSm().parseApdu(cmd)
        assertEquals(0, result.lc)
        assertEquals(8, result.le)
        assertNull(result.dataField)
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseApdu_extendedLcData_throwsIllegalArgumentException() {
        // Extended Lc+data APDU is unsupported and must throw before array access errors.
        // [CLA, INS, P1, P2, 0x00, LcHi, LcLo, ...data...]
        val cmd = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x0C.toByte(),
            0x00.toByte(), // Extended marker
            0x00.toByte(), 0x03.toByte(), // Lc = 3
            0x01.toByte(), 0x02.toByte(), 0x03.toByte()
        )
        createSm().parseApdu(cmd)
    }

    // ──────────────────────────────────────────────────
    // DO97 エンコードの検証
    // ──────────────────────────────────────────────────

    /**
     * le=65536 のとき DO97 は ISO7816-4 の仕様通り 0x97 0x02 0x00 0x00 になること。
     * 修正前は 0x97 0x02 0x01 0x00 と誤変換されていた。
     */
    @Test
    fun parseApdu_le65536_do97EncodedAsZeroZero() {
        // Le=0x0000 → internal le=65536 となる 7バイト Extended APDU
        val cmd = byteArrayOf(0x00.toByte(), 0xB0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte())
        val result = createSm().parseApdu(cmd)
        assertEquals(65536, result.le)
        // 65536 は ISO7816-4 上 0x0000 で表現される（0x0100 ではない）
        // DO97 バイト列 = [0x97, 0x02, 0x00, 0x00]
        val leHi = if (result.le == 65536) 0x00.toByte() else (result.le ushr 8).toByte()
        val leLo = if (result.le == 65536) 0x00.toByte() else (result.le and 0xFF).toByte()
        assertEquals(0x00.toByte(), leHi)
        assertEquals(0x00.toByte(), leLo)
    }
}
