package com.example.epassport.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveAuthenticationDataTest {

    @Test
    fun toBase64Map_encodesAllFields() {
        val data = ActiveAuthenticationData(
            publicKeyInfo = byteArrayOf(0x01, 0x02),
            challenge = byteArrayOf(0x03, 0x04),
            signature = byteArrayOf(0x05, 0x06)
        )

        val map = data.toBase64Map()

        assertEquals(3, map.size)
        assertEquals("AQI=", map["publicKeyInfo"])
        assertEquals("AwQ=", map["challenge"])
        assertEquals("BQY=", map["signature"])
    }

    @Test
    fun clear_zeroizesAllFields() {
        val data = ActiveAuthenticationData(
            publicKeyInfo = byteArrayOf(0x01, 0x02),
            challenge = byteArrayOf(0x03, 0x04),
            signature = byteArrayOf(0x05, 0x06)
        )

        data.clear()

        assertArrayEquals(byteArrayOf(0, 0), data.publicKeyInfo)
        assertArrayEquals(byteArrayOf(0, 0), data.challenge)
        assertArrayEquals(byteArrayOf(0, 0), data.signature)
    }

    @Test
    fun equals_sameContent_returnsTrue() {
        val a = ActiveAuthenticationData(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3))
        val b = ActiveAuthenticationData(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3))
        assertTrue(a == b)
    }
}
