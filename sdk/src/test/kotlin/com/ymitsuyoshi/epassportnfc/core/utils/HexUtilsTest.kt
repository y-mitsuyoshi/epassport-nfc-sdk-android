package com.ymitsuyoshi.epassportnfc.core.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HexUtilsTest {

    @Test
    fun `toHexString converts empty array to empty string`() {
        assertEquals("", HexUtils.toHexString(byteArrayOf()))
    }

    @Test
    fun `toHexString produces uppercase hex without separators`() {
        val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertEquals("DEADBEEF", HexUtils.toHexString(bytes))
    }

    @Test
    fun `toHexString handles single byte correctly`() {
        assertEquals("0F", HexUtils.toHexString(byteArrayOf(0x0F)))
        assertEquals("FF", HexUtils.toHexString(byteArrayOf(0xFF.toByte())))
        assertEquals("00", HexUtils.toHexString(byteArrayOf(0x00)))
    }

    @Test
    fun `fromHexString decodes correctly`() {
        val expected = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertArrayEquals(expected, HexUtils.fromHexString("DEADBEEF"))
    }

    @Test
    fun `fromHexString is case-insensitive`() {
        val expected = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        assertArrayEquals(expected, HexUtils.fromHexString("abcd"))
        assertArrayEquals(expected, HexUtils.fromHexString("ABCD"))
        assertArrayEquals(expected, HexUtils.fromHexString("AbCd"))
    }

    @Test
    fun `fromHexString throws for odd-length input`() {
        assertThrows(IllegalArgumentException::class.java) {
            HexUtils.fromHexString("ABC")
        }
    }

    @Test
    fun `fromHexString throws for invalid hex characters`() {
        assertThrows(IllegalArgumentException::class.java) {
            HexUtils.fromHexString("GG")
        }
    }

    @Test
    fun `fromHexString decodes empty string to empty array`() {
        assertArrayEquals(byteArrayOf(), HexUtils.fromHexString(""))
    }

    @Test
    fun `toHexString and fromHexString are inverse operations`() {
        val original = byteArrayOf(0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
            0x88.toByte(), 0x99.toByte(), 0xAA.toByte(), 0xBB.toByte(),
            0xCC.toByte(), 0xDD.toByte(), 0xEE.toByte(), 0xFF.toByte())
        assertArrayEquals(original, HexUtils.fromHexString(HexUtils.toHexString(original)))
    }

    @Test
    fun `toFormattedHexString uses colon separator`() {
        val bytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())
        assertEquals("DE:AD:BE:EF", HexUtils.toFormattedHexString(bytes))
    }

    @Test
    fun `toFormattedHexString handles empty array`() {
        assertEquals("", HexUtils.toFormattedHexString(byteArrayOf()))
    }
}
