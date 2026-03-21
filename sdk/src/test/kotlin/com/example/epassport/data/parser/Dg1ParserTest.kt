package com.example.epassport.data.parser

import com.example.epassport.domain.model.MrzData
import org.junit.Assert.assertEquals
import org.junit.Test

class Dg1ParserTest {

    @Test
    fun parse_validDg1Data_returnsMrzData() {
        // Example from ICAO 9303 Part 3, Appendix B, Section 4.1.1
        val dg1Data = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<L898902C<3UTO6908061F9406236ZE184226B<<<<<14"
            .replace('<', ' ').toByteArray()

        val tlvData = byteArrayOf(0x61, dg1Data.size.toByte()) + dg1Data

        val mrzData = Dg1Parser.parse(tlvData)

        assertEquals("L898902C<", mrzData.documentNumber)
        assertEquals("690806", mrzData.dateOfBirth)
        assertEquals("940623", mrzData.dateOfExpiry)
    }
}
