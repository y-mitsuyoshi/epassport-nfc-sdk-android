package com.example.epassport.data.parser

import com.example.epassport.domain.exception.InvalidDataException
import com.example.epassport.domain.model.Dg1Data
import java.nio.charset.StandardCharsets

object Dg1Parser {

    /**
     * DG1 バイナリデータ (Tag 0x61) をパースして Dg1Data モデルに変換する
     */
    fun parse(data: ByteArray): Dg1Data {
        val rootNodes = TlvParser.parse(data)
        val dg1Node = rootNodes.find { it.tag == 0x61 } 
            ?: throw InvalidDataException("DG1 (0x61) tag not found")
        
        val mrzNodes = TlvParser.parse(dg1Node.value)
        val mrzNode = mrzNodes.find { it.tag == 0x5F1F } 
            ?: throw InvalidDataException("MRZ (0x5F1F) tag not found in DG1")
            
        val mrzString = String(mrzNode.value, StandardCharsets.UTF_8).replace("\r", "").replace("\n", "")
        
        // ICAO Doc 9303 Part 4, Section 4.2: MRZ Formats (TD1, TD2, TD3)
        return when (mrzString.length) {
            88 -> parseTd3(mrzString) // MRZ is 2 lines of 44 characters
            90 -> parseTd1(mrzString) // MRZ is 3 lines of 30 characters
            72 -> parseTd2(mrzString) // MRZ is 2 lines of 36 characters
            else -> throw InvalidDataException("Invalid MRZ length: ${mrzString.length}")
        }
    }

    private fun parseTd3(mrz: String): Dg1Data {
        // Line 1
        val documentCode = mrz.substring(0, 2)
        val issuingState = mrz.substring(2, 5)
        
        // Names: separated by << (two carets), primary then secondary names (separated by <)
        val namesField = mrz.substring(5, 44)
        val nameParts = namesField.split("<<")
        val primaryIdentifier = nameParts[0].replace("<", " ").trim()
        val secondaryIdentifier = if (nameParts.size > 1) nameParts[1].replace("<", " ").trim() else ""

        // Line 2
        val documentNumber = mrz.substring(44, 53)
        // val docNumCheckDigit = mrz[53]
        val nationality = mrz.substring(54, 57)
        val dateOfBirth = mrz.substring(57, 63)
        // val dobCheckDigit = mrz[63]
        val sex = mrz.substring(64, 65)
        val dateOfExpiry = mrz.substring(65, 71)
        // val doeCheckDigit = mrz[71]
        val personalNumber = mrz.substring(72, 86)

        return Dg1Data(
            documentCode = documentCode.replace("<", ""),
            issuingState = issuingState.replace("<", ""),
            documentNumber = documentNumber.replace("<", ""),
            optionalData1 = "",
            dateOfBirth = dateOfBirth,
            sex = sex.replace("<", ""),
            dateOfExpiry = dateOfExpiry,
            nationality = nationality.replace("<", ""),
            optionalData2 = personalNumber.replace("<", ""),
            primaryIdentifier = primaryIdentifier,
            secondaryIdentifier = secondaryIdentifier
        )
    }

    private fun parseTd1(mrz: String): Dg1Data {
        // Simplified fallback for TD1
        val documentCode = mrz.substring(0, 2)
        val documentNumber = mrz.substring(5, 14)
        return Dg1Data(
            documentCode = documentCode.replace("<", ""),
            issuingState = mrz.substring(2, 5).replace("<", ""),
            documentNumber = documentNumber.replace("<", ""),
            optionalData1 = mrz.substring(15, 30).replace("<", ""),
            dateOfBirth = mrz.substring(30, 36),
            sex = mrz.substring(37, 38).replace("<", ""),
            dateOfExpiry = mrz.substring(38, 44),
            nationality = mrz.substring(45, 48).replace("<", ""),
            optionalData2 = mrz.substring(48, 59).replace("<", ""),
            primaryIdentifier = "",
            secondaryIdentifier = ""
        )
    }

    private fun parseTd2(mrz: String): Dg1Data {
        // Simplified fallback for TD2
        val documentCode = mrz.substring(0, 2)
        val issuingState = mrz.substring(2, 5)
        val documentNumber = mrz.substring(36, 45)
        val dateOfBirth = mrz.substring(49, 55)
        val sex = mrz.substring(56, 57)
        return Dg1Data(
            documentCode = documentCode.replace("<", ""),
            issuingState = issuingState.replace("<", ""),
            documentNumber = documentNumber.replace("<", ""),
            optionalData1 = "",
            dateOfBirth = dateOfBirth,
            sex = sex.replace("<", ""),
            dateOfExpiry = mrz.substring(57, 63),
            nationality = mrz.substring(63, 66).replace("<", ""),
            optionalData2 = "",
            primaryIdentifier = "",
            secondaryIdentifier = ""
        )
    }
}
