package com.example.epassport.data.parser

import com.example.epassport.domain.exception.InvalidDataException

data class TlvNode(val tag: Int, val value: ByteArray)

/**
 * ASN.1 BER-TLV パーサー
 */
object TlvParser {

    /** 与えられたバイト列をパースし、TLVノードのリストを返す */
    fun parse(data: ByteArray): List<TlvNode> {
        val nodes = mutableListOf<TlvNode>()
        var offset = 0
        while (offset < data.size) {
            if (data[offset] == 0x00.toByte() || data[offset] == 0xFF.toByte()) {
                // padding
                offset++
                continue
            }
            val tagResult = readTag(data, offset)
            offset += tagResult.bytesRead
            
            val lengthResult = readLength(data, offset)
            offset += lengthResult.bytesRead
            
            if (offset + lengthResult.length > data.size) {
                throw InvalidDataException("TLV value length $lengthResult exceeds available data")
            }
            
            val valueBytes = ByteArray(lengthResult.length)
            System.arraycopy(data, offset, valueBytes, 0, lengthResult.length)
            offset += lengthResult.length
            
            nodes.add(TlvNode(tagResult.tag, valueBytes))
        }
        return nodes
    }

    /** タグの読み取り (1-3 バイト) */
    fun readTag(data: ByteArray, offset: Int): TagResult {
        var idx = offset
        var tag = data[idx].toInt() and 0xFF
        var bytesRead = 1
        
        if ((tag and 0x1F) == 0x1F) {
            idx++
            var nextByte = data[idx].toInt() and 0xFF
            bytesRead++
            tag = (tag shl 8) or nextByte
            
            if ((nextByte and 0x80) == 0x80) {
                idx++
                nextByte = data[idx].toInt() and 0xFF
                bytesRead++
                tag = (tag shl 8) or nextByte
            }
        }
        return TagResult(tag, bytesRead)
    }

    /** 長さの読み取り (1-5 バイト) */
    fun readLength(data: ByteArray, offset: Int): LengthResult {
        var idx = offset
        val firstByte = data[idx].toInt() and 0xFF
        var bytesRead = 1
        var length: Int
        
        if (firstByte <= 0x7F) {
            length = firstByte
        } else if (firstByte == 0x81) {
            idx++
            length = data[idx].toInt() and 0xFF
            bytesRead += 1
        } else if (firstByte == 0x82) {
            length = ((data[idx + 1].toInt() and 0xFF) shl 8) or (data[idx + 2].toInt() and 0xFF)
            bytesRead += 2
        } else if (firstByte == 0x83) {
            length = ((data[idx + 1].toInt() and 0xFF) shl 16) or 
                     ((data[idx + 2].toInt() and 0xFF) shl 8) or 
                     (data[idx + 3].toInt() and 0xFF)
            bytesRead += 3
        } else {
            throw InvalidDataException("Unsupported TLV length encoding")
        }
        return LengthResult(length, bytesRead)
    }

    data class TagResult(val tag: Int, val bytesRead: Int)
    data class LengthResult(val length: Int, val bytesRead: Int)
}
