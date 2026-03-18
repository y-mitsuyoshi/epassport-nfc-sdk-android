package com.example.epassport.data.reader

import com.example.epassport.data.nfc.ApduCommand
import com.example.epassport.data.parser.Dg1Parser
import com.example.epassport.data.parser.Dg2Parser
import com.example.epassport.data.parser.TlvParser
import com.example.epassport.domain.exception.ApduException
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

    private suspend fun readDataGroup(transceiver: NfcTransceiver, fileId: ByteArray): ByteArray {
        // 1. SELECT FILE
        val selectCmd = ApduCommand.selectFile(fileId)
        val selectResponse = transceiver.transceive(selectCmd)
        checkStatus(selectResponse)

        // 2. Read first 4 bytes to determine TLV tag and length
        val initialReadCmd = ApduCommand.readBinary(0, 4)
        val headerResponse = transceiver.transceive(initialReadCmd)
        checkStatus(headerResponse)
        
        val headerBytes = headerResponse.copyOfRange(0, headerResponse.size - 2)
        
        // Parse Length
        val lengthResult = TlvParser.readLength(headerBytes, 1) // First byte is Tag (e.g. 0x61, 0x75)
        
        val sequenceLength = 1 + lengthResult.bytesRead + lengthResult.length
        
        val outputStream = ByteArrayOutputStream()
        
        // chunk reading
        var offset = 0
        var remainingData = sequenceLength
        val maxLe = 255 // extended Le might fail on some passports, sticking to short LE in BAC

        while (remainingData > 0) {
            val le = if (remainingData > maxLe) maxLe else remainingData
            val readCmd = ApduCommand.readBinary(offset, le)
            val response = transceiver.transceive(readCmd)
            checkStatus(response)

            val data = response.copyOfRange(0, response.size - 2)
            outputStream.write(data)

            offset += data.size // Update offset by actual read data size
            remainingData -= data.size
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
