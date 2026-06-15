package com.example.epassport.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaceInfoTest {

    @Test
    fun ecdhAes256Properties() {
        val info = PaceInfo("0.4.0.127.0.7.2.2.4.2.4", 2, 0x0D)

        assertTrue(info.isEcdh)
        assertTrue(info.isAesCmac)
        assertEquals(256, info.keyLength)
    }

    @Test
    fun dh3DesProperties() {
        val info = PaceInfo("0.4.0.127.0.7.2.2.4.1.1", 2, null)

        assertFalse(info.isEcdh)
        assertFalse(info.isAesCmac)
        assertEquals(112, info.keyLength)
    }

    @Test
    fun ecdhAes128Properties() {
        val info = PaceInfo("0.4.0.127.0.7.2.2.4.2.2", 2, 0x05)

        assertTrue(info.isEcdh)
        assertTrue(info.isAesCmac)
        assertEquals(128, info.keyLength)
    }
}
