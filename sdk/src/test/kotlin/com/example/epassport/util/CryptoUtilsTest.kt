package com.example.epassport.util

import com.example.epassport.domain.model.MrzData
import org.junit.Assert.assertArrayEquals
import org.junit.Before
import org.junit.Test
import java.security.Security

class CryptoUtilsTest {

    @Before
    fun setUp() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    @Test
    fun deriveKey_3des_matchesIcaoAppendixD() {
        // ICAO 9303 Part 11 D.2
        val mrzData = MrzData("L898902C<", "690806", "940623")
        val kSeed = mrzData.deriveBacKeySeed()
        
        // D.3, k_enc seed C = 00000001
        val kEnc = CryptoUtils.deriveKey(kSeed, byteArrayOf(0x00, 0x00, 0x00, 0x01))
        val expectedKEnc = byteArrayOf(
            0xAB.toByte(), 0x94.toByte(), 0xFD.toByte(), 0xEC.toByte(),
            0xF2.toByte(), 0x67.toByte(), 0x4F.toByte(), 0xDF.toByte(),
            0xB9.toByte(), 0xB3.toByte(), 0x91.toByte(), 0xF8.toByte(),
            0x5D.toByte(), 0x7F.toByte(), 0x76.toByte(), 0xF2.toByte()
        )
        assertArrayEquals(expectedKEnc, kEnc)

        // D.3, k_mac seed C = 00000002
        val kMac = CryptoUtils.deriveKey(kSeed, byteArrayOf(0x00, 0x00, 0x00, 0x02))
        val expectedKMac = byteArrayOf(
            0x79.toByte(), 0x62.toByte(), 0xD9.toByte(), 0xEC.toByte(),
            0xE0.toByte(), 0x3D.toByte(), 0x1A.toByte(), 0xCD.toByte(),
            0x4C.toByte(), 0x76.toByte(), 0x08.toByte(), 0x9D.toByte(),
            0xCE.toByte(), 0x13.toByte(), 0x15.toByte(), 0x43.toByte()
        )
        assertArrayEquals(expectedKMac, kMac)
    }

    @Test
    fun calculateMac_matchesIcaoAppendixD() {
        // ICAO 9303 Part 11 D.3 - R_ifd (Mutual Auth)
        val kMac = byteArrayOf(
            0x79.toByte(), 0x62.toByte(), 0xD9.toByte(), 0xEC.toByte(),
            0xE0.toByte(), 0x3D.toByte(), 0x1A.toByte(), 0xCD.toByte(),
            0x4C.toByte(), 0x76.toByte(), 0x08.toByte(), 0x9D.toByte(),
            0xCE.toByte(), 0x13.toByte(), 0x15.toByte(), 0x43.toByte()
        )
        
        val data = byteArrayOf(
            0x72.toByte(), 0xC2.toByte(), 0x3B.toByte(), 0x38.toByte(),
            0x41.toByte(), 0x61.toByte(), 0x19.toByte(), 0x49.toByte(),
            0x78.toByte(), 0x14.toByte(), 0x46.toByte(), 0x0F.toByte(),
            0x88.toByte(), 0x39.toByte(), 0x8C.toByte(), 0x12.toByte(),
            0x46.toByte(), 0x0F.toByte(), 0x88.toByte(), 0x39.toByte(),
            0x8C.toByte(), 0x12.toByte(), 0xBF.toByte(), 0x90.toByte(),
            0x7C.toByte(), 0x8D.toByte(), 0x94.toByte(), 0xCE.toByte(),
            0x22.toByte(), 0x83.toByte(), 0x1B.toByte(), 0x18.toByte()
        )

        val mac = CryptoUtils.calculateMac(kMac, data)
        val expectedMac = byteArrayOf(
            0x5F.toByte(), 0x16.toByte(), 0x7B.toByte(), 0x59.toByte(),
            0x2A.toByte(), 0x0E.toByte(), 0x0D.toByte(), 0x51.toByte()
        )
        
        assertArrayEquals(expectedMac, mac)
    }
}
