package com.example.epassport.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SecureByteArrayTest {

    @Test
    fun close_clearsData() {
        val initialData = byteArrayOf(1, 2, 3, 4, 5)
        val secureArray = SecureByteArray(initialData.clone())

        // Data should be accessible before close
        assertArrayEquals(initialData, secureArray.unwrap())

        // After close, the internal array should be zeroed
        secureArray.close()
        for (byte in secureArray.unwrap()) {
            assertEquals(0.toByte(), byte)
        }
    }

    @Test
    fun size_returnsCorrectLength() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val secureArray = SecureByteArray(data)

        assertEquals(5, secureArray.size)
    }

    @Test
    fun size_afterClose_returnsSameLength() {
        val data = byteArrayOf(1, 2, 3)
        val secureArray = SecureByteArray(data)
        secureArray.close()

        // Size should still return the array length even after clearing
        assertEquals(3, secureArray.size)
    }

    @Test
    fun unwrap_returnsInternalArray() {
        val data = byteArrayOf(10, 20, 30)
        val secureArray = SecureByteArray(data)

        val unwrapped = secureArray.unwrap()

        // Should be the same object reference
        assert(data === unwrapped)
    }

    @Test
    fun useBlock_closesAfterBlock() {
        val data = byteArrayOf(1, 2, 3, 4, 5)
        val secureArray = SecureByteArray(data.clone())

        secureArray.use { sa ->
            assertArrayEquals(data, sa.unwrap())
        }

        // After use block, data should be cleared
        for (byte in secureArray.unwrap()) {
            assertEquals(0.toByte(), byte)
        }
    }

    @Test
    fun multipleCloses_doesNotThrow() {
        val secureArray = SecureByteArray(byteArrayOf(1, 2, 3))
        secureArray.close()
        secureArray.close() // Should not throw
    }
}
