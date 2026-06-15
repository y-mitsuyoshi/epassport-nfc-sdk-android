package com.example.epassport.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EncryptedPassportServerTransferDataTest {

    @Test
    fun dataClass_holdsJwe() {
        val data = EncryptedPassportServerTransferData("header.key.iv.ciphertext.tag")
        assertEquals("header.key.iv.ciphertext.tag", data.jwe)
    }
}
