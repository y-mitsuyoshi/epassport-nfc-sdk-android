package com.example.epassport.domain.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.Security

class MrzDataTest {

    init {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    @Test
    fun computeCheckDigit_isValid() {
        val mrzData = MrzData("L898902C<", "690806", "940623")
        
        // As per ICAO 9303 Part 11 Appendix D.1
        assertEquals(3, mrzData.computeCheckDigit("L898902C<"))
        assertEquals(1, mrzData.computeCheckDigit("690806"))
        assertEquals(6, mrzData.computeCheckDigit("940623"))
    }

    @Test
    fun deriveBacKeySeed_matchesIcaoAppendixD() {
        val mrzData = MrzData("L898902C<", "690806", "940623")
        
        val kSeed = mrzData.deriveBacKeySeed()
        // ICAO 9303 Part 11 D.2:
        val expectedKSeed = byteArrayOf(
            0x23.toByte(), 0x9A.toByte(), 0xB9.toByte(), 0xCB.toByte(),
            0x28.toByte(), 0x2D.toByte(), 0xAF.toByte(), 0x66.toByte(),
            0x23.toByte(), 0x1D.toByte(), 0xC5.toByte(), 0xA4.toByte(),
            0xDF.toByte(), 0x6B.toByte(), 0xFB.toByte(), 0xAE.toByte()
        )
        assertArrayEquals(expectedKSeed, kSeed)
    }

    @Test
    fun deriveBacKeys_matchesIcaoAppendixD() {
        val mrzData = MrzData("L898902C<", "690806", "940623")
        val bacKey = mrzData.deriveBacKeys()

        val expectedKEnc = byteArrayOf(
            0xAB.toByte(), 0x94.toByte(), 0xFD.toByte(), 0xEC.toByte(),
            0xF2.toByte(), 0x67.toByte(), 0x4F.toByte(), 0xDF.toByte(),
            0xB9.toByte(), 0xB3.toByte(), 0x91.toByte(), 0xF8.toByte(),
            0x5D.toByte(), 0x7F.toByte(), 0x76.toByte(), 0xF2.toByte()
        )

        val expectedKMac = byteArrayOf(
            0x79.toByte(), 0x62.toByte(), 0xD9.toByte(), 0xEC.toByte(),
            0xE0.toByte(), 0x3D.toByte(), 0x1A.toByte(), 0xCD.toByte(),
            0x4C.toByte(), 0x76.toByte(), 0x08.toByte(), 0x9D.toByte(),
            0xCE.toByte(), 0x13.toByte(), 0x15.toByte(), 0x43.toByte()
        )
        
        assertArrayEquals(expectedKEnc, bacKey.encKey)
        assertArrayEquals(expectedKMac, bacKey.macKey)
    }
}
