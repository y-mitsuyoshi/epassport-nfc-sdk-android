package com.example.epassport.data.parser

import com.example.epassport.domain.exception.InvalidDataException
import org.junit.Assert.assertEquals
import org.junit.Test

class Dg1ParserTest {

    /**
     * Build proper DG1 TLV: 0x61 [ 0x5F1F [ MRZ bytes ] ]
     */
    private fun buildDg1Tlv(mrzString: String): ByteArray {
        val mrzBytes = mrzString.toByteArray(Charsets.UTF_8)
        // Inner: tag 5F1F + length + value
        val innerTag = byteArrayOf(0x5F, 0x1F)
        val innerLength = encodeTlvLength(mrzBytes.size)
        val inner = innerTag + innerLength + mrzBytes
        // Outer: tag 61 + length + inner
        val outerTag = byteArrayOf(0x61)
        val outerLength = encodeTlvLength(inner.size)
        return outerTag + outerLength + inner
    }

    private fun encodeTlvLength(length: Int): ByteArray {
        return when {
            length <= 0x7F -> byteArrayOf(length.toByte())
            length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length shr 8).toByte(), (length and 0xFF).toByte())
        }
    }

    @Test
    fun parse_td3_validMrz_returnsCorrectFields() {
        // ICAO 9303 Part 3, TD3 passport MRZ (2 lines x 44 chars = 88 chars)
        val mrz = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<" +
                  "L898902C<3UTO6908061F9406236ZE184226B<<<<<14"
        val tlv = buildDg1Tlv(mrz)

        val result = Dg1Parser.parse(tlv)

        assertEquals("P", result.documentCode)
        assertEquals("UTO", result.issuingState)
        assertEquals("ERIKSSON", result.primaryIdentifier)
        assertEquals("ANNA MARIA", result.secondaryIdentifier)
        assertEquals("L898902C", result.documentNumber)
        assertEquals("UTO", result.nationality)
        assertEquals("690806", result.dateOfBirth)
        assertEquals("F", result.sex)
        assertEquals("940623", result.dateOfExpiry)
    }

    @Test
    fun parse_td1_validMrz_returnsCorrectFields() {
        // TD1 format: 3 lines x 30 chars = 90 chars
        val mrz = "I<UTOD231458907<<<<<<<<<<<<<<<" +
                  "7408122F1204159UTO<<<<<<<<<<<6" +
                  "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"
        val tlv = buildDg1Tlv(mrz)

        val result = Dg1Parser.parse(tlv)

        assertEquals("I", result.documentCode)
        assertEquals("UTO", result.issuingState)
        assertEquals("D23145890", result.documentNumber)
        assertEquals("740812", result.dateOfBirth)
        assertEquals("ERIKSSON", result.primaryIdentifier)
        assertEquals("ANNA MARIA", result.secondaryIdentifier)
        assertEquals("F", result.sex)
        assertEquals("120415", result.dateOfExpiry)
        assertEquals("UTO", result.nationality)
    }

    @Test
    fun parse_td2_validMrz_returnsCorrectFields() {
        // TD2 format: 2 lines x 36 chars = 72 chars
        val mrz = "I<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<"  +
                  "D231458907UTO7408122F1204159<<<<<<<6"
        val tlv = buildDg1Tlv(mrz)

        val result = Dg1Parser.parse(tlv)

        assertEquals("I", result.documentCode)
        assertEquals("UTO", result.issuingState)
        assertEquals("D23145890", result.documentNumber)
        assertEquals("740812", result.dateOfBirth)
        assertEquals("ERIKSSON", result.primaryIdentifier)
        assertEquals("ANNA MARIA", result.secondaryIdentifier)
        assertEquals("F", result.sex)
        assertEquals("120415", result.dateOfExpiry)
        assertEquals("UTO", result.nationality)
    }

    @Test(expected = InvalidDataException::class)
    fun parse_invalidMrzLength_throwsException() {
        val mrz = "INVALID_SHORT_MRZ"
        val tlv = buildDg1Tlv(mrz)
        Dg1Parser.parse(tlv)
    }

    @Test(expected = InvalidDataException::class)
    fun parse_missingDg1Tag_throwsException() {
        // DG1 expects tag 0x61, sending tag 0x75 instead
        val mrzBytes = "SOME_DATA".toByteArray()
        val tlv = byteArrayOf(0x75, mrzBytes.size.toByte()) + mrzBytes
        Dg1Parser.parse(tlv)
    }
}
