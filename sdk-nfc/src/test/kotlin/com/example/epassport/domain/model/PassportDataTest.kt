package com.example.epassport.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator

class PassportDataTest {

    @Test
    fun toServerTransferData_encodesDg2AndClearsBytes() {
        val faceImage = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val dg2 = Dg2Data(faceImage, "image/jpeg")
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
        val passportData = PassportData(dg1 = dg1, dg2 = dg2)

        val transferData = passportData.toServerTransferData()

        assertEquals(dg1, transferData.dg1)
        assertEquals("image/jpeg", transferData.faceImageMimeType)
        assertEquals("/9j/", transferData.faceImageBase64)
        assertTrue(dg2.faceImageBytes.all { it == 0x00.toByte() })
    }

    @Test
    fun toEncryptedServerTransferData_returnsJwe() {
        val faceImage = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val dg2 = Dg2Data(faceImage, "image/jpeg")
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
        val passportData = PassportData(dg1 = dg1, dg2 = dg2)

        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val encrypted = passportData.toEncryptedServerTransferData(keyPair.public)

        assertNotNull(encrypted.jwe)
        assertEquals(5, encrypted.jwe.split(".").size)
    }

    @Test
    fun clear_zeroizesSensitiveByteArrays() {
        val faceImage = byteArrayOf(1, 2, 3)
        val dg2 = Dg2Data(faceImage, "image/jpeg")
        val aaData = ActiveAuthenticationData(
            publicKeyInfo = byteArrayOf(4, 5, 6),
            challenge = byteArrayOf(7, 8, 9),
            signature = byteArrayOf(10, 11, 12)
        )
        val passportData = PassportData(dg1 = Dg1Data(
            documentCode = "P", issuingState = "UTO", documentNumber = "X",
            optionalData1 = "", dateOfBirth = "000000", sex = "M",
            dateOfExpiry = "000000", nationality = "UTO", optionalData2 = "",
            primaryIdentifier = "", secondaryIdentifier = ""
        ), dg2 = dg2, activeAuthenticationData = aaData)

        passportData.clear()

        assertTrue(dg2.faceImageBytes.all { it == 0x00.toByte() })
        assertTrue(aaData.publicKeyInfo.all { it == 0x00.toByte() })
        assertTrue(aaData.challenge.all { it == 0x00.toByte() })
        assertTrue(aaData.signature.all { it == 0x00.toByte() })
    }
}
