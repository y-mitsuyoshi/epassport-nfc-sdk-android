package com.example.epassport.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class Dg2DataTest {

    @Test
    fun clear_zeroesFaceImageBytes() {
        val faceImage = byteArrayOf(1, 2, 3, 4)
        val dg2Data = Dg2Data(faceImage, "image/jpeg")

        dg2Data.clear()

        assertArrayEquals(byteArrayOf(0, 0, 0, 0), dg2Data.faceImageBytes)
    }

    @Test
    fun toString_returnsMetadataButNotData() {
        val dg2Data = Dg2Data(byteArrayOf(1, 2, 3), "image/jp2")
        assertEquals("Dg2Data(mimeType=image/jp2, size=3 bytes)", dg2Data.toString())
    }
}
