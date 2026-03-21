package com.example.epassport.data.parser

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Dg2ParserTest {

    @Test
    fun parse_validDg2Data_returnsFaceImageBytes() {
        // Mock DG2 data. Structure is more complex, involving biometrics.
        // For this test, we'll simulate a simplified structure.
        // 7F61 (Biometric data template) -> 02 (Number of biometric data blocks)
        // -> 7F2E (Facial data) -> A1 (Biometric header) -> 88 (Image data)
        
        val imageData = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) // Simple fake JPEG SOI

        // This is a highly simplified TLV structure. A real one is more nested.
        val dg2Tlv = byteArrayOf(
            0x75, (imageData.size + 4).toByte(), // DG2 File
            0x7F, 0x61, (imageData.size + 2).toByte(), // Biometric Data Template
            0x02, 0x01, 0x01, // Number of instances
            0x7F, 0x2E, imageData.size.toByte(), // Facial data block
            *imageData
        )
        
        val dg2Data = Dg2Parser.parse(dg2Tlv)
        
        assertArrayEquals(imageData, dg2Data.faceImage)
    }
}
