package com.example.epassport.data.nfc

/**
 * APDU (Application Protocol Data Unit) コマンドとレスポンスのドメインモデルおよびファクトリ。
 */
object ApduCommand {

    /** eMRTD アプリケーションの SELECT コマンド (A0 00 00 02 47 10 01) */
    fun selectApplet(): ByteArray {
        return byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x0C.toByte(), 0x07.toByte(),
            0xA0.toByte(), 0x00.toByte(), 0x00.toByte(), 0x02.toByte(), 0x47.toByte(),
            0x10.toByte(), 0x01.toByte()
        )
    }

    /** GET CHALLENGE コマンド (8バイト取得) */
    fun getChallenge(): ByteArray {
        return byteArrayOf(0x00.toByte(), 0x84.toByte(), 0x00.toByte(), 0x00.toByte(), 0x08.toByte())
    }

    /** MUTUAL AUTHENTICATE コマンド (EXTERNAL AUTHENTICATE) */
    fun mutualAuthenticate(authData: ByteArray): ByteArray {
        val apdu = ByteArray(5 + authData.size + 1)
        apdu[0] = 0x00.toByte()
        apdu[1] = 0x82.toByte()
        apdu[2] = 0x00.toByte()
        apdu[3] = 0x00.toByte()
        apdu[4] = authData.size.toByte()
        System.arraycopy(authData, 0, apdu, 5, authData.size)
        apdu[apdu.size - 1] = 0x28.toByte() // Le (40 bytes expected MAC) - but usually it's 0x28 for BAC
        return apdu
    }

    /** SELECT FILE コマンド */
    fun selectFile(fileId: ByteArray): ByteArray {
        val apdu = ByteArray(5 + fileId.size)
        apdu[0] = 0x00.toByte()
        apdu[1] = 0xA4.toByte()
        apdu[2] = 0x02.toByte()
        apdu[3] = 0x0C.toByte()
        apdu[4] = fileId.size.toByte()
        System.arraycopy(fileId, 0, apdu, 5, fileId.size)
        return apdu
    }

    /** READ BINARY コマンド（ショートフォーマット：オフセットが15ビット以内の場合） */
    fun readBinary(offset: Int, le: Int): ByteArray {
        val p1 = (offset ushr 8).toByte()
        val p2 = (offset and 0xFF).toByte()
        return byteArrayOf(
            0x00.toByte(), 0xB0.toByte(), p1, p2, le.toByte()
        )
    }
}
