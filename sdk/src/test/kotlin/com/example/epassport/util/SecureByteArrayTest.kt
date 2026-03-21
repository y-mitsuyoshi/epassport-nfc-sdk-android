package com.example.epassport.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SecureByteArrayTest {

    @Test
    fun createAndUse_storesAndClearsData() {
        val initialData = byteArrayOf(1, 2, 3, 4, 5)
        val secureArray = SecureByteArray(initialData.clone())

        secureArray.use {
            assertArrayEquals(initialData, it)
        }

        // After `use`, the internal array should be cleared
        secureArray.use {
            for (byte in it) {
                assertEquals(0.toByte(), byte)
            }
        }
    }

    @Test
    fun clone_createsIndependentCopy() {
        val initialData = byteArrayOf(1, 2, 3, 4, 5)
        val secureArray1 = SecureByteArray(initialData.clone())
        val secureArray2 = secureArray1.clone()

        secureArray1.use { arr1 ->
            secureArray2.use { arr2 ->
                assertArrayEquals(arr1, arr2)
                
                // Modify copy
                arr2[0] = 99
                assertEquals(99.toByte(), arr2[0])
                assertEquals(1.toByte(), arr1[0]) // Original should be unchanged
            }
        }

        secureArray1.destroy()
        secureArray2.destroy()
    }
}
