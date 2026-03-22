package com.example.epassport.app

import android.nfc.tech.IsoDep
import com.example.epassport.domain.port.NfcTransceiver

class AndroidNfcTransceiver(private val isoDep: IsoDep) : NfcTransceiver {

    override val isConnected: Boolean
        get() = isoDep.isConnected

    override var timeout: Int
        get() = isoDep.timeout
        set(value) { isoDep.timeout = value }

    override suspend fun selectApp() {
        if (!isoDep.isConnected) {
            isoDep.connect()
        }
        // eMRTD Applet AID (A0 00 00 02 47 10 01)
        val selectCmd = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x0C.toByte(),
            0x07.toByte(),
            0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(),
            0x47.toByte(), 0x10.toByte(), 0x01.toByte()
        )
        val response = isoDep.transceive(selectCmd)
        // Usually should check SW1/SW2 = 90 00
    }

    override suspend fun transceive(command: ByteArray): ByteArray {
        if (!isoDep.isConnected) {
            isoDep.connect()
        }
        return isoDep.transceive(command)
    }
}
