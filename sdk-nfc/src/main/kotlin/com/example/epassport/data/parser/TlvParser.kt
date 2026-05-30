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
        if (offset < 0 || offset >= data.size) {
            throw InvalidDataException("Tag offset $offset exceeds available data")
        }
        var idx = offset
        var tag = data[idx].toInt() and 0xFF
        var bytesRead = 1
        
        if ((tag and 0x1F) == 0x1F) {
            if (idx + 1 >= data.size) {
                throw InvalidDataException("Truncated multi-byte TLV tag")
            }
            idx++
            var nextByte = data[idx].toInt() and 0xFF
            bytesRead++
            tag = (tag shl 8) or nextByte
            
            if ((nextByte and 0x80) == 0x80) {
                if (idx + 1 >= data.size) {
                    throw InvalidDataException("Truncated multi-byte TLV tag")
                }
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
        if (offset < 0 || offset >= data.size) {
            throw InvalidDataException("Length offset $offset exceeds available data")
        }
        var idx = offset
        val firstByte = data[idx].toInt() and 0xFF
        var bytesRead = 1
        var length: Int
        
        if (firstByte <= 0x7F) {
            length = firstByte
        } else if (firstByte == 0x81) {
            if (idx + 1 >= data.size) {
                throw InvalidDataException("Truncated TLV length (expected 1 additional byte)")
            }
            idx++
            length = data[idx].toInt() and 0xFF
            bytesRead += 1
        } else if (firstByte == 0x82) {
            if (idx + 2 >= data.size) {
                throw InvalidDataException("Truncated TLV length (expected 2 additional bytes)")
            }
            length = ((data[idx + 1].toInt() and 0xFF) shl 8) or (data[idx + 2].toInt() and 0xFF)
            bytesRead += 2
        } else if (firstByte == 0x83) {
            if (idx + 3 >= data.size) {
                throw InvalidDataException("Truncated TLV length (expected 3 additional bytes)")
            }
            length = ((data[idx + 1].toInt() and 0xFF) shl 16) or 
                     ((data[idx + 2].toInt() and 0xFF) shl 8) or 
                     (data[idx + 3].toInt() and 0xFF)
            bytesRead += 3
        } else {
            throw InvalidDataException("Unsupported TLV length encoding: 0x${firstByte.toString(16)}")
        }
        return LengthResult(length, bytesRead)
    }

    data class TagResult(val tag: Int, val bytesRead: Int)
    data class LengthResult(val length: Int, val bytesRead: Int)
}
