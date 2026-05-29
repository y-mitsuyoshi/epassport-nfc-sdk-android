package com.example.epassport.data.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class ApduCommandTest {

    @Test
    fun readBinaryExtended_le256_generatesCorrectBytes() {
        val cmd = ApduCommand.readBinaryExtended(0, 256)
        assertEquals(7, cmd.size)
        assertEquals(0x00.toByte(), cmd[0]) // CLA
        assertEquals(0xB0.toByte(), cmd[1]) // INS
        assertEquals(0x00.toByte(), cmd[2]) // P1
        assertEquals(0x00.toByte(), cmd[3]) // P2
        assertEquals(0x00.toByte(), cmd[4]) // Extended marker
        assertEquals(0x01.toByte(), cmd[5]) // LeHi
        assertEquals(0x00.toByte(), cmd[6]) // LeLo
    }

    @Test
    fun readBinaryExtended_le65535_generatesCorrectBytes() {
        val cmd = ApduCommand.readBinaryExtended(0x1234, 65535)
        assertEquals(0x12.toByte(), cmd[2])
        assertEquals(0x34.toByte(), cmd[3])
        assertEquals(0xFF.toByte(), cmd[5])
        assertEquals(0xFF.toByte(), cmd[6])
    }

    @Test
    fun readBinaryExtended_le0_generatesZeroLeMeans65536() {
        val cmd = ApduCommand.readBinaryExtended(0, 0)
        assertEquals(0x00.toByte(), cmd[5])
        assertEquals(0x00.toByte(), cmd[6])
    }

    @Test(expected = IllegalArgumentException::class)
    fun readBinaryExtended_leNegative_throws() {
        ApduCommand.readBinaryExtended(0, -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun readBinaryExtended_leTooLarge_throws() {
        ApduCommand.readBinaryExtended(0, 65537)
    }

    @Test(expected = IllegalArgumentException::class)
    fun readBinaryExtended_offsetNegative_throws() {
        ApduCommand.readBinaryExtended(-1, 256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun readBinaryExtended_offsetAt32768_throws() {
        ApduCommand.readBinaryExtended(32768, 256)
    }

    @Test
    fun readBinary_shortFormat_generatesCorrectBytes() {
        val cmd = ApduCommand.readBinary(0x1234, 255)
        assertEquals(5, cmd.size)
        assertEquals(0x12.toByte(), cmd[2])
        assertEquals(0x34.toByte(), cmd[3])
        assertEquals(0xFF.toByte(), cmd[4])
    }

    @Test
    fun selectFile_generatesCorrectBytes() {
        val cmd = ApduCommand.selectFile(byteArrayOf(0x01, 0x01))
        assertEquals(7, cmd.size)
        assertEquals(0x00.toByte(), cmd[0])
        assertEquals(0xA4.toByte(), cmd[1])
        assertEquals(0x02.toByte(), cmd[2])
        assertEquals(0x0C.toByte(), cmd[3])
        assertEquals(0x02.toByte(), cmd[4])
        assertEquals(0x01.toByte(), cmd[5])
        assertEquals(0x01.toByte(), cmd[6])
    }
}
