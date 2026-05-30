package com.example.epassport.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BacKeyTest {

    @Test
    fun clear_zeroesBothKeys() {
        val encKey = byteArrayOf(1, 2, 3, 4)
        val macKey = byteArrayOf(5, 6, 7, 8)
        val bacKey = BacKey(encKey, macKey)

        bacKey.clear()

        assertArrayEquals(byteArrayOf(0, 0, 0, 0), bacKey.encKey)
        assertArrayEquals(byteArrayOf(0, 0, 0, 0), bacKey.macKey)
    }

    @Test
    fun toString_doesNotExposeKeys() {
        val bacKey = BacKey(byteArrayOf(1, 2), byteArrayOf(3, 4))
        assertEquals("BacKey(***)", bacKey.toString())
    }
}
