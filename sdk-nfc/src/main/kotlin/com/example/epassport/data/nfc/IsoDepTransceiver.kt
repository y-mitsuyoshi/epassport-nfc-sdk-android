package com.example.epassport.data.nfc

import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import com.example.epassport.domain.exception.ApduException
import com.example.epassport.domain.exception.NfcTagLostException
import com.example.epassport.domain.port.NfcTransceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * `android.nfc.tech.IsoDep` をラップした [NfcTransceiver] 実装。
 */
class IsoDepTransceiver(private val isoDep: IsoDep) : NfcTransceiver {

    override val isConnected: Boolean
        get() = isoDep.isConnected

    override var timeout: Int
        get() = isoDep.timeout
        set(value) { isoDep.timeout = value }

    override val isExtendedLengthSupported: Boolean
        get() = isoDep.isExtendedLengthApduSupported

    override suspend fun selectApp() {
        val response = transceive(ApduCommand.selectApplet())
        checkStatusWord(response)
    }

    override suspend fun transceive(command: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        // 接続残名の場合は即座に例外を投げる。
        // 自動再接続は別のチップに誤って接続するリスクがあるため筌止。
        if (!isoDep.isConnected) {
            throw NfcTagLostException(IllegalStateException("NFC tag is not connected"))
        }

        try {
            val response = isoDep.transceive(command)
            if (response == null || response.size < 2) {
                throw NfcTagLostException(IllegalStateException("Invalid APDU response length"))
            }
            response
        } catch (e: TagLostException) {
            throw NfcTagLostException(e)
        } catch (e: java.io.IOException) {
            // TagLostException 以外の NFC I/O 障害（例: RF 消断）も NfcTagLostException として抱届する
            throw NfcTagLostException(e)
        }
    }

    private fun checkStatusWord(response: ByteArray) {
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        if (sw1 != 0x90 || sw2 != 0x00) {
            throw ApduException(sw1, sw2, "APDU Error SW1=$sw1, SW2=$sw2")
        }
    }
}
