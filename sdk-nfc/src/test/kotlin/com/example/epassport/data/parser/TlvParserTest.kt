package com.example.epassport.data.parser

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class TlvParserTest {

    @Test
    fun parseTag_singleByte() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val result = TlvParser.readTag(data, 0)
        assertEquals(0x01, result.tag)
        assertEquals(1, result.bytesRead)
    }

    @Test
    fun parseTag_twoBytes() {
        val data = byteArrayOf(0x5F, 0x1F, 0x02)
        val result = TlvParser.readTag(data, 0)
        assertEquals(0x5F1F, result.tag)
        assertEquals(2, result.bytesRead)
    }

    @Test
    fun parseLength_singleByte() {
        val data = byteArrayOf(0x7F)
        val result = TlvParser.readLength(data, 0)
        assertEquals(127, result.length)
        assertEquals(1, result.bytesRead)
    }

    @Test
    fun parseLength_twoBytes() {
        // 0x81 followed by 1 length byte
        val data = byteArrayOf(0x81.toByte(), 0x80.toByte())
        val result = TlvParser.readLength(data, 0)
        assertEquals(128, result.length)
        assertEquals(2, result.bytesRead)
    }

    @Test
    fun parseLength_threeBytes() {
        // 0x82 followed by 2 length bytes
        val data = byteArrayOf(0x82.toByte(), 0x02.toByte(), 0x01.toByte())
        val result = TlvParser.readLength(data, 0)
        assertEquals(513, result.length)
        assertEquals(3, result.bytesRead)
    }

    @Test
    fun parseSimpleTree() {
        // Tag 0x5F1F, Length 0x02, Value [0x11, 0x22]
        val data = byteArrayOf(0x5F, 0x1F, 0x02, 0x11, 0x22, 0x00, 0x00) // trailing zeros to check parser stops neatly
        val nodes = TlvParser.parse(data.copyOfRange(0, 5))
        
        assertEquals(1, nodes.size)
        assertEquals(0x5F1F, nodes[0].tag)
        assertArrayEquals(byteArrayOf(0x11, 0x22), nodes[0].value)
    }
}
