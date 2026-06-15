package com.example.epassport.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Dg2DataTest {

    @Test
    fun clear_zeroesFaceImageBytes() {
        val faceImage = byteArrayOf(1, 2, 3, 4)
        val dg2Data = Dg2Data(faceImage, "image/jpeg")

        dg2Data.clear()

        assertArrayEquals(byteArrayOf(0, 0, 0, 0), dg2Data.faceImageBytes)
    }

    @Test
    fun toString_returnsMetadataButNotData() {
        val dg2Data = Dg2Data(byteArrayOf(1, 2, 3), "image/jp2")
        assertEquals("Dg2Data(mimeType=image/jp2, size=3 bytes)", dg2Data.toString())
    }

    @Test
    fun constructor_makesDefensiveCopy() {
        val original = byteArrayOf(1, 2, 3, 4)
        val dg2Data = Dg2Data(original, "image/jpeg")

        original[0] = 0xFF.toByte()

        assertEquals(1, dg2Data.faceImageBytes[0].toInt() and 0xFF)
    }

    @Test
    fun toBase64AndClear_returnsBase64AndZeroesBytes() {
        val faceImage = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val dg2Data = Dg2Data(faceImage, "image/jpeg")

        val base64 = dg2Data.toBase64AndClear()

        assertEquals("/9j/", base64)
        assertArrayEquals(byteArrayOf(0, 0, 0), dg2Data.faceImageBytes)
    }

    @Test
    fun toBase64CharArray_returnsCharArrayAndZeroesBytes() {
        val faceImage = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val dg2Data = Dg2Data(faceImage, "image/jpeg")

        val chars = dg2Data.toBase64CharArray()

        assertArrayEquals("/9j/".toCharArray(), chars)
        assertArrayEquals(byteArrayOf(0, 0, 0), dg2Data.faceImageBytes)
        chars.fill('\u0000')
    }
}
