package com.example.epassport.data.auth

import com.example.epassport.domain.exception.ApduException
import com.example.epassport.domain.exception.AuthenticationException
import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.util.CryptoUtils
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.util.Arrays

/**
 * 送信 APDU を暗号化し MAC を付加、受信 APDU の MAC を検証して復号するデコレータ。
 */
class SecureMessaging(
    private val delegate: NfcTransceiver,
    private val ksEnc: ByteArray,
    private val ksMac: ByteArray,
    private val ssc: ByteArray
) : NfcTransceiver {

    override val isConnected: Boolean get() = delegate.isConnected
    override var timeout: Int
        get() = delegate.timeout
        set(value) { delegate.timeout = value }

    override suspend fun selectApp() {
        // eMRTD Applet 選択済み状態のため通常は不要だが委譲する
        delegate.selectApp()
    }

    override suspend fun transceive(command: ByteArray): ByteArray {
        incrementSsc()

        val cla = command[0].toInt() and 0xFF
        val ins = command[1].toInt() and 0xFF
        val p1 = command[2].toInt() and 0xFF
        val p2 = command[3].toInt() and 0xFF
        // Parse Le/Lc
        var lc = 0
        var le = -1
        var dataField: ByteArray? = null

        if (command.size > 5) {
            lc = command[4].toInt() and 0xFF
            dataField = command.copyOfRange(5, 5 + lc)
            if (command.size > 5 + lc) {
                le = command[5 + lc].toInt() and 0xFF
            }
        } else if (command.size == 5) {
            le = command[4].toInt() and 0xFF
        }

        // 1. Mask CLA
        val maskedCla = (cla or 0x0C).toByte()

        // 2. Pad command header
        val header = byteArrayOf(maskedCla, ins.toByte(), p1.toByte(), p2.toByte())
        val paddedHeader = CryptoUtils.pad(header)

        // 3. DO87 (Data encryption)
        var do87: ByteArray? = null
        if (dataField != null && lc > 0) {
            val paddedData = CryptoUtils.pad(dataField)
            val encryptedData = CryptoUtils.encrypt3DesCbc(ksEnc, paddedData)
            
            val do87Payload = ByteArray(1 + encryptedData.size)
            do87Payload[0] = 0x01
            System.arraycopy(encryptedData, 0, do87Payload, 1, encryptedData.size)
            
            val lengthBytes = buildLength(do87Payload.size)
            do87 = ByteArray(1 + lengthBytes.size + do87Payload.size)
            do87[0] = 0x87.toByte()
            System.arraycopy(lengthBytes, 0, do87, 1, lengthBytes.size)
            System.arraycopy(do87Payload, 0, do87, 1 + lengthBytes.size, do87Payload.size)
        }

        // 4. DO97 (Le)
        var do97: ByteArray? = null
        if (le >= 0) {
            do97 = byteArrayOf(0x97.toByte(), 0x01.toByte(), le.toByte())
        }

        // 5. Build M for MAC
        val macStream = ByteArrayOutputStream()
        macStream.write(ssc)
        macStream.write(paddedHeader)
        if (do87 != null) macStream.write(do87)
        if (do97 != null) macStream.write(do97)

        val macData = CryptoUtils.pad(macStream.toByteArray())
        val mac = CryptoUtils.calculateMac(ksMac, macData)

        // DO8E
        val do8e = ByteArray(10)
        do8e[0] = 0x8E.toByte()
        do8e[1] = 0x08.toByte()
        System.arraycopy(mac, 0, do8e, 2, 8)

        // 6. Build new APDU
        val protectedCmdStream = ByteArrayOutputStream()
        protectedCmdStream.write(header)
        
        var totalLc = 0
        if (do87 != null) totalLc += do87.size
        if (do97 != null) totalLc += do97.size
        totalLc += do8e.size
        
        protectedCmdStream.write(totalLc)
        if (do87 != null) protectedCmdStream.write(do87)
        if (do97 != null) protectedCmdStream.write(do97)
        protectedCmdStream.write(do8e)
        // Le for wrapping is always 0x00
        protectedCmdStream.write(0x00)

        // 7. Transceive
        val response = delegate.transceive(protectedCmdStream.toByteArray())

        // 8. Verify and Unpack Response
        incrementSsc()

        if (response.size < 2) {
            throw ApduException(0, 0, "Invalid SM response length")
        }

        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        
        // SM error handling usually returns SW without SM data if error occurs
        if (response.size == 2 && (sw1 != 0x90 || sw2 != 0x00)) {
            throw ApduException(sw1, sw2, "APDU Error SW1=$sw1, SW2=$sw2")
        }

        return unwrapResponse(response)
    }

    private fun unwrapResponse(response: ByteArray): ByteArray {
        // Strip 2 bytes SW at the end
        val data = response.copyOfRange(0, response.size - 2)
        val sw1 = response[response.size - 2]
        val sw2 = response[response.size - 1]

        // Parse DO contents: 87(encrypted), 99(SW), 8E(MAC)
        var offset = 0
        var do87Value: ByteArray? = null
        var do99Value: ByteArray? = null
        var do8eValue: ByteArray? = null

        while (offset < data.size) {
            val tag = data[offset].toInt() and 0xFF
            offset++
            val (len, lenBytes) = parseLength(data, offset)
            offset += lenBytes
            
            val value = data.copyOfRange(offset, offset + len)
            offset += len

            when (tag) {
                0x87 -> do87Value = value
                0x99 -> do99Value = value
                0x8E -> do8eValue = value
            }
        }

        if (do8eValue == null || do99Value == null) {
            throw AuthenticationException("Invalid SM response structure: missing DO8E or DO99")
        }

        // Verify MAC
        val macStream = ByteArrayOutputStream()
        macStream.write(ssc)
        // Ensure to include DO87 including its tag and length if present
        if (do87Value != null) {
            val do87LenInfo = buildLength(do87Value.size)
            macStream.write(0x87)
            macStream.write(do87LenInfo)
            macStream.write(do87Value)
        }
        val do99LenInfo = buildLength(do99Value.size)
        macStream.write(0x99)
        macStream.write(do99LenInfo)
        macStream.write(do99Value)

        val macData = CryptoUtils.pad(macStream.toByteArray())
        val calculatedMac = CryptoUtils.calculateMac(ksMac, macData)

        if (!Arrays.equals(do8eValue, calculatedMac)) {
            throw AuthenticationException("SM Response MAC verification failed")
        }

        // DO99 SW check
        if (do99Value[0] != sw1 || do99Value[1] != sw2) {
            throw AuthenticationException("DO99 SW does not match Response SW")
        }

        // Decrypt DO87
        if (do87Value != null) {
            // First byte of DO87 value is PI (Padding Indicator, usually 01).
            if (do87Value[0].toInt() != 0x01) {
                throw AuthenticationException("Unsupported padding indicator ${do87Value[0]}")
            }
            val encrypted = do87Value.copyOfRange(1, do87Value.size)
            val decryptedWithPad = CryptoUtils.decrypt3DesCbc(ksEnc, encrypted)
            val decrypted = CryptoUtils.unpad(decryptedWithPad)
            
            // Build response: decrypted + SW1 + SW2
            val finalRes = ByteArray(decrypted.size + 2)
            System.arraycopy(decrypted, 0, finalRes, 0, decrypted.size)
            finalRes[finalRes.size - 2] = sw1
            finalRes[finalRes.size - 1] = sw2
            return finalRes
        }

        return byteArrayOf(sw1, sw2)
    }

    private fun incrementSsc() {
        var sscVal = BigInteger(1, ssc)
        sscVal = sscVal.add(BigInteger.ONE)
        val bytes = sscVal.toByteArray()
        // bytes might have leading 0x00 for sign or be shorter than 8 bytes
        ssc.fill(0)
        val copyLen = minOf(bytes.size, 8)
        val offset = bytes.size - copyLen
        System.arraycopy(bytes, offset, ssc, 8 - copyLen, copyLen)
    }

    private fun parseLength(data: ByteArray, offset: Int): Pair<Int, Int> {
        val firstByte = data[offset].toInt() and 0xFF
        if (firstByte <= 0x7F) {
            return Pair(firstByte, 1)
        } else if (firstByte == 0x81) {
            return Pair(data[offset + 1].toInt() and 0xFF, 2)
        } else if (firstByte == 0x82) {
            val len = ((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset + 2].toInt() and 0xFF)
            return Pair(len, 3)
        }
        throw IllegalArgumentException("Unsupported length encoding")
    }

    private fun buildLength(length: Int): ByteArray {
        if (length <= 0x7F) {
            return byteArrayOf(length.toByte())
        } else if (length <= 0xFF) {
            return byteArrayOf(0x81.toByte(), length.toByte())
        } else {
            return byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), (length and 0xFF).toByte())
        }
    }
}
