package com.example.epassport.data.reader

import com.example.epassport.data.nfc.ApduCommand
import com.example.epassport.data.parser.Dg1Parser
import com.example.epassport.data.parser.Dg2Parser
import com.example.epassport.data.parser.TlvParser
import com.example.epassport.domain.exception.ApduException
import com.example.epassport.domain.exception.EPassportException
import com.example.epassport.domain.model.Dg1Data
import com.example.epassport.domain.model.Dg2Data
import com.example.epassport.domain.port.DataGroupReader
import com.example.epassport.domain.port.NfcTransceiver
import java.io.ByteArrayOutputStream

class IcaoDataGroupReader : DataGroupReader {

    override suspend fun readDg1(transceiver: NfcTransceiver): Dg1Data {
        val dg1Bytes = readDataGroup(transceiver, byteArrayOf(0x01, 0x01)) // DG1 File ID
        return Dg1Parser.parse(dg1Bytes)
    }

    override suspend fun readDg2(transceiver: NfcTransceiver): Dg2Data {
        val dg2Bytes = readDataGroup(transceiver, byteArrayOf(0x01, 0x02)) // DG2 File ID
        return Dg2Parser.parse(dg2Bytes)
    }

    override suspend fun readDg15(transceiver: NfcTransceiver): ByteArray {
        return readDataGroup(transceiver, byteArrayOf(0x01, 0x0F)) // DG15 File ID
    }

    override suspend fun readDg14(transceiver: NfcTransceiver): ByteArray {
        return readDataGroup(transceiver, byteArrayOf(0x01, 0x0E)) // DG14 File ID
    }

    override suspend fun readSod(transceiver: NfcTransceiver): ByteArray {
        return readDataGroup(transceiver, byteArrayOf(0x01, 0x1D)) // EF.SOD File ID
    }

    override suspend fun performActiveAuthentication(
        transceiver: NfcTransceiver,
        challenge: ByteArray
    ): ByteArray {
        val cmd = ApduCommand.internalAuthenticate(challenge)
        val response = transceiver.transceive(cmd)
        checkStatus(response)
        return response.copyOfRange(0, response.size - 2) // Omit status word (SW)
    }

    internal suspend fun readDataGroup(transceiver: NfcTransceiver, fileId: ByteArray): ByteArray {

        // 1. SELECT FILE
        val selectCmd = ApduCommand.selectFile(fileId)
        val selectResponse = transceiver.transceive(selectCmd)
        checkStatus(selectResponse)

        // 2. Read first 8 bytes to determine TLV tag and length.
        // ISO7816-4 の TLV 長フィールドは最大4バイト（0x83 + 3バイト値）になるため、
        // タグ1-3バイト + 長さ最大4バイト = 計5-7バイトが必要。余裕を持って8バイト読む。
        val initialReadCmd = ApduCommand.readBinary(0, 8)
        val headerResponse = transceiver.transceive(initialReadCmd)
        checkStatus(headerResponse)
        
        val headerBytes = headerResponse.copyOfRange(0, headerResponse.size - 2)
        
        // 異常系ガード: 最低でも2バイト以上（Tag + Lengthの最初の1バイト）必要。
        if (headerBytes.size < 2) {
            throw com.example.epassport.domain.exception.InvalidDataException(
                "Invalid or truncated TLV header response (size=${headerBytes.size})"
            )
        }
        
        // Parse Tag and Length (可変バイト長タグに完全対応)
        val tagResult = TlvParser.readTag(headerBytes, 0)
        val lengthResult = TlvParser.readLength(headerBytes, tagResult.bytesRead)
        
        val sequenceLength = tagResult.bytesRead + lengthResult.bytesRead + lengthResult.length
        
        val outputStream = ByteArrayOutputStream()
        
        // すでに initial read で取得済みのヘッダデータを書き込む (二重通信の排除による高速化)
        val initialBytesToWrite = minOf(headerBytes.size, sequenceLength)
        outputStream.write(headerBytes, 0, initialBytesToWrite)
        
        var offset = initialBytesToWrite
        var remainingData = sequenceLength - initialBytesToWrite
        
        if (transceiver.isExtendedLengthSupported) {
            // Extended APDU: 最大 65536 バイトずつ高速読み出し
            var iterations = 0
            val maxIterations = 1000
            while (remainingData > 0) {
                if (++iterations > maxIterations) {
                    throw EPassportException(
                        "Exceeded maximum read iterations ($maxIterations) during Extended Read Binary"
                    )
                }
                // le=0 は ISO7816-4 で「65536 バイト」を意味する
                val chunkLe = if (remainingData > 65536) 0 else remainingData
                val readCmd = ApduCommand.readBinaryExtended(offset, chunkLe)
                val response = transceiver.transceive(readCmd)
                checkStatus(response)
                val data = response.copyOfRange(0, response.size - 2)

                // 安全弁: NFCチップが9000成功応答を返したにも関わらずボディが空の場合のフリーズ防止
                if (data.isEmpty()) {
                    throw EPassportException("NFC card returned empty data during Extended Read Binary")
                }

                outputStream.write(data)
                offset += data.size
                remainingData -= data.size
            }
        } else {
            // chunk reading (下位互換用：従来通り255バイトずつ細切れに読み出す)
            val maxLe = 255 // extended Le might fail on some passports, sticking to short LE in BAC

            var iterations = 0
            val maxIterations = 1000
            while (remainingData > 0) {
                if (++iterations > maxIterations) {
                    throw EPassportException(
                        "Exceeded maximum read iterations ($maxIterations) during Short APDU Read Binary"
                    )
                }
                if (offset >= 32768) {
                    throw EPassportException(
                        "Short APDU fallback does not support reading files larger than 32KB (offset=$offset)"
                    )
                }
                val le = if (remainingData > maxLe) maxLe else remainingData
                val readCmd = ApduCommand.readBinary(offset, le)
                val response = transceiver.transceive(readCmd)
                checkStatus(response)

                val data = response.copyOfRange(0, response.size - 2)

                // 安全弁: 無限ループフリーズ防止
                if (data.isEmpty()) {
                    throw EPassportException("NFC card returned empty data during Short APDU Read Binary")
                }

                outputStream.write(data)

                offset += data.size // Update offset by actual read data size
                remainingData -= data.size
            }
        }

        return outputStream.toByteArray()
    }

    private fun checkStatus(response: ByteArray) {
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        if (sw1 != 0x90 || sw2 != 0x00) {
            throw ApduException(sw1, sw2, "Error reading data group. SW=$sw1 $sw2")
        }
    }
}
