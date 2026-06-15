package com.example.epassport.data.auth

import com.example.epassport.domain.exception.ApduException
import com.example.epassport.domain.exception.AuthenticationException
import com.example.epassport.domain.port.NfcTransceiver
import com.example.epassport.util.CryptoUtils
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.macs.CMac
import org.bouncycastle.crypto.params.KeyParameter
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-CMAC ベースの SecureMessaging（PACE 用）。
 *
 * 送信 APDU に対して AES-CBC で暗号化、AES-CMAC で MAC を付加する。
 * 受信 APDU の MAC を検証し、AES-CBC で復号する。
 */
class AesCmacSecureMessaging(
    private val delegate: NfcTransceiver,
    private val ksEnc: ByteArray,
    private val ksMac: ByteArray,
    ssc: ByteArray
) : NfcTransceiver, java.io.Closeable {

    private val ssc: ByteArray = ssc.copyOf()

    override fun close() {
        ksEnc.fill(0)
        ksMac.fill(0)
        ssc.fill(0)
    }

    override val isConnected: Boolean get() = delegate.isConnected
    override var timeout: Int
        get() = delegate.timeout
        set(value) { delegate.timeout = value }

    override val isExtendedLengthSupported: Boolean get() = delegate.isExtendedLengthSupported

    override suspend fun selectApp() {
        delegate.selectApp()
    }

    override suspend fun transceive(command: ByteArray): ByteArray {
        incrementSsc()

        val cla = command[0].toInt() and 0xFF
        val ins = command[1].toInt() and 0xFF
        val p1 = command[2].toInt() and 0xFF
        val p2 = command[3].toInt() and 0xFF

        val maskedCla = (cla or 0x0C).toByte()
        val header = byteArrayOf(maskedCla, ins.toByte(), p1.toByte(), p2.toByte())

        // Build DO97 (Le)
        val do97 = byteArrayOf(0x97.toByte(), 0x01.toByte(), 0x00.toByte())

        // Encrypt DO87 data if present
        var do87: ByteArray? = null
        if (command.size > 5) {
            val lc = command[4].toInt() and 0xFF
            val data = command.copyOfRange(5, 5 + lc)
            val paddedData = CryptoUtils.pad(data)
            val encrypted = Cipher.getInstance("AES/CBC/NoPadding", "BC").apply {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(ksEnc, "AES"), IvParameterSpec(ByteArray(16)))
            }.doFinal(paddedData)

            val payload = byteArrayOf(0x01) + encrypted
            val len = encodeLength(payload.size)
            do87 = byteArrayOf(0x87.toByte()) + len + payload
        }

        // Build MAC input
        val macStream = ByteArrayOutputStream()
        macStream.write(ssc)
        macStream.write(header)
        do87?.let { macStream.write(it) }
        macStream.write(do97)
        val macData = CryptoUtils.pad(macStream.toByteArray())
        val mac = calculateCmac(ksMac, macData)

        // Build DO8E
        val do8e = byteArrayOf(0x8E.toByte(), 0x10.toByte()) + mac

        // Build protected APDU
        val cmdStream = ByteArrayOutputStream()
        cmdStream.write(header)
        val content = (do87 ?: byteArrayOf()) + do97 + do8e
        cmdStream.write(content.size)
        cmdStream.write(content)
        cmdStream.write(0x00) // Le

        val response = delegate.transceive(cmdStream.toByteArray())

        incrementSsc()
        return unwrapResponse(response)
    }

    private fun unwrapResponse(response: ByteArray): ByteArray {
        if (response.size < 2) throw ApduException(0, 0, "Invalid SM response length")
        val sw1 = response[response.size - 2]
        val sw2 = response[response.size - 1]

        if (response.size == 2 && (sw1.toInt() and 0xFF) != 0x90) {
            throw ApduException(sw1.toInt() and 0xFF, sw2.toInt() and 0xFF, "APDU error")
        }

        val data = response.copyOfRange(0, response.size - 2)
        var (do87Value, do99Value, do8eValue) = parseResponseTlvs(data)

        if (do8eValue == null || do99Value == null) {
            throw AuthenticationException("Invalid SM response structure")
        }

        // Verify MAC
        val macStream = ByteArrayOutputStream()
        macStream.write(ssc)
        do87Value?.let {
            val len = encodeLength(it.size)
            macStream.write(byteArrayOf(0x87.toByte()) + len + it)
        }
        val len99 = encodeLength(do99Value.size)
        macStream.write(byteArrayOf(0x99.toByte()) + len99 + do99Value)
        val calculatedMac = calculateCmac(ksMac, CryptoUtils.pad(macStream.toByteArray()))
        if (!MessageDigest.isEqual(do8eValue, calculatedMac)) {
            throw AuthenticationException("SM Response MAC verification failed")
        }

        // Check SW
        if (do99Value[0] != sw1 || do99Value[1] != sw2) {
            throw AuthenticationException("DO99 SW does not match Response SW")
        }

        // Decrypt
        if (do87Value != null) {
            if (do87Value[0].toInt() != 0x01) {
                throw AuthenticationException("Unsupported padding indicator")
            }
            val encrypted = do87Value.copyOfRange(1, do87Value.size)
            val decryptedWithPad = Cipher.getInstance("AES/CBC/NoPadding", "BC").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(ksEnc, "AES"), IvParameterSpec(ByteArray(16)))
            }.doFinal(encrypted)
            val decrypted = CryptoUtils.unpad(decryptedWithPad)
            return decrypted + byteArrayOf(sw1, sw2)
        }

        return byteArrayOf(sw1, sw2)
    }

    private fun parseResponseTlvs(data: ByteArray): Triple<ByteArray?, ByteArray?, ByteArray?> {
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
                0x7C -> {
                    val nested = parseResponseTlvs(value)
                    do87Value = do87Value ?: nested.first
                    do99Value = do99Value ?: nested.second
                    do8eValue = do8eValue ?: nested.third
                }
            }
        }
        return Triple(do87Value, do99Value, do8eValue)
    }

    private fun calculateCmac(key: ByteArray, data: ByteArray): ByteArray {
        val mac = CMac(AESEngine())
        mac.init(KeyParameter(key))
        mac.update(data, 0, data.size)
        val result = ByteArray(16)
        mac.doFinal(result, 0)
        return result
    }

    private fun incrementSsc() {
        var value = BigInteger(1, ssc)
        value = value.add(BigInteger.ONE)
        val bytes = value.toByteArray()
        ssc.fill(0)
        val copyLen = minOf(bytes.size, ssc.size)
        System.arraycopy(bytes, bytes.size - copyLen, ssc, ssc.size - copyLen, copyLen)
    }

    private fun parseLength(data: ByteArray, offset: Int): Pair<Int, Int> {
        val first = data[offset].toInt() and 0xFF
        return when {
            first <= 0x7F -> Pair(first, 1)
            first == 0x81 -> Pair(data[offset + 1].toInt() and 0xFF, 2)
            first == 0x82 -> Pair(((data[offset + 1].toInt() and 0xFF) shl 8) or (data[offset + 2].toInt() and 0xFF), 3)
            else -> throw IllegalArgumentException("Unsupported length encoding")
        }
    }

    private fun encodeLength(length: Int): ByteArray {
        return when {
            length <= 0x7F -> byteArrayOf(length.toByte())
            length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), (length and 0xFF).toByte())
        }
    }
}
