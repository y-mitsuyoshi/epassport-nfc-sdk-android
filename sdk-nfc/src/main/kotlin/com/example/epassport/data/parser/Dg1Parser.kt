package com.example.epassport.data.parser

import com.example.epassport.domain.exception.InvalidDataException
import com.example.epassport.domain.model.Dg1Data
import com.example.epassport.domain.model.MrzData
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
            88 -> parseTd3(mrzString, data) // MRZ is 2 lines of 44 characters
            90 -> parseTd1(mrzString, data) // MRZ is 3 lines of 30 characters
            72 -> parseTd2(mrzString, data) // MRZ is 2 lines of 36 characters
            else -> throw InvalidDataException("Invalid MRZ length: ${mrzString.length}")
        }
    }

    private fun parseTd3(mrz: String, data: ByteArray): Dg1Data {
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
        val docNumCheckDigit = mrz[53]
        val nationality = mrz.substring(54, 57)
        val dateOfBirth = mrz.substring(57, 63)
        val dobCheckDigit = mrz[63]
        val sex = mrz.substring(64, 65)
        val dateOfExpiry = mrz.substring(65, 71)
        val doeCheckDigit = mrz[71]
        val personalNumber = mrz.substring(72, 86)
        val personalNumberCheckDigit = mrz[86]

        validateCheckDigit(documentNumber, docNumCheckDigit, "TD3 document number")
        validateCheckDigit(dateOfBirth, dobCheckDigit, "TD3 date of birth")
        validateCheckDigit(dateOfExpiry, doeCheckDigit, "TD3 date of expiry")
        validateCheckDigit(personalNumber, personalNumberCheckDigit, "TD3 personal number")

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
            secondaryIdentifier = secondaryIdentifier,
            rawBytes = data
        )
    }

    private fun parseTd1(mrz: String, data: ByteArray): Dg1Data {
        val namesField = mrz.substring(60, 90)
        val nameParts = namesField.split("<<")
        val primaryIdentifier = nameParts[0].replace("<", " ").trim()
        val secondaryIdentifier = if (nameParts.size > 1) nameParts[1].replace("<", " ").trim() else ""

        // Line 1
        val documentNumber = mrz.substring(5, 14)
        val docNumCheckDigit = mrz[14]

        // Line 2 (indices 30..59 in full MRZ)
        val dateOfBirth = mrz.substring(36, 42)
        val dobCheckDigit = mrz[42]
        val sex = mrz.substring(43, 44)
        val dateOfExpiry = mrz.substring(44, 50)
        val doeCheckDigit = mrz[50]
        val optionalData2 = mrz.substring(51, 57)
        val optionalData2CheckDigit = mrz[57]

        validateCheckDigit(documentNumber, docNumCheckDigit, "TD1 document number")
        validateCheckDigit(dateOfBirth, dobCheckDigit, "TD1 date of birth")
        validateCheckDigit(dateOfExpiry, doeCheckDigit, "TD1 date of expiry")
        validateCheckDigit(optionalData2, optionalData2CheckDigit, "TD1 optional data")

        return Dg1Data(
            documentCode = mrz.substring(0, 2).replace("<", ""),
            issuingState = mrz.substring(2, 5).replace("<", ""),
            documentNumber = documentNumber.replace("<", ""),
            optionalData1 = mrz.substring(15, 30).replace("<", ""),
            dateOfBirth = dateOfBirth,
            sex = sex.replace("<", ""),
            dateOfExpiry = dateOfExpiry,
            nationality = "", // TD1 MRZ does not contain a dedicated nationality field
            optionalData2 = optionalData2.replace("<", ""),
            primaryIdentifier = primaryIdentifier,
            secondaryIdentifier = secondaryIdentifier,
            rawBytes = data
        )
    }

    private fun parseTd2(mrz: String, data: ByteArray): Dg1Data {
        val namesField = mrz.substring(5, 36)
        val nameParts = namesField.split("<<")
        val primaryIdentifier = nameParts[0].replace("<", " ").trim()
        val secondaryIdentifier = if (nameParts.size > 1) nameParts[1].replace("<", " ").trim() else ""

        // Line 2 (indices 36..71 in full MRZ)
        val documentNumber = mrz.substring(36, 45)
        val docNumCheckDigit = mrz[45]
        val nationality = mrz.substring(46, 49)
        val dateOfBirth = mrz.substring(49, 55)
        val dobCheckDigit = mrz[55]
        val sex = mrz.substring(56, 57)
        val dateOfExpiry = mrz.substring(57, 63)
        val doeCheckDigit = mrz[63]
        val optionalData2 = mrz.substring(64, 71)
        val optionalData2CheckDigit = mrz[71]

        validateCheckDigit(documentNumber, docNumCheckDigit, "TD2 document number")
        validateCheckDigit(dateOfBirth, dobCheckDigit, "TD2 date of birth")
        validateCheckDigit(dateOfExpiry, doeCheckDigit, "TD2 date of expiry")
        validateCheckDigit(optionalData2, optionalData2CheckDigit, "TD2 optional data")

        return Dg1Data(
            documentCode = mrz.substring(0, 2).replace("<", ""),
            issuingState = mrz.substring(2, 5).replace("<", ""),
            documentNumber = documentNumber.replace("<", ""),
            optionalData1 = "",
            dateOfBirth = dateOfBirth,
            sex = sex.replace("<", ""),
            dateOfExpiry = dateOfExpiry,
            nationality = nationality.replace("<", ""),
            optionalData2 = optionalData2.replace("<", ""),
            primaryIdentifier = primaryIdentifier,
            secondaryIdentifier = secondaryIdentifier,
            rawBytes = data
        )
    }

    /**
     * ICAO Doc 9303 Part 3 のチェックディジットを検証する。
     * 不一致の場合は [InvalidDataException] をスローする。
     */
    private fun validateCheckDigit(field: String, expectedDigitChar: Char, fieldName: String) {
        val expectedDigit = expectedDigitChar.toString().toIntOrNull()
            ?: throw InvalidDataException("Invalid check digit character for $fieldName: $expectedDigitChar")
        val fieldChars = field.toCharArray()
        try {
            val calculated = MrzData.computeCheckDigitStatic(fieldChars)
            if (calculated != expectedDigit) {
                throw InvalidDataException(
                    "Check digit mismatch for $fieldName: expected $expectedDigit, calculated $calculated"
                )
            }
        } catch (e: IllegalArgumentException) {
            throw InvalidDataException("Invalid MRZ character in $fieldName: $field", e)
        } finally {
            fieldChars.fill('\u0000')
        }
    }
}
