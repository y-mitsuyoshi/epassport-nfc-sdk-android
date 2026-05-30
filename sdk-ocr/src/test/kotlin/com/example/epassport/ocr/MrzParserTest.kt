package com.example.epassport.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class MrzParserTest {

    // ========== TD3 (2行×44文字) ==========

    @Test
    fun `parse TD3 extracts document number dateOfBirth and dateOfExpiry`() {
        val mrz = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\n" +
                  "L898902C36UTO7408122F1204159ZE184226B<<<<<14"
        val result = MrzParser.parse(mrz)
        assertEquals("L898902C3", result.documentNumber)
        assertEquals("740812", result.dateOfBirth)
        assertEquals("120415", result.dateOfExpiry)
    }

    @Test
    fun `parse TD3 strips filler less-than from document number`() {
        val mrz = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<\n" +
                  "L898902<<6UTO7408122F1204159ZE184226B<<<<<14"
        val result = MrzParser.parse(mrz)
        assertEquals("L898902", result.documentNumber)
    }

    // ========== TD2 (2行×36文字) ==========

    @Test
    fun `parse TD2 extracts fields correctly`() {
        val line1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<"
        val line2 = "L898902C36UTO7408122F1204159ZE18422B"
        val result = MrzParser.parse("$line1\n$line2")
        assertEquals("L898902C3", result.documentNumber)
        assertEquals("740812", result.dateOfBirth)
        assertEquals("120415", result.dateOfExpiry)
    }

    @Test
    fun `parse TD2 strips filler less-than from document number`() {
        val line1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<"
        val line2 = "L898902<<6UTO7408122F1204159ZE18422B"
        val result = MrzParser.parse("$line1\n$line2")
        assertEquals("L898902", result.documentNumber)
    }

    // ========== TD1 (3行×30文字) ==========

    @Test
    fun `parse TD1 standard format extracts fields correctly`() {
        // I<UTO (5-char prefix) + D23145890 (9) + 7 (check) + remaining
        val line1 = "I<UTOD231458907<<<<<<<<<<<<<<<"
        val line2 = "9408125M1204158D<<1310107<<<<<"
        val line3 = "MUSTERMANN<<ERIKA<<<<<<<<<<<<<"
        val result = MrzParser.parse("$line1\n$line2\n$line3")
        assertEquals("D23145890", result.documentNumber)
        assertEquals("940812", result.dateOfBirth)
        assertEquals("120415", result.dateOfExpiry)
    }

    @Test
    fun `parse TD1 with diplomatic D prefix`() {
        val line1 = "D<UTOD231458907<<<<<<<<<<<<<<<"
        val line2 = "9408125M1204158D<<1310107<<<<<"
        val line3 = "MUSTERMANN<<ERIKA<<<<<<<<<<<<<"
        val result = MrzParser.parse("$line1\n$line2\n$line3")
        assertEquals("D23145890", result.documentNumber)
        assertEquals("940812", result.dateOfBirth)
        assertEquals("120415", result.dateOfExpiry)
    }

    @Test
    fun `parse TD1 strips filler less-than from document number`() {
        val line1 = "I<UTO<<123456<7<<<<<<<<<<<<<<<"
        val line2 = "9408125M1204158D<<1310107<<<<<"
        val line3 = "MUSTERMANN<<ERIKA<<<<<<<<<<<<<"
        val result = MrzParser.parse("$line1\n$line2\n$line3")
        assertEquals("123456", result.documentNumber)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws for single line input`() {
        MrzParser.parse("P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<")
    }
}
