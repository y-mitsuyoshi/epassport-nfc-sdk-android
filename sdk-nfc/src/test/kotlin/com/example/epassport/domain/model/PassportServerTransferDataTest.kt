package com.example.epassport.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test

class PassportServerTransferDataTest {

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
        val data = PassportServerTransferData(
            dg1 = dg1,
            faceImageBase64 = "/9j/",
            faceImageMimeType = "image/jpeg",
            activeAuthentication = mapOf("challenge" to "AwQ=")
        )

        val json = Json.encodeToString(data)
        val restored = Json.decodeFromString<PassportServerTransferData>(json)

        assertEquals(data, restored)
    }
}
