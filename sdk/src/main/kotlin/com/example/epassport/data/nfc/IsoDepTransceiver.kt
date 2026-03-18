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

    override suspend fun selectApp() {
        val response = transceive(ApduCommand.selectApplet())
        checkStatusWord(response)
    }

    override suspend fun transceive(command: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        if (!isoDep.isConnected) {
            try {
                isoDep.connect()
            } catch (e: Exception) {
                throw NfcTagLostException(e)
            }
        }
        
        try {
            val response = isoDep.transceive(command)
            if (response == null || response.size < 2) {
                throw NfcTagLostException(IllegalStateException("Invalid APDU response length"))
            }
            response
        } catch (e: TagLostException) {
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
