package com.example.epassport.data.parser

import com.example.epassport.domain.exception.InvalidDataException
import com.example.epassport.domain.model.Dg2Data

object Dg2Parser {

    /**
     * DG2 バイナリデータ (Tag 0x75) をパースして Dg2Data にマッピングする。
     * 内部から顔画像のバイナリストリームと MIME Type (JPEG / JP2000) を抽出する。
     */
    fun parse(data: ByteArray): Dg2Data {
        try {
            val dg2Node = TlvParser.parse(data).find { it.tag == 0x75 }
                ?: throw InvalidDataException("DG2 (0x75) tag not found")

            // Biometric Information Template (BIT) group: tag 0x7F61
            val bitNodes = TlvParser.parse(dg2Node.value)
            val bitNode = bitNodes.find { it.tag == 0x7F61 }
                ?: throw InvalidDataException("BIT (0x7F61) tag not found")

            // Biometric Data Block (BDB): various inner tags, finding Biometric Data Block tag (0x5F2E) or 0x7F60
            val bdbTagNodes = TlvParser.parse(bitNode.value)
            
            // Note: The structure inside 0x7F61 can be generic or have 0x02, 0x7F60
            // Actually, we can just do a brute force search for JPEG / JP2 headers in the payload
            // as ICAO facial structures can vary.
            
            val faceImageResult = extractImage(data)
                ?: throw InvalidDataException("Facial image header (JPEG/JP2000) not found in DG2")
                
            return Dg2Data(faceImageResult.first, faceImageResult.second)
            
        } catch (e: Exception) {
            throw InvalidDataException("Failed to parse DG2: ${e.message}", e)
        }
    }

    /**
     * バイナリ列から JPEG (`FF D8 FF`) または JP2 (`00 00 00 0C 6A 50 20 20`) のヘッダを探し、
     * 最後までを抽出する。
     */
    private fun extractImage(data: ByteArray): Pair<ByteArray, String>? {
        // Search JPEG
        for (i in 0 until data.size - 2) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD8.toByte() && data[i + 2] == 0xFF.toByte()) {
                val imageBytes = ByteArray(data.size - i)
                System.arraycopy(data, i, imageBytes, 0, imageBytes.size)
                return Pair(imageBytes, "image/jpeg")
            }
        }
        
        // Search JP2000
        val jp2Header = byteArrayOf(0x00, 0x00, 0x00, 0x0C, 0x6A, 0x50, 0x20, 0x20)
        for (i in 0 until data.size - 8) {
            var match = true
            for (j in 0 until 8) {
                if (data[i + j] != jp2Header[j]) {
                    match = false
                    break
                }
            }
            if (match) {
                val imageBytes = ByteArray(data.size - i)
                System.arraycopy(data, i, imageBytes, 0, imageBytes.size)
                return Pair(imageBytes, "image/jp2")
            }
        }
        
        return null
    }
}
