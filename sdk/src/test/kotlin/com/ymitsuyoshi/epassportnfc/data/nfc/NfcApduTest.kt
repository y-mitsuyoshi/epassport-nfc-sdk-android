package com.ymitsuyoshi.epassportnfc.data.nfc

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.ymitsuyoshi.epassportnfc.core.EPassportException

class NfcApduCommandTest {

    @Test
    fun `toBytes serialises 4-byte header-only command correctly`() {
        val cmd = NfcApduCommand(cla = 0x00, ins = 0x84.toByte(), p1 = 0x00, p2 = 0x00, le = 8)
        val bytes = cmd.toBytes()
        assertArrayEquals(byteArrayOf(0x00, 0x84.toByte(), 0x00, 0x00, 0x08), bytes)
    }

    @Test
    fun `toBytes serialises command with data and Le`() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val cmd = NfcApduCommand(cla = 0x00, ins = 0x82.toByte(), p1 = 0x00, p2 = 0x00, data = data, le = 40)
        val bytes = cmd.toBytes()
        // CLA INS P1 P2 Lc Data Le
        assertEquals(4 + 1 + 3 + 1, bytes.size)
        assertEquals(0x03, bytes[4].toInt() and 0xFF)  // Lc
        assertEquals(0x28, bytes[8].toInt() and 0xFF)  // Le = 40
    }

    @Test
    fun `selectApplication factory builds correct command`() {
        val aid = byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x02, 0x47, 0x10, 0x01)
        val cmd = NfcApduCommand.selectApplication(aid)
        assertEquals(0x00, cmd.cla.toInt() and 0xFF)
        assertEquals(0xA4, cmd.ins.toInt() and 0xFF)
        assertEquals(0x04, cmd.p1.toInt() and 0xFF)
    }

    @Test
    fun `readBinary factory encodes offset correctly`() {
        val cmd = NfcApduCommand.readBinary(offset = 256, length = 128)
        assertEquals(0x01, cmd.p1.toInt() and 0xFF)  // high byte of 256
        assertEquals(0x00, cmd.p2.toInt() and 0xFF)  // low byte of 256
        assertEquals(128, cmd.le)
    }
}

class NfcApduResponseTest {

    @Test
    fun `fromBytes parses success response`() {
        val raw = byteArrayOf(0xAB.toByte(), 0xCD.toByte(), 0x90.toByte(), 0x00)
        val response = NfcApduResponse.fromBytes(raw)
        assertArrayEquals(byteArrayOf(0xAB.toByte(), 0xCD.toByte()), response.data)
        assertEquals(0x9000, response.statusWord)
        assertTrue(response.isSuccess)
    }

    @Test
    fun `fromBytes parses failure response`() {
        val raw = byteArrayOf(0x6A.toByte(), 0x82.toByte())
        val response = NfcApduResponse.fromBytes(raw)
        assertEquals(0, response.data.size)
        assertEquals(0x6A82, response.statusWord)
        assertFalse(response.isSuccess)
    }

    @Test(expected = EPassportException.NfcCommunicationException::class)
    fun `fromBytes throws for response shorter than 2 bytes`() {
        NfcApduResponse.fromBytes(byteArrayOf(0x90.toByte()))
    }

    @Test(expected = EPassportException.NfcCommunicationException::class)
    fun `requireSuccess throws on non-9000 status`() {
        val response = NfcApduResponse(data = byteArrayOf(), sw1 = 0x6A.toByte(), sw2 = 0x82.toByte())
        response.requireSuccess("test operation")
    }

    @Test
    fun `requireSuccess does not throw on 9000`() {
        val response = NfcApduResponse(data = byteArrayOf(), sw1 = 0x90.toByte(), sw2 = 0x00)
        response.requireSuccess("test operation")
    }
}
