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
        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())

        // As per ICAO 9303 Part 11 Appendix D.1
        assertEquals(3, mrzData.computeCheckDigit("L898902C<".toCharArray()))
        assertEquals(1, mrzData.computeCheckDigit("690806".toCharArray()))
        assertEquals(6, mrzData.computeCheckDigit("940623".toCharArray()))
        mrzData.clear()
    }

    @Test
    fun deriveBacKeySeed_matchesIcaoAppendixD() {
        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())

        val kSeed = mrzData.deriveBacKeySeed()
        // ICAO 9303 Part 11 D.2:
        val expectedKSeed = byteArrayOf(
            0x23.toByte(), 0x9A.toByte(), 0xB9.toByte(), 0xCB.toByte(),
            0x28.toByte(), 0x2D.toByte(), 0xAF.toByte(), 0x66.toByte(),
            0x23.toByte(), 0x1D.toByte(), 0xC5.toByte(), 0xA4.toByte(),
            0xDF.toByte(), 0x6B.toByte(), 0xFB.toByte(), 0xAE.toByte()
        )
        assertArrayEquals(expectedKSeed, kSeed)
        kSeed.fill(0)
        mrzData.clear()
    }

    @Test
    fun deriveBacKeys_matchesIcaoAppendixD() {
        val mrzData = MrzData("L898902C<".toCharArray(), "690806".toCharArray(), "940623".toCharArray())
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
        bacKey.clear()
        mrzData.clear()
    }

    @Test
    fun deriveBacKeys_yumaPassport() {
        val mrzData = MrzData("TR6930600".toCharArray(), "901008".toCharArray(), "261017".toCharArray())
        val bacKey = mrzData.deriveBacKeys()

        val expectedKEnc = byteArrayOf(
            0xC8.toByte(), 0xE3.toByte(), 0xF4.toByte(), 0xEF.toByte(),
            0xBF.toByte(), 0xD9.toByte(), 0xC4.toByte(), 0x61.toByte(),
            0xC8.toByte(), 0x45.toByte(), 0x08.toByte(), 0x10.toByte(),
            0x13.toByte(), 0x86.toByte(), 0x6E.toByte(), 0x68.toByte()
        )

        val expectedKMac = byteArrayOf(
            0xE9.toByte(), 0x2A.toByte(), 0x2F.toByte(), 0x7A.toByte(),
            0xEC.toByte(), 0x25.toByte(), 0x76.toByte(), 0xD5.toByte(),
            0xAB.toByte(), 0x61.toByte(), 0x0E.toByte(), 0xB6.toByte(),
            0x92.toByte(), 0x79.toByte(), 0xDC.toByte(), 0xB5.toByte()
        )

        assertArrayEquals(expectedKEnc, bacKey.encKey)
        assertArrayEquals(expectedKMac, bacKey.macKey)
        bacKey.clear()
        mrzData.clear()
    }

    @Test
    fun clear_zeroizesAllFields() {
        val docNum = "L898902C<".toCharArray()
        val dob = "690806".toCharArray()
        val doe = "940623".toCharArray()
        val mrzData = MrzData(docNum, dob, doe)

        mrzData.clear()

        assertEquals('\u0000', mrzData.documentNumber[0])
        assertEquals('\u0000', mrzData.dateOfBirth[0])
        assertEquals('\u0000', mrzData.dateOfExpiry[0])
        docNum.fill('\u0000')
        dob.fill('\u0000')
        doe.fill('\u0000')
    }

    @Test
    fun clear_zeroizesCanField() {
        val docNum = "L898902C<".toCharArray()
        val dob = "690806".toCharArray()
        val doe = "940623".toCharArray()
        val can = "123456".toCharArray()
        val mrzData = MrzData(docNum, dob, doe, can)

        mrzData.clear()

        assertEquals('\u0000', mrzData.documentNumber[0])
        assertEquals('\u0000', mrzData.dateOfBirth[0])
        assertEquals('\u0000', mrzData.dateOfExpiry[0])
        org.junit.Assert.assertNotNull(mrzData.can)
        assertEquals('\u0000', mrzData.can!![0])
        docNum.fill('\u0000')
        dob.fill('\u0000')
        doe.fill('\u0000')
        can.fill('\u0000')
    }
}
