package com.example.epassport.data.parser

import com.example.epassport.domain.exception.InvalidDataException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Dg2ParserTest {

    @Test
    fun parse_jpegImage_returnsFaceImageBytesAndMimeType() {
        // JPEG SOI marker: FF D8 FF
        val jpegData = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00.toByte(), 0x10.toByte(), 0x4A.toByte(), 0x46.toByte()
        )

        // Build a minimal DG2 TLV structure: 0x75 [ 0x7F61 [ data with JPEG ] ]
        val innerData = byteArrayOf(0x02, 0x01, 0x01) + jpegData
        val bitTag = byteArrayOf(0x7F, 0x61, innerData.size.toByte())
        val dg2Inner = bitTag + innerData
        val dg2Tlv = byteArrayOf(0x75, dg2Inner.size.toByte()) + dg2Inner

        val result = Dg2Parser.parse(dg2Tlv)

        assertEquals("image/jpeg", result.mimeType)
        // The extracted image should start from the JPEG SOI marker to end
        assertEquals(0xFF.toByte(), result.faceImageBytes[0])
        assertEquals(0xD8.toByte(), result.faceImageBytes[1])
        assertEquals(0xFF.toByte(), result.faceImageBytes[2])
    }

    @Test
    fun parse_jp2Image_returnsFaceImageBytesAndMimeType() {
        // JP2000 header: 00 00 00 0C 6A 50 20 20
        val jp2Data = byteArrayOf(
            0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20,
            0x01, 0x02, 0x03
        )

        val innerData = byteArrayOf(0x02, 0x01, 0x01) + jp2Data
        val bitTag = byteArrayOf(0x7F, 0x61, innerData.size.toByte())
        val dg2Inner = bitTag + innerData
        val dg2Tlv = byteArrayOf(0x75, dg2Inner.size.toByte()) + dg2Inner

        val result = Dg2Parser.parse(dg2Tlv)

        assertEquals("image/jp2", result.mimeType)
        assertEquals(0x00.toByte(), result.faceImageBytes[0])
        assertEquals(0x0C.toByte(), result.faceImageBytes[3])
    }

    @Test(expected = InvalidDataException::class)
    fun parse_missingDg2Tag_throwsException() {
        // Using wrong tag 0x61 instead of 0x75
        val data = byteArrayOf(0x61, 0x02, 0x01, 0x01)
        Dg2Parser.parse(data)
    }

    @Test(expected = InvalidDataException::class)
    fun parse_noImageHeader_throwsException() {
        // DG2 with no recognizable image header
        val noImageData = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val innerData = byteArrayOf(0x02, 0x01, 0x01) + noImageData
        val bitTag = byteArrayOf(0x7F, 0x61, innerData.size.toByte())
        val dg2Inner = bitTag + innerData
        val dg2Tlv = byteArrayOf(0x75, dg2Inner.size.toByte()) + dg2Inner

        Dg2Parser.parse(dg2Tlv)
    }
}
