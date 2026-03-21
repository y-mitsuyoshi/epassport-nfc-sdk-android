package com.example.epassport.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.security.Security

class CryptoUtilsTest {

    @Before
    fun setUp() {
        Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
    }

    @Test
    fun encrypt3DesCbc_thenDecrypt_returnsOriginal() {
        val key = ByteArray(16) { (it + 1).toByte() }
        val plaintext = ByteArray(16) { (it * 2).toByte() }

        val encrypted = CryptoUtils.encrypt3DesCbc(key, plaintext)
        val decrypted = CryptoUtils.decrypt3DesCbc(key, encrypted)

        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun encrypt3DesCbc_differentKeys_produceDifferentCiphertext() {
        val key1 = ByteArray(16) { 0x01.toByte() }
        val key2 = ByteArray(16) { 0x02.toByte() }
        val plaintext = ByteArray(8) { 0x55.toByte() }

        val encrypted1 = CryptoUtils.encrypt3DesCbc(key1, plaintext)
        val encrypted2 = CryptoUtils.encrypt3DesCbc(key2, plaintext)

        assert(!encrypted1.contentEquals(encrypted2))
    }

    @Test
    fun encrypt3DesCbc_withCustomIv_produceDifferentResult() {
        val key = ByteArray(16) { 0x01.toByte() }
        val plaintext = ByteArray(8) { 0x55.toByte() }
        val iv = ByteArray(8) { 0xFF.toByte() }

        val enc1 = CryptoUtils.encrypt3DesCbc(key, plaintext)
        val enc2 = CryptoUtils.encrypt3DesCbc(key, plaintext, iv)

        assert(!enc1.contentEquals(enc2))
    }

    @Test
    fun calculateMac_matchesIcaoAppendixD() {
        // ICAO 9303 Part 11 D.3
        val kMac = byteArrayOf(
            0x79.toByte(), 0x62.toByte(), 0xD9.toByte(), 0xEC.toByte(),
            0xE0.toByte(), 0x3D.toByte(), 0x1A.toByte(), 0xCD.toByte(),
            0x4C.toByte(), 0x76.toByte(), 0x08.toByte(), 0x9D.toByte(),
            0xCE.toByte(), 0x13.toByte(), 0x15.toByte(), 0x43.toByte()
        )

        // ICAO 9303 Part 11 D.3 eIFD
        val data = byteArrayOf(
            0x72.toByte(), 0xC2.toByte(), 0x3B.toByte(), 0x38.toByte(),
            0x41.toByte(), 0x61.toByte(), 0x19.toByte(), 0x49.toByte(),
            0x78.toByte(), 0x51.toByte(), 0xEE.toByte(), 0xB2.toByte(),
            0x0A.toByte(), 0x6A.toByte(), 0xBC.toByte(), 0xD0.toByte(),
            0xB5.toByte(), 0x32.toByte(), 0x30.toByte(), 0x7B.toByte(),
            0xCA.toByte(), 0x64.toByte(), 0xD7.toByte(), 0xBA.toByte(),
            0x5F.toByte(), 0xA0.toByte(), 0x58.toByte(), 0x4E.toByte(),
            0x8A.toByte(), 0xB3.toByte(), 0xBA.toByte(), 0xDE.toByte()
        )

        val mac = CryptoUtils.calculateMac(kMac, CryptoUtils.pad(data))
        
        // ICAO 9303 Part 11 D.3 M_IFD
        val expectedMac = byteArrayOf(
            0x5F.toByte(), 0x14.toByte(), 0x48.toByte(), 0xEE.toByte(),
            0xA8.toByte(), 0xAD.toByte(), 0x90.toByte(), 0xA7.toByte()
        )

        assertArrayEquals(expectedMac, mac)
    }

    @Test
    fun calculateMac_returns8Bytes() {
        val key = ByteArray(16) { 0x01.toByte() }
        val data = ByteArray(16) { 0x55.toByte() }

        val mac = CryptoUtils.calculateMac(key, data)

        assertEquals(8, mac.size)
    }

    @Test
    fun pad_addsCorrectPadding() {
        // Data of 4 bytes → should be padded to 8 bytes (80 00 00 00)
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val padded = CryptoUtils.pad(data)

        assertEquals(8, padded.size)
        assertEquals(0x01.toByte(), padded[0])
        assertEquals(0x04.toByte(), padded[3])
        assertEquals(0x80.toByte(), padded[4])
        assertEquals(0x00.toByte(), padded[5])
    }

    @Test
    fun pad_dataAlreadyBlockSize_addsFullBlock() {
        // Data of 8 bytes → should add a full block of padding (8 more bytes)
        val data = ByteArray(8) { 0x01.toByte() }
        val padded = CryptoUtils.pad(data)

        assertEquals(16, padded.size)
        assertEquals(0x80.toByte(), padded[8])
    }

    @Test
    fun unpad_removesIso7816Padding() {
        val padded = byteArrayOf(0x01, 0x02, 0x03, 0x80.toByte(), 0x00, 0x00, 0x00, 0x00)
        val unpadded = CryptoUtils.unpad(padded)

        assertEquals(3, unpadded.size)
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), unpadded)
    }

    @Test
    fun unpad_noPadMarker_returnsOriginal() {
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val unpadded = CryptoUtils.unpad(data)

        assertArrayEquals(data, unpadded)
    }

    @Test
    fun pad_then_unpad_roundtrips() {
        val data = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55)
        val padded = CryptoUtils.pad(data)
        val unpadded = CryptoUtils.unpad(padded)

        assertArrayEquals(data, unpadded)
    }
}
