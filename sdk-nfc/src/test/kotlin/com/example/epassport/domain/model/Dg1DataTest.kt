package com.example.epassport.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class Dg1DataTest {

    @Test
    fun serialization_roundtrips() {
        val dg1 = Dg1Data(
            documentCode = "P",
            issuingState = "UTO",
            documentNumber = "L898902C3",
            optionalData1 = "",
            dateOfBirth = "690806",
            sex = "F",
            dateOfExpiry = "940623",
            nationality = "UTO",
            optionalData2 = "",
            primaryIdentifier = "ERIKSSON",
            secondaryIdentifier = "ANNA MARIA"
        )

        val json = Json.encodeToString(dg1)
        val restored = Json.decodeFromString<Dg1Data>(json)

        assertEquals(dg1, restored)
    }
}
