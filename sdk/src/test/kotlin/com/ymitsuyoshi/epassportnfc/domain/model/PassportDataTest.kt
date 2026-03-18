package com.ymitsuyoshi.epassportnfc.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class PersonalDataTest {

    private val futureDate = Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000)
    private val pastDate = Date(System.currentTimeMillis() - 365L * 24 * 60 * 60 * 1000)

    private fun makePersonalData(expiry: Date = futureDate) = PersonalData(
        surname = "SMITH",
        givenNames = "JOHN",
        documentNumber = "AB1234567",
        nationality = "GBR",
        dateOfBirth = Date(631152000000L), // 1990-01-01
        gender = 'M',
        dateOfExpiry = expiry,
    )

    @Test
    fun `fullName concatenates given names and surname`() {
        val data = makePersonalData()
        assertEquals("JOHN SMITH", data.fullName)
    }

    @Test
    fun `isValid returns true when expiry is in the future`() {
        assertTrue(makePersonalData(expiry = futureDate).isValid())
    }

    @Test
    fun `isValid returns false when expiry is in the past`() {
        assertFalse(makePersonalData(expiry = pastDate).isValid())
    }

    @Test
    fun `isValid uses provided reference date`() {
        val expiry = Date(1000L)
        val before = Date(500L)
        val after = Date(1500L)
        assertTrue(makePersonalData(expiry = expiry).isValid(before))
        assertFalse(makePersonalData(expiry = expiry).isValid(after))
    }
}

class PassportDataTest {

    @Test
    fun `equals compares content of faceImageBytes`() {
        val personal = PersonalData(
            surname = "DOE",
            givenNames = "JANE",
            documentNumber = "XY9876543",
            nationality = "USA",
            dateOfBirth = Date(631152000000L),
            gender = 'F',
            dateOfExpiry = Date(System.currentTimeMillis() + 1_000_000_000L),
        )
        val bytes = byteArrayOf(0x01, 0x02, 0x03)
        val a = PassportData(personal, faceImageBytes = bytes.copyOf())
        val b = PassportData(personal, faceImageBytes = bytes.copyOf())
        assertEquals(a, b)
    }

    @Test
    fun `DataGroup fromNumber returns correct entry`() {
        assertEquals(DataGroup.DG1, DataGroup.fromNumber(1))
        assertEquals(DataGroup.DG2, DataGroup.fromNumber(2))
        assertEquals(null, DataGroup.fromNumber(99))
    }
}
