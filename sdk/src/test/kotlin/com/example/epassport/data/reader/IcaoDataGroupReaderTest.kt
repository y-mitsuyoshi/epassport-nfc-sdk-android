package com.example.epassport.data.reader

import com.example.epassport.domain.exception.EPassportException
import com.example.epassport.domain.port.NfcTransceiver
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class IcaoDataGroupReaderTest {

    @Test
    fun readDataGroup_extendedApdu_singleChunk() = runBlocking {
        val transceiver = mockk<NfcTransceiver>(relaxed = true)
        coEvery { transceiver.isExtendedLengthSupported } returns true

        var transceiveCount = 0
        coEvery { transceiver.transceive(any()) } answers {
            transceiveCount++
            val cmd = arg<ByteArray>(0)
            when (cmd[1].toInt() and 0xFF) {
                0xA4 -> byteArrayOf(0x90.toByte(), 0x00.toByte()) // SELECT OK
                0xB0 -> {
                    val offset = ((cmd[2].toInt() and 0xFF) shl 8) or (cmd[3].toInt() and 0xFF)
                    val le = if (cmd.size == 7) {
                        ((cmd[5].toInt() and 0xFF) shl 8) or (cmd[6].toInt() and 0xFF)
                    } else {
                        cmd[4].toInt() and 0xFF
                    }
                    if (offset == 0 && le == 8) {
                        // Initial 8-byte header: tag=0x61, len=10, padding
                        byteArrayOf(0x61, 0x0A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x90.toByte(), 0x00.toByte())
                    } else {
                        // Return requested amount of dummy data + SW
                        ByteArray(le) { 0xAA.toByte() } + byteArrayOf(0x90.toByte(), 0x00.toByte())
                    }
                }
                else -> byteArrayOf(0x90.toByte(), 0x00.toByte())
            }
        }

        val reader = IcaoDataGroupReader()
        val result = reader.readDataGroup(transceiver, byteArrayOf(0x01, 0x01))

        // sequenceLength = 1(tag) + 1(len) + 10(value) = 12 bytes
        assertEquals(12, result.size)
        // SELECT + initial read + one extended read = 3 calls
        assertEquals(3, transceiveCount)
    }

    @Test
    fun readDataGroup_extendedApdu_multipleChunks() = runBlocking {
        val transceiver = mockk<NfcTransceiver>(relaxed = true)
        coEvery { transceiver.isExtendedLengthSupported } returns true

        var transceiveCount = 0
        coEvery { transceiver.transceive(any()) } answers {
            transceiveCount++
            val cmd = arg<ByteArray>(0)
            when (cmd[1].toInt() and 0xFF) {
                0xA4 -> byteArrayOf(0x90.toByte(), 0x00.toByte())
                0xB0 -> {
                    val offset = ((cmd[2].toInt() and 0xFF) shl 8) or (cmd[3].toInt() and 0xFF)
                    val le = if (cmd.size == 7) {
                        ((cmd[5].toInt() and 0xFF) shl 8) or (cmd[6].toInt() and 0xFF)
                    } else {
                        cmd[4].toInt() and 0xFF
                    }
                    if (offset == 0 && le == 8) {
                        // tag=0x61, 3-byte length encoding: len=65536
                        // headerBytes: 1 tag + 4 length bytes + 3 padding = 8 bytes
                        byteArrayOf(
                            0x61, 0x83.toByte(), 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
                            0x90.toByte(), 0x00.toByte()
                        )
                    } else {
                        val chunkSize = if (le == 0) 65536 else le
                        ByteArray(chunkSize) { 0xAA.toByte() } + byteArrayOf(0x90.toByte(), 0x00.toByte())
                    }
                }
                else -> byteArrayOf(0x90.toByte(), 0x00.toByte())
            }
        }

        val reader = IcaoDataGroupReader()
        val result = reader.readDataGroup(transceiver, byteArrayOf(0x01, 0x01))

        // sequenceLength = 1 + 4 + 65536 = 65541 bytes
        assertEquals(65541, result.size)
        // 修正後: initial read の 8バイト再利用により、残りの 65533 バイトを 1回 の Extended APDU チャンクで読み出せる。
        // SELECT + initial read + Extended read (65533) = 計 3 calls
        assertEquals(3, transceiveCount)
    }

    @Test(expected = EPassportException::class)
    fun readDataGroup_shortApdu_32kBoundary_throws() = runBlocking {
        val transceiver = mockk<NfcTransceiver>(relaxed = true)
        coEvery { transceiver.isExtendedLengthSupported } returns false

        coEvery { transceiver.transceive(any()) } answers {
            val cmd = arg<ByteArray>(0)
            when (cmd[1].toInt() and 0xFF) {
                0xA4 -> byteArrayOf(0x90.toByte(), 0x00.toByte())
                0xB0 -> {
                    val offset = ((cmd[2].toInt() and 0xFF) shl 8) or (cmd[3].toInt() and 0xFF)
                    val le = cmd[4].toInt() and 0xFF
                    if (offset == 0 && le == 8) {
                        // tag=0x61, 2-byte length: len=0x808E=32894, padded to 8 bytes
                        byteArrayOf(
                            0x61, 0x82.toByte(), 0x80.toByte(), 0x8E.toByte(), 0x00, 0x00, 0x00, 0x00,
                            0x90.toByte(), 0x00.toByte()
                        )
                    } else {
                        ByteArray(le) { 0xAA.toByte() } + byteArrayOf(0x90.toByte(), 0x00.toByte())
                    }
                }
                else -> byteArrayOf(0x90.toByte(), 0x00.toByte())
            }
        }

        val reader = IcaoDataGroupReader()
        // sequenceLength = 1 + 3 + 32894 = 32898
        // Short APDU with maxLe=255 will cross 32768 boundary before finishing
        reader.readDataGroup(transceiver, byteArrayOf(0x01, 0x01))
    }

    @Test
    fun readDataGroup_shortApdu_under32k_succeeds() = runBlocking {
        val transceiver = mockk<NfcTransceiver>(relaxed = true)
        coEvery { transceiver.isExtendedLengthSupported } returns false

        var transceiveCount = 0
        coEvery { transceiver.transceive(any()) } answers {
            transceiveCount++
            val cmd = arg<ByteArray>(0)
            when (cmd[1].toInt() and 0xFF) {
                0xA4 -> byteArrayOf(0x90.toByte(), 0x00.toByte())
                0xB0 -> {
                    val offset = ((cmd[2].toInt() and 0xFF) shl 8) or (cmd[3].toInt() and 0xFF)
                    val le = cmd[4].toInt() and 0xFF
                    if (offset == 0 && le == 8) {
                        // tag=0x61, len=500 => sequenceLength = 1+1+500 = 502, padded to 8 bytes
                        byteArrayOf(0x61, 0x01, 0xF4.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x90.toByte(), 0x00.toByte())
                    } else {
                        ByteArray(le) { 0xAA.toByte() } + byteArrayOf(0x90.toByte(), 0x00.toByte())
                    }
                }
                else -> byteArrayOf(0x90.toByte(), 0x00.toByte())
            }
        }

        val reader = IcaoDataGroupReader()
        val result = reader.readDataGroup(transceiver, byteArrayOf(0x01, 0x01))

        // sequenceLength = 502
        assertEquals(502, result.size)
        // SELECT + initial read + ceil(502/255)=2 short reads = 4 calls
        assertEquals(4, transceiveCount)
    }

    @Test(expected = com.example.epassport.domain.exception.InvalidDataException::class)
    fun readDataGroup_truncatedHeader_throwsInvalidDataException() = runBlocking {
        val transceiver = mockk<NfcTransceiver>(relaxed = true)
        coEvery { transceiver.transceive(any()) } answers {
            val cmd = arg<ByteArray>(0)
            when (cmd[1].toInt() and 0xFF) {
                0xA4 -> byteArrayOf(0x90.toByte(), 0x00.toByte())
                0xB0 -> {
                    // ヘッダデータが 1バイトのみ（TLV長さを判定するのに最低2バイト必要）
                    byteArrayOf(0x61, 0x90.toByte(), 0x00.toByte())
                }
                else -> byteArrayOf(0x90.toByte(), 0x00.toByte())
            }
        }

        val reader = IcaoDataGroupReader()
        reader.readDataGroup(transceiver, byteArrayOf(0x01, 0x01))
    }

    @Test(expected = EPassportException::class)
    fun readDataGroup_emptyDataResponse_preventsInfiniteLoop() = runBlocking {
        val transceiver = mockk<NfcTransceiver>(relaxed = true)
        coEvery { transceiver.isExtendedLengthSupported } returns true
        coEvery { transceiver.transceive(any()) } answers {
            val cmd = arg<ByteArray>(0)
            when (cmd[1].toInt() and 0xFF) {
                0xA4 -> byteArrayOf(0x90.toByte(), 0x00.toByte())
                0xB0 -> {
                    val offset = ((cmd[2].toInt() and 0xFF) shl 8) or (cmd[3].toInt() and 0xFF)
                    val le = if (cmd.size == 7) {
                        ((cmd[5].toInt() and 0xFF) shl 8) or (cmd[6].toInt() and 0xFF)
                    } else {
                        cmd[4].toInt() and 0xFF
                    }
                    if (offset == 0 && le == 8) {
                        // tag=0x61, len=100
                        byteArrayOf(0x61, 0x64, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x90.toByte(), 0x00.toByte())
                    } else {
                        // ボディが0バイトの 9000 成功レスポンスを返す (フリーズ・無限ループを誘発するテスト)
                        byteArrayOf(0x90.toByte(), 0x00.toByte())
                    }
                }
                else -> byteArrayOf(0x90.toByte(), 0x00.toByte())
            }
        }

        val reader = IcaoDataGroupReader()
        reader.readDataGroup(transceiver, byteArrayOf(0x01, 0x01))
    }
}
