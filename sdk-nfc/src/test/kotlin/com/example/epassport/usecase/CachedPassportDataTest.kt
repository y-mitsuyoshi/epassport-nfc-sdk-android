package com.example.epassport.usecase

import com.example.epassport.domain.model.Dg1Data
import com.example.epassport.domain.model.Dg2Data
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CachedPassportDataTest {

    @Test
    fun defaultValues_areNull() {
        val cached = CachedPassportData()
        assertNull(cached.dg1)
        assertNull(cached.dg2)
        assertNull(cached.sodBytes)
        assertNull(cached.aaData)
    }

    @Test
    fun properties_areRetained() {
        val mockDg1 = mockk<Dg1Data>()
        val mockDg2 = mockk<Dg2Data>()
        val sod = byteArrayOf(0x01, 0x02)

        val cached = CachedPassportData(
            dg1 = mockDg1,
            dg2 = mockDg2,
            sodBytes = sod
        )

        assertEquals(mockDg1, cached.dg1)
        assertEquals(mockDg2, cached.dg2)
        assertEquals(sod, cached.sodBytes)
    }

    @Test
    fun isExpired_worksCorrectly() {
        val cached = CachedPassportData(timestamp = System.currentTimeMillis())
        assertFalse(cached.isExpired(timeoutMs = 10000))

        val expiredCached = CachedPassportData(timestamp = System.currentTimeMillis() - 20000)
        assertTrue(expiredCached.isExpired(timeoutMs = 10000))
    }
}
